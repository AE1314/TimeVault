#include <WiFi.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <SPI.h>
#include <MFRC522.h>
#include <ArduinoJson.h>
#include "secrets.h"

// ================= ASIGNACIÓN DE PINES =================
#define SS_PIN     5
#define RST_PIN    22
#define RELAY_PIN  4

#define LED_ROJO   2      // LOW: encendido, HIGH: apagado
#define LED_VERDE  13     // LOW: encendido, HIGH: apagado
#define BUZZER_PIN 15     // HIGH: encendido, LOW: apagado

MFRC522 mfrc522(SS_PIN, RST_PIN);

// ================= ESTADO DEL SISTEMA =================
const char* PUERTA_CODIGO = "MAIN_01";

unsigned long lastPollTime = 0;
const unsigned long POLL_INTERVAL = 2000;

bool puertaAbierta = false;
String uidActivoEnSala = "";

WiFiClientSecure client;

// ================= DECLARACIÓN DE FUNCIONES =================
void setupActuadores();
void conectarWiFi();
void checkVaultSettings();
void syncLockToSupabase(bool lock);
bool verificarAccesoEnSupabase(String uid);
void registrarAcceso(String uid, bool autorizado, String direccion, String accion);
void aplicarEstadoFisico(bool abierta);
void feedbackAbrir();
void feedbackCerrar();
void feedbackError();

void setup() {
  Serial.begin(115200);
  delay(500);

  setupActuadores();

  SPI.begin();
  mfrc522.PCD_Init();
  Serial.println("\n[SISTEMA] Periféricos y Lector RFID listos.");

  client.setInsecure();
  conectarWiFi();
}

void loop() {
  if (WiFi.status() != WL_CONNECTED) {
    conectarWiFi();
  }

  // Sincronización remota desde la app
  if (millis() - lastPollTime >= POLL_INTERVAL) {
    lastPollTime = millis();
    checkVaultSettings();
  }

  // Detección de tarjeta RFID
  if (!mfrc522.PICC_IsNewCardPresent() || !mfrc522.PICC_ReadCardSerial()) {
    return;
  }

  String uidString = "";
  for (byte i = 0; i < mfrc522.uid.size; i++) {
    if (mfrc522.uid.uidByte[i] < 0x10) uidString += "0";
    uidString += String(mfrc522.uid.uidByte[i], HEX);
  }
  uidString.toUpperCase();

  Serial.print("\n[RFID] Tarjeta detectada: ");
  Serial.println(uidString);

  // 1. Validar si la tarjeta existe y está activa en Supabase
  bool autorizado = verificarAccesoEnSupabase(uidString);

  if (!autorizado) {
    Serial.println("[RFID] DENEGADO: Tarjeta no autorizada.");
    feedbackError();
    registrarAcceso(uidString, false, puertaAbierta ? "OUT" : "IN", "REJECT_UNAUTHORIZED");
    delay(1000);
    return;
  }

  // 2. Control de acceso
  if (puertaAbierta) {
    puertaAbierta = false;
    aplicarEstadoFisico(false);
    feedbackCerrar();
    Serial.println("[PUERTA] SALIDA AUTORIZADA. CERRADA / BLOQUEADA.");
    registrarAcceso(uidString, true, "OUT", "LOCK");
    syncLockToSupabase(true);
  } else {
    puertaAbierta = true;
    aplicarEstadoFisico(true);
    feedbackAbrir();
    Serial.print("[PUERTA] ENTRADA AUTORIZADA por: ");
    Serial.println(uidString);
    registrarAcceso(uidString, true, "IN", "UNLOCK");
    syncLockToSupabase(false);
  }

  delay(1500);
}

// ================= ACTUADORES =================
void setupActuadores() {
  // En reposo queremos circuito cerrado en NO con relé Active-LOW:
  // Se escribe LOW para activar la bobina y conmutar el contacto NO a cerrado
  digitalWrite(RELAY_PIN, LOW);
  pinMode(RELAY_PIN, OUTPUT);

  pinMode(LED_ROJO, OUTPUT);
  pinMode(LED_VERDE, OUTPUT);
  pinMode(BUZZER_PIN, OUTPUT);

  aplicarEstadoFisico(false);
  digitalWrite(BUZZER_PIN, LOW);
}

void aplicarEstadoFisico(bool abierta) {
  if (abierta) {
    // Al abrir: apagamos la bobina (HIGH), el contacto NO vuelve a su estado abierto (corta circuito)
    digitalWrite(RELAY_PIN, HIGH);
    digitalWrite(LED_ROJO, HIGH);  // Apaga rojo
    digitalWrite(LED_VERDE, LOW);  // Enciende verde
  } else {
    // En reposo (cerrada): energizamos la bobina (LOW), el contacto NO se une a COM (circuito cerrado)
    digitalWrite(RELAY_PIN, LOW);
    digitalWrite(LED_VERDE, HIGH); // Apaga verde
    digitalWrite(LED_ROJO, LOW);   // Enciende rojo
  }
}

void feedbackAbrir() {
  digitalWrite(BUZZER_PIN, HIGH);
  delay(180);
  digitalWrite(BUZZER_PIN, LOW);
}

