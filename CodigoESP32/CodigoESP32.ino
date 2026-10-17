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

#define LED_ROJO   2      // Ánodo común: LOW enciende, HIGH apaga
#define LED_VERDE  13     // Ánodo común: LOW enciende, HIGH apaga
#define BUZZER_PIN 15     // Buzzer: HIGH enciende, LOW apaga

MFRC522 mfrc522(SS_PIN, RST_PIN);

// ================= ESTADO DEL SISTEMA =================
const char* PUERTA_CODIGO = "MAIN_01";

unsigned long lastPollTime = 0;
const unsigned long POLL_INTERVAL = 2000;

bool puertaAbierta = false;

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
  Serial.println("\n[SISTEMA] Perifericos y Lector RFID inicializados.");

  client.setInsecure();

  conectarWiFi();
}

void loop() {
  if (WiFi.status() != WL_CONNECTED) {
    conectarWiFi();
  }

  // Polling para sincronizar cambios remotos desde la app
  if (millis() - lastPollTime >= POLL_INTERVAL) {
    lastPollTime = millis();
    checkVaultSettings();
  }

  // Comprobar presencia y lectura de tarjeta RFID
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

  // Validación directa contra Supabase con logs completos
  bool autorizado = verificarAccesoEnSupabase(uidString);

  if (!autorizado) {
    Serial.println("[RFID] DENEGADO: Tarjeta no autorizada o inactiva.");
    feedbackError();
    registrarAcceso(uidString, false, puertaAbierta ? "OUT" : "IN", "REJECT_INACTIVE_OR_UNAUTHORIZED");
  } else {
    puertaAbierta = !puertaAbierta;
    aplicarEstadoFisico(puertaAbierta);

    if (puertaAbierta) {
      feedbackAbrir();
      Serial.println("[PUERTA] ABIERTA con credencial autorizada.");
      registrarAcceso(uidString, true, "IN", "UNLOCK");
      syncLockToSupabase(false);
    } else {
      feedbackCerrar();
      Serial.println("[PUERTA] CERRADA con credencial autorizada.");
      registrarAcceso(uidString, true, "OUT", "LOCK");
      syncLockToSupabase(true);
    }
  }

  mfrc522.PICC_HaltA();
  mfrc522.PCD_StopCrypto1();
  delay(1000);
}

// ================= ACTUADORES =================
void setupActuadores() {
  pinMode(RELAY_PIN, OUTPUT);
  pinMode(LED_ROJO, OUTPUT);
  pinMode(LED_VERDE, OUTPUT);
  pinMode(BUZZER_PIN, OUTPUT);

  aplicarEstadoFisico(false);
  digitalWrite(BUZZER_PIN, LOW);
}

void aplicarEstadoFisico(bool abierta) {
  if (abierta) {
    digitalWrite(RELAY_PIN, LOW);   // Relé activado (abierto)
    digitalWrite(LED_ROJO, HIGH);  // Rojo apagado
    digitalWrite(LED_VERDE, LOW);  // Verde encendido
  } else {
    digitalWrite(RELAY_PIN, HIGH);  // Relé reposo (cerrado)
    digitalWrite(LED_VERDE, HIGH); // Verde apagado
    digitalWrite(LED_ROJO, LOW);   // Rojo encendido
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
    Serial.println("\n[WIFI] Conectado exitosamente.");
    Serial.print("[WIFI] IP asignada: ");
    Serial.println(WiFi.localIP());
  } else {
    Serial.println("\n[WIFI] Error al conectar. Reintentando...");
  }
}

// ================= CONSULTAS Y SINCRONIZACIÓN SUPABASE =================
bool verificarAccesoEnSupabase(String uid) {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("[SUPABASE] Error: Sin conexion WiFi");
    return false;
  }

  HTTPClient http;
  String url = String(SUPABASE_URL) + "/rest/v1/tarjetas?uid=eq." + uid + "&select=activa";
  bool permitido = false;

  Serial.print("[SUPABASE CONSULTA URL]: ");
  Serial.println(url);

  if (http.begin(client, url)) {
    http.addHeader("apikey", SUPABASE_KEY);
    http.addHeader("Authorization", String("Bearer ") + SUPABASE_KEY);

    int httpCode = http.GET();
    Serial.print("[SUPABASE HTTP CODE]: ");
    Serial.println(httpCode);

    if (httpCode == HTTP_CODE_OK) {
      String payload = http.getString();
      Serial.print("[SUPABASE PAYLOAD]: ");
      Serial.println(payload);

      StaticJsonDocument<256> doc;
      DeserializationError err = deserializeJson(doc, payload);

      if (err) {
        Serial.print("[JSON ERROR]: ");
        Serial.println(err.c_str());
      } else if (doc.size() == 0) {
        Serial.println("[SUPABASE]: Arreglo vacio (El UID no existe en tabla tarjetas).");
      } else {
        permitido = doc[0]["activa"] | false;
        Serial.print("[SUPABASE ESTADO ACTIVA]: ");
        Serial.println(permitido ? "TRUE" : "FALSE");
      }
    } else {
      Serial.print("[SUPABASE RESPUESTA ERROR]: ");
      Serial.println(http.getString());
    }
    http.end();
  }
  return permitido;
}

void checkVaultSettings() {
  if (WiFi.status() != WL_CONNECTED) return;

  HTTPClient http;
  String url = String(SUPABASE_URL) + "/rest/v1/vault_settings?id=eq.1&select=is_locked";

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
            Serial.println("[REMOTO] Puerta ABIERTA desde la aplicacion.");
          } else {
            feedbackCerrar();
            Serial.println("[REMOTO] Puerta BLOQUEADA desde la aplicacion.");
          }
        }
      }
    }
    http.end();
  }
}

void syncLockToSupabase(bool lock) {
  if (WiFi.status() != WL_CONNECTED) return;

  HTTPClient http;
  String url = String(SUPABASE_URL) + "/rest/v1/vault_settings?id=eq.1";

  if (http.begin(client, url)) {
    http.addHeader("Content-Type", "application/json");
    http.addHeader("apikey", SUPABASE_KEY);
    http.addHeader("Authorization", String("Bearer ") + SUPABASE_KEY);
    http.addHeader("Prefer", "return=minimal");

    String body = String("{\"is_locked\":") + (lock ? "true" : "false") + "}";
    http.sendRequest("PATCH", (uint8_t*)body.c_str(), body.length());
    http.end();
  }
}

void registrarAcceso(String uid, bool autorizado, String direccion, String accion) {
  if (WiFi.status() != WL_CONNECTED) return;

  HTTPClient http;
  String url = String(SUPABASE_URL) + "/rest/v1/accesos";

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
}