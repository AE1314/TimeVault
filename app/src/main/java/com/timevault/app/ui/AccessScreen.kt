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
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

@Serializable
data class AccesoModel(
    val id: Long = 0,
    val puerta_codigo: String = "",
    val uid: String = "",
    val estado: String = "",
    val direccion: String = "",
    val accion: String = "",
    val creado_en: String? = null
)

private data class AdminAccessSummary(
    val company: String,
    val total: Long,
    val entries: Long,
    val exits: Long,
    val denied: Long,
    val events: List<AccesoModel>
)

private suspend fun loadDirectAccessSummary(): AdminAccessSummary {
    val list = SupabaseClient.client.from("accesos")
        .select {
            order("id", Order.DESCENDING)
            limit(50)
        }
        .decodeList<AccesoModel>()

    val entries = list.count { it.estado.equals("AUTHORIZED", true) && it.direccion.equals("IN", true) }.toLong()
    val exits = list.count { it.estado.equals("AUTHORIZED", true) && it.direccion.equals("OUT", true) }.toLong()
    val denied = list.count { it.estado.contains("DENIED", true) || it.accion.contains("REJECT", true) }.toLong()

    return AdminAccessSummary(
        company = "TimeVault Security",
        total = list.size.toLong(),
        entries = entries,
        exits = exits,
        denied = denied,
        events = list
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
                delay(5000L) // Actualiza cada 5 segundos
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
                val isDenied = event.estado.contains("DENIED", true) || event.accion.contains("REJECT", true)
                val isExit = event.direccion.equals("OUT", true)
                val eventColor = if (isDenied) deniedColor else if (isExit) exitColor else entryColor
                val eventLabel = if (isDenied) "DENEGADO" else if (isExit) "SALIDA / CIERRE" else "ENTRADA / APERTURA"

                AccessPanelCard(cardColor = cardColor, borderColor = borderColor) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "UID: ${event.uid}",
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = eventColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = eventLabel,
                                color = eventColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 11.sp
                            )
                        }
                    }
                    Text("Acción: ${event.accion} | Puerta: ${event.puerta_codigo}", color = secondaryColor, fontSize = 13.sp)
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