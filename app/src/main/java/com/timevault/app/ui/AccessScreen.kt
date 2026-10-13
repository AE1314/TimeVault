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
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*

private data class AdminAccessEvent(
    val id: String,
    val employeeName: String,
    val cardUid: String,
    val type: String,
    val doorName: String,
    val time: String
)

private data class AdminAccessSummary(
    val company: String,
    val day: String,
    val total: Long,
    val entries: Long,
    val exits: Long,
    val denied: Long,
    val updatedAt: String,
    val events: List<AdminAccessEvent>
)

private fun JsonObject.accessText(key: String): String {
    return this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
}

private fun JsonObject.accessCount(key: String): Long {
    return this[key]?.jsonPrimitive?.longOrNull ?: 0L
}

private suspend fun loadAdminAccessSummary(): AdminAccessSummary {
    val response = SupabaseClient.client.postgrest
        .rpc("timevault_admin_access_dashboard")
        .decodeSingle<JsonObject>()

    val payload = response["payload"]?.jsonObject
        ?: error("La respuesta no contiene los datos del panel.")

    val events = payload["events"]
        ?.jsonArray
        ?.map { element ->
            val event = element.jsonObject

            AdminAccessEvent(
                id = event.getValue("id").jsonPrimitive.content,
                employeeName = event.accessText("name"),
                cardUid = event.accessText("uid"),
                type = event.accessText("type"),
                doorName = event.accessText("door"),
                time = event.accessText("time")
            )
        }
        .orEmpty()

    return AdminAccessSummary(
        company = payload.accessText("company"),
        day = payload.accessText("day"),
        total = payload.accessCount("total"),
        entries = payload.accessCount("entries"),
        exits = payload.accessCount("exits"),
        denied = payload.accessCount("denied"),
        updatedAt = payload.accessText("updated_at"),
        events = events
    )
}

