package com.timevault.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.timevault.app.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private data class AdminAccessSummary(
    val company: String,
    val total: Long,
    val entries: Long,
    val exits: Long,
    val denied: Long,
    val events: List<AccessRecord>
)

private fun formatTimestamp(rawIso: String?): String {
    if (rawIso.isNullOrBlank() || rawIso == "null") return "--:--"

    return try {
        val clean = rawIso.replace("Z", "+00:00")
        val instant = java.time.Instant.parse(clean)
        val zonedDateTime = instant.atZone(java.time.ZoneId.of("America/Guatemala"))
        val formatter = java.time.format.DateTimeFormatter.ofPattern("hh:mm a · yyyy-MM-dd", Locale.US)
        zonedDateTime.format(formatter)
    } catch (_: Exception) {
        try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = parser.parse(rawIso.take(19)) ?: return rawIso.take(16).replace("T", " ")
            val formatter = SimpleDateFormat("hh:mm a · yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("America/Guatemala")
            }
            formatter.format(date)
        } catch (_: Exception) {
            rawIso.take(16).replace("T", " ")
        }
    }
}

private suspend fun loadDirectAccessSummary(): AdminAccessSummary {
    // 1. Mapeo UID -> Nombre
    val personasRaw = SupabaseClient.client
        .from("personas")
        .select(columns = Columns.raw("id,nombre,tarjetas(uid)"))
        .decodeList<JsonObject>()

    val uidToName = mutableMapOf<String, String>()
    personasRaw.forEach { row ->
        val name = row["nombre"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val cards = row["tarjetas"] as? JsonArray
        cards?.forEach { cardElem ->
            val cardUid = cardElem.jsonObject["uid"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (cardUid.isNotBlank() && name.isNotBlank()) {
                uidToName[cardUid.trim().uppercase()] = name
            }
        }
    }

    // 2. Consulta a accesos sin límite
    val list = SupabaseClient.client.from("accesos")
        .select {
            order("id", Order.DESCENDING)
        }
        .decodeList<JsonObject>()

    val records = list.map { obj ->
        val id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val uid = obj["uid"]?.jsonPrimitive?.contentOrNull.orEmpty().trim().uppercase()
        val estado = obj["estado"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val direccion = obj["direccion"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val accion = obj["accion"]?.jsonPrimitive?.contentOrNull.orEmpty()

        // Columna real en Supabase: ocurrido_en
        val rawTime = obj["ocurrido_en"]?.jsonPrimitive?.contentOrNull
            ?: obj["creado_en"]?.jsonPrimitive?.contentOrNull
            ?: obj["created_at"]?.jsonPrimitive?.contentOrNull

        val isDenied = estado.contains("DENIED", true) || accion.contains("REJECT", true)
        val isExit = direccion.equals("OUT", true)
        val isRemote = uid.equals("APP_BUTTON", true) || accion.contains("REMOTE", true)

        val resolvedName = when {
            isRemote -> "Apertura desde App"
            uidToName.containsKey(uid) -> uidToName[uid]!!
            else -> "Tarjeta sin asignar"
        }

        val typeLabel = when {
            isDenied -> "DENEGADO"
            isExit -> "SALIDA"
            else -> "ENTRADA"
        }

        AccessRecord(
            id = id,
            employeeName = resolvedName,
            cardUid = uid,
            timestamp = formatTimestamp(rawTime),
            type = typeLabel
        )
    }

    val entries = records.count { it.type == "ENTRADA" }.toLong()
    val exits = records.count { it.type == "SALIDA" }.toLong()
    val denied = records.count { it.type == "DENEGADO" }.toLong()

    return AdminAccessSummary(
        company = "TimeVault Security",
        total = records.size.toLong(),
        entries = entries,
        exits = exits,
        denied = denied,
        events = records
    )
}

@Composable
fun AccessScreen(isDarkTheme: Boolean = true) {
    var summary by remember { mutableStateOf<AdminAccessSummary?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current

    val backgroundColor = if (isDarkTheme) Color(0xFF0C192B) else Color(0xFFF3F6FA)
    val cardColor = if (isDarkTheme) Color(0xFF192B3D) else Color.White
    val textColor = if (isDarkTheme) Color(0xFFF4F7FB) else Color(0xFF142438)
    val secondaryColor = if (isDarkTheme) Color(0xFFB3C1D1) else Color(0xFF58697B)
    val borderColor = if (isDarkTheme) Color(0xFF2B4055) else Color(0xFFE0E7EF)
    val entryColor = if (isDarkTheme) Color(0xFF63E1CC) else Color(0xFF087F75)
    val exitColor = if (isDarkTheme) Color(0xFF93C5FD) else Color(0xFF1D4ED8)
    val deniedColor = if (isDarkTheme) Color(0xFFFFA3A3) else Color(0xFFB91C1C)

    LaunchedEffect(refreshKey, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                isLoading = true
                errorMessage = null
                try {
                    summary = loadDirectAccessSummary()
                } catch (e: Exception) {
                    errorMessage = e.message ?: "Error al consultar la tabla accesos"
                } finally {
                    isLoading = false
                }
                delay(5000L)
            }
        }
    }

    val currentSummary = summary

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "ACTIVIDAD EN VIVO",
                color = entryColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Control de accesos",
                color = textColor,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Historial sincronizado con Supabase.",
                color = secondaryColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Registros de la Bóveda",
                    color = textColor,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    enabled = !isLoading,
                    onClick = { refreshKey++ }
                ) {
                    Text(
                        text = if (isLoading) "Sincronizando..." else "Actualizar",
                        color = if (isLoading) secondaryColor else entryColor
                    )
                }
            }
        }

        if (isLoading) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = entryColor,
                    trackColor = borderColor
                )
            }
        }

        errorMessage?.let { message ->
            item {
                AccessPanelCard(cardColor = cardColor, borderColor = borderColor) {
                    Text("Error de conexión", color = textColor, fontWeight = FontWeight.Bold)
                    Text(message, color = deniedColor)
                    Button(onClick = { refreshKey++ }) {
                        Text("Reintentar")
                    }
                }
            }
        }

        if (currentSummary != null) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF10273B)
                ) {
                    Column(
                        modifier = Modifier
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF10273B), Color(0xFF125750))
                                )
                            )
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(currentSummary.company, color = Color(0xFFD4E7EA), fontWeight = FontWeight.SemiBold)
                        Text(
                            text = currentSummary.total.toString(),
                            color = Color.White,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text("Total eventos registrados", color = Color(0xFFD4E7EA))
                    }
                }
            }

            item {
                AccessPanelCard(cardColor = cardColor, borderColor = borderColor) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AccessCounter("Entradas", currentSummary.entries, entryColor, secondaryColor, Modifier.weight(1f))
                        AccessCounter("Salidas", currentSummary.exits, exitColor, secondaryColor, Modifier.weight(1f))
                        AccessCounter("Denegados", currentSummary.denied, deniedColor, secondaryColor, Modifier.weight(1f))
                    }
                }
            }

            item {
                Text(
                    text = "Eventos recientes",
                    color = textColor,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            items(currentSummary.events, key = { it.id }) { event ->
                val isDenied = event.type == "DENEGADO"
                val isExit = event.type == "SALIDA"
                val eventColor = if (isDenied) deniedColor else if (isExit) exitColor else entryColor

                AccessPanelCard(cardColor = cardColor, borderColor = borderColor) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = event.employeeName,
                                color = textColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = event.timestamp,
                                color = secondaryColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = eventColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = event.type,
                                color = eventColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (event.cardUid.isNotBlank() && event.cardUid != "APP_BUTTON") {
                        Text(
                            text = "UID: ${event.cardUid}",
                            color = secondaryColor.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccessPanelCard(
    cardColor: Color,
    borderColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun AccessCounter(
    label: String,
    count: Long,
    valueColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = count.toString(), color = valueColor, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(text = label, color = labelColor, style = MaterialTheme.typography.labelMedium)
    }
}