void feedbackCerrar() {
  digitalWrite(BUZZER_PIN, HIGH);
  delay(70);
  digitalWrite(BUZZER_PIN, LOW);
  delay(60);
  digitalWrite(BUZZER_PIN, HIGH);
  delay(150);
  digitalWrite(BUZZER_PIN, LOW);
}

void feedbackError() {
  for (int i = 0; i < 4; i++) {
    digitalWrite(LED_VERDE, HIGH);
    digitalWrite(LED_ROJO, LOW);
    digitalWrite(BUZZER_PIN, HIGH);
    delay(60);
    digitalWrite(LED_ROJO, HIGH);
    digitalWrite(BUZZER_PIN, LOW);
    delay(60);
  }
  aplicarEstadoFisico(puertaAbierta);
}

// ================= WI-FI =================
void conectarWiFi() {
  if (WiFi.status() == WL_CONNECTED) return;

  Serial.print("[WIFI] Conectando a ");
  Serial.println(WIFI_SSID);

  WiFi.disconnect(true);
  delay(100);
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASS);

  int intentos = 0;
  while (WiFi.status() != WL_CONNECTED && intentos < 30) {
    delay(500);
    Serial.print(".");
    intentos++;
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\n[WIFI] Conectado.");
    Serial.print("[WIFI] IP: ");
    Serial.println(WiFi.localIP());
  } else {
    Serial.println("\n[WIFI] Error al conectar.");
  }
}

// ================= COMUNICACIÓN CON SUPABASE =================
bool verificarAccesoEnSupabase(String uid) {
  if (WiFi.status() != WL_CONNECTED) return false;

  HTTPClient http;
  String url = String(SUPABASE_URL) + "/rest/v1/tarjetas?uid=eq." + uid + "&select=activa";
  bool permitido = false;

  http.setTimeout(2500);
  if (http.begin(client, url)) {
    http.addHeader("apikey", SUPABASE_KEY);
    http.addHeader("Authorization", String("Bearer ") + SUPABASE_KEY);

    int httpCode = http.GET();
    if (httpCode == HTTP_CODE_OK) {
      String payload = http.getString();
      StaticJsonDocument<256> doc;
      if (!deserializeJson(doc, payload) && doc.size() > 0) {
        permitido = doc[0]["activa"] | false;
      }
    }
    http.end();
  }
  client.stop();
  return permitido;
}

void checkVaultSettings() {
  if (WiFi.status() != WL_CONNECTED) return;

  HTTPClient http;
  String url = String(SUPABASE_URL) + "/rest/v1/vault_settings?id=eq.1&select=is_locked";

  http.setTimeout(2500);
  if (http.begin(client, url)) {
    http.addHeader("apikey", SUPABASE_KEY);
    http.addHeader("Authorization", String("Bearer ") + SUPABASE_KEY);

    int httpCode = http.GET();
    if (httpCode == HTTP_CODE_OK) {
      String payload = http.getString();
      StaticJsonDocument<256> doc;
      if (!deserializeJson(doc, payload) && doc.size() > 0) {
        bool remoteLocked = doc[0]["is_locked"] | false;
        bool estadoDeseadoAbierto = !remoteLocked;

        if (estadoDeseadoAbierto != puertaAbierta) {
          puertaAbierta = estadoDeseadoAbierto;
          aplicarEstadoFisico(puertaAbierta);

          if (puertaAbierta) {
            feedbackAbrir();
          } else {
            feedbackCerrar();
          }
        }
      }
    }
    http.end();
  }
  client.stop();
}

void syncLockToSupabase(bool lock) {
  if (WiFi.status() != WL_CONNECTED) return;

  HTTPClient http;
  String url = String(SUPABASE_URL) + "/rest/v1/vault_settings?id=eq.1";

  http.setTimeout(2500);
  if (http.begin(client, url)) {
    http.addHeader("Content-Type", "application/json");
    http.addHeader("apikey", SUPABASE_KEY);
    http.addHeader("Authorization", String("Bearer ") + SUPABASE_KEY);
    http.addHeader("Prefer", "return=minimal");

    String body = String("{\"is_locked\":") + (lock ? "true" : "false") + "}";
    http.sendRequest("PATCH", (uint8_t*)body.c_str(), body.length());
    http.end();
  }
  client.stop();
}

void registrarAcceso(String uid, bool autorizado, String direccion, String accion) {
  if (WiFi.status() != WL_CONNECTED) return;

  HTTPClient http;
  String url = String(SUPABASE_URL) + "/rest/v1/accesos";

  http.setTimeout(2500);
  if (http.begin(client, url)) {
    http.addHeader("Content-Type", "application/json");
    http.addHeader("apikey", SUPABASE_KEY);
    http.addHeader("Authorization", String("Bearer ") + SUPABASE_KEY);
    http.addHeader("Prefer", "return=minimal");

    String estado = autorizado ? "AUTHORIZED" : "DENIED";
    String body = "{"
      "\"puerta_codigo\":\"" + String(PUERTA_CODIGO) + "\","
      "\"uid\":\"" + uid + "\","
      "\"estado\":\"" + estado + "\","
      "\"direccion\":\"" + direccion + "\","
      "\"accion\":\"" + accion + "\""
    "}";

    http.POST(body);
    http.end();
  }
  client.stop();
}