@Composable
fun AccessScreen(isDarkTheme: Boolean = true) {
    var summary by remember {
        mutableStateOf<AdminAccessSummary?>(null)
    }

    var isLoading by remember {
        mutableStateOf(true)
    }

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    var refreshKey by remember {
        mutableIntStateOf(0)
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    val backgroundColor = if (isDarkTheme) {
        Color(0xFF0C192B)
    } else {
        Color(0xFFF3F6FA)
    }

    val cardColor = if (isDarkTheme) {
        Color(0xFF192B3D)
    } else {
        Color.White
    }

    val textColor = if (isDarkTheme) {
        Color(0xFFF4F7FB)
    } else {
        Color(0xFF142438)
    }

    val secondaryColor = if (isDarkTheme) {
        Color(0xFFB3C1D1)
    } else {
        Color(0xFF58697B)
    }

    val borderColor = if (isDarkTheme) {
        Color(0xFF2B4055)
    } else {
        Color(0xFFE0E7EF)
    }

    val entryColor = if (isDarkTheme) {
        Color(0xFF63E1CC)
    } else {
        Color(0xFF087F75)
    }

    val exitColor = if (isDarkTheme) {
        Color(0xFF93C5FD)
    } else {
        Color(0xFF1D4ED8)
    }

    val deniedColor = if (isDarkTheme) {
        Color(0xFFFFA3A3)
    } else {
        Color(0xFFB91C1C)
    }

    // Consultar al abrir, regresar a la app
    // o presionar Actualizar.
    LaunchedEffect(refreshKey, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(
            Lifecycle.State.STARTED
        ) {
            while (true) {
                isLoading = true
                errorMessage = null

                try {
                    summary = withTimeout(20_000L) {
                        loadAdminAccessSummary()
                    }
                } catch (_: TimeoutCancellationException) {
                    summary = null
                    errorMessage =
                        "La consulta tardó demasiado. " +
                                "Revisá tu conexión y volvé a intentar."
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    summary = null
                    errorMessage =
                        "No pudimos consultar los accesos. " +
                                "Revisá la conexión y que tu cuenta " +
                                "administradora y empresa sigan activas. " +
                                "Si persiste, revisá la función en Supabase."
                } finally {
                    isLoading = false
                }

                // Esperar antes de la siguiente consulta.
                // Se pausa cuando la pantalla deja de estar visible.
                delay(30_000L)
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
                text = "ACTIVIDAD DE TU EMPRESA",
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
                text = "Entradas, salidas y accesos denegados.",
                color = secondaryColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = currentSummary?.day
                            ?: "Actividad de hoy",
                        color = textColor,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "Horario de Guatemala",
                        color = secondaryColor,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                TextButton(
                    enabled = !isLoading,
                    onClick = {
                        refreshKey++
                    }
                ) {
                    Text(
                        text = if (isLoading) {
                            "Consultando…"
                        } else {
                            "Actualizar"
                        },
                        color = if (isLoading) {
                            secondaryColor
                        } else {
                            entryColor
                        }
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
                AccessPanelCard(
                    cardColor = cardColor,
                    borderColor = borderColor
                ) {
                    Text(
                        text = "Datos no disponibles",
                        color = textColor,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = message,
                        color = deniedColor
                    )

                    Button(
                        enabled = !isLoading,
                        onClick = {
                            refreshKey++
                        }
                    ) {
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
                                    listOf(
                                        Color(0xFF10273B),
                                        Color(0xFF125750)
                                    )
                                )
                            )
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = currentSummary.company,
                            color = Color(0xFFD4E7EA),
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = currentSummary.total.toString(),
                            color = Color.White,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Marcaciones de hoy",
                            color = Color(0xFFD4E7EA)
                        )
                    }
                }
            }

            item {
                AccessPanelCard(
                    cardColor = cardColor,
                    borderColor = borderColor
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AccessCounter(
                            label = "Entradas",
                            count = currentSummary.entries,
                            valueColor = entryColor,
                            labelColor = secondaryColor,
                            modifier = Modifier.weight(1f)
                        )

                        AccessCounter(
                            label = "Salidas",
                            count = currentSummary.exits,
                            valueColor = exitColor,
                            labelColor = secondaryColor,
                            modifier = Modifier.weight(1f)
                        )

                        AccessCounter(
                            label = "Denegados",
                            count = currentSummary.denied,
                            valueColor = deniedColor,
                            labelColor = secondaryColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Movimientos de hoy",
                    color = textColor,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "Última consulta: ${currentSummary.updatedAt}",
                    color = secondaryColor,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (currentSummary.events.isEmpty()) {
                item {
                    AccessPanelCard(
                        cardColor = cardColor,
                        borderColor = borderColor
                    ) {
                        Text(
                            text = "Todavía no hay marcaciones",
                            color = textColor,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text =
                                "Los movimientos aparecerán aquí " +
                                        "cuando el servidor los registre " +
                                        "en Supabase.",
                            color = secondaryColor
                        )
                    }
                }
            }

            items(
                items = currentSummary.events,
                key = { it.id }
            ) { event ->
                val eventColor = when (event.type) {
                    "ENTRADA" -> entryColor
                    "SALIDA" -> exitColor
                    "DENEGADO" -> deniedColor
                    else -> secondaryColor
                }

                val eventLabel = when (event.type) {
                    "ENTRADA" -> "Entrada"
                    "SALIDA" -> "Salida"
                    "DENEGADO" -> "Denegado"
                    else -> event.type.ifBlank {
                        "Evento"
                    }
                }

                AccessPanelCard(
                    cardColor = cardColor,
                    borderColor = borderColor
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = event.employeeName.ifBlank {
                                "Persona no identificada"
                            },
                            modifier = Modifier.weight(1f),
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            text = event.time,
                            color = secondaryColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = eventColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = eventLabel,
                            modifier = Modifier.padding(
                                horizontal = 12.dp,
                                vertical = 6.dp
                            ),
                            color = eventColor,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    Text(
                        text = "Puerta: ${event.doorName}",
                        color = secondaryColor,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = "Tarjeta: ${event.cardUid.ifBlank { "Sin UID" }}",
                        color = secondaryColor,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (
                currentSummary.total >
                currentSummary.events.size.toLong()
            ) {
                item {
                    Text(
                        text =
                            "Se muestran los últimos " +
                                    "${currentSummary.events.size} movimientos. " +
                                    "Los contadores incluyen todas las " +
                                    "marcaciones del día.",
                        color = secondaryColor,
                        style = MaterialTheme.typography.bodySmall
                    )
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
            .border(
                width = 1.dp,
                color = borderColor,
                shape = shape
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
        Text(
            text = count.toString(),
            color = valueColor,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = label,
            color = labelColor,
            style = MaterialTheme.typography.labelLarge
        )
    }
}