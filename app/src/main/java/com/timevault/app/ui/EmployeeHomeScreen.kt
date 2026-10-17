package com.timevault.app.ui

import androidx.compose.foundation.background
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.timevault.app.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private fun JsonObject.valueText(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject.valueBool(key: String): Boolean =
    this[key]?.jsonPrimitive?.booleanOrNull == true

private fun JsonObject.valueLong(key: String): Long =
    this[key]?.jsonPrimitive?.longOrNull ?: 0L

private fun shiftEmployeeDay(
    day: String,
    amount: Int
): String {
    val format = SimpleDateFormat(
        "yyyy-MM-dd",
        Locale.US
    ).apply {
        timeZone = TimeZone.getTimeZone("America/Guatemala")
        isLenient = false
    }

    val date = requireNotNull(format.parse(day))

    val calendar = Calendar.getInstance(format.timeZone).apply {
        time = date
        add(Calendar.DAY_OF_MONTH, amount)
    }

    return format.format(calendar.time)
}

@Composable
fun EmployeeHomeScreen(
    userEmail: String,
    isDarkTheme: Boolean,
    signingOut: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    onLogout: () -> Unit
) {
    var selectedDay by remember {
        mutableStateOf<String?>(null)
    }

    var refreshKey by remember {
        mutableIntStateOf(0)
    }

    var data by remember {
        mutableStateOf<JsonObject?>(null)
    }

    var isLoading by remember {
        mutableStateOf(true)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    var showProfile by remember {
        mutableStateOf(false)
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    // Consulta al abrir la pantalla, cambiar fecha
    // o presionar Actualizar.
    LaunchedEffect(
        selectedDay,
        refreshKey,
        lifecycleOwner
    ) {
        data = null
        error = null
        isLoading = true

        // La actualización automática se pausa
        // cuando la app deja de estar visible.
        lifecycleOwner.lifecycle.repeatOnLifecycle(
            Lifecycle.State.STARTED
        ) {
            while (true) {
                try {
                    val result = withTimeout(20_000L) {
                        val params = buildJsonObject {
                            selectedDay?.let {
                                put("p_day", it)
                            }
                        }

                        val response = SupabaseClient.client.postgrest
                            .rpc(
                                "timevault_employee_dashboard",
                                params
                            )
                            .decodeSingle<JsonObject>()

                        response["payload"]?.jsonObject
                            ?: throw IllegalStateException(
                                "Respuesta incompleta"
                            )
                    }
                    if (result.valueBool("ok")) {
                        data = result
                        error = null
                    } else {
                        data = null
                        error = result.valueText("message")
                    }
                } catch (_: TimeoutCancellationException) {
                    data = null
                    error =
                        "La consulta tardó demasiado. " +
                                "Revisá tu conexión y reintentá."
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    data = null
                    error =
                        "No pudimos cargar tus datos. " +
                                "Revisá la conexión. Si persiste, " +
                                "el administrador debe revisar " +
                                "la configuración del panel."
                } finally {
                    isLoading = false
                }

                delay(30_000L)
            }
        }
    }

    val colors = MaterialTheme.colorScheme
    val snapshot = data

    val events = snapshot
        ?.get("events")
        ?.jsonArray
        ?.map { it.jsonObject }
        .orEmpty()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Encabezado.
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "TIMEVAULT",
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleLarge
                    )

                    Text(
                        text = "Mi espacio",
                        color = colors.onSurfaceVariant
                    )
                }

                TextButton(
                    onClick = {
                        showProfile = !showProfile
                    }
                ) {
                    Text(
                        text = if (showProfile) {
                            "Mis accesos"
                        } else {
                            "Mi perfil"
                        }
                    )
                }
            }
        }

        // Tarjeta de bienvenida.
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF10273B)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
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
                        text = "TU JORNADA, A LA VISTA",
                        color = Color(0xFF90E6D6),
                        style = MaterialTheme.typography.labelLarge
                    )

                    val name = snapshot
                        ?.valueText("name")
                        .orEmpty()

                    Text(
                        text = if (name.isNotBlank()) {
                            "Hola, $name"
                        } else {
                            "Bienvenido a TimeVault"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineSmall
                    )

                    Text(
                        text = snapshot?.valueText("company")
                            ?: if (error != null) {
                                "Información no disponible"
                            } else {
                                "Consultando tu información…"
                            },
                        color = Color(0xFFD4E7EA)
                    )
                }
            }
        }

        // Perfil de la cuenta conectada.
        if (showProfile) {
            item {
                EmployeeSection {
                    Text(
                        text = "Mi perfil",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Text(text = userEmail)

                    Text(text = "Rol: Empleado")

                    snapshot?.let {
                        Text(
                            text = "Código: ${it.valueText("code")}"
                        )

                        Text(
                            text = "Tarjetas activas: " +
                                    it.valueLong("active_cards")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Modo oscuro",
                            modifier = Modifier.weight(1f)
                        )

                        Switch(
                            checked = isDarkTheme,
                            onCheckedChange = onThemeToggle
                        )
                    }

                    Text(
                        text = "Para cambiar tus datos o tu tarjeta, " +
                                "contactá al administrador.",
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }

        // Mensajes de carga o error.
        if (error != null) {
            item {
                EmployeeSection {
                    Text(
                        text = "Datos no disponibles",
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = error.orEmpty(),
                        color = colors.error
                    )

                    Button(
                        onClick = {
                            refreshKey++
                        },
                        enabled = !signingOut
                    ) {
                        Text("Reintentar")
                    }
                }
            }
        } else if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        // Resumen y movimientos.
        if (!showProfile && snapshot != null) {
            item {
                EmployeeSection {
                    Text(
                        text = "Mis accesos · " +
                                snapshot.valueText("day"),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = "Horario de Guatemala",
                        color = colors.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            enabled = snapshot.valueBool(
                                "can_previous"
                            ),
                            onClick = {
                                selectedDay = shiftEmployeeDay(
                                    snapshot.valueText("day"),
                                    -1
                                )
                            }
                        ) {
                            Text("Anterior")
                        }

                        TextButton(
                            onClick = {
                                selectedDay = null
                                refreshKey++
                            }
                        ) {
                            Text("Hoy")
                        }

                        TextButton(
                            enabled = snapshot.valueBool(
                                "can_next"
                            ),
                            onClick = {
                                selectedDay = shiftEmployeeDay(
                                    snapshot.valueText("day"),
                                    1
                                )
                            }
                        ) {
                            Text("Siguiente")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        EmployeeMetric(
                            label = "Entradas",
                            value = snapshot
                                .valueLong("entries")
                                .toString()
                        )

                        EmployeeMetric(
                            label = "Salidas",
                            value = snapshot
                                .valueLong("exits")
                                .toString()
                        )

                        EmployeeMetric(
                            label = "Denegados",
                            value = snapshot
                                .valueLong("denied")
                                .toString()
                        )
                    }
                }
            }

            item {
                EmployeeSection {
                    Text(
                        text = "Tiempo entre marcaciones",
                        fontWeight = FontWeight.Bold
                    )

                    val minutes = snapshot["closed_minutes"]
                        ?.jsonPrimitive
                        ?.longOrNull

                    Text(
                        text = if (minutes == null) {
                            "Requiere revisión"
                        } else {
                            "${minutes / 60} h ${minutes % 60} min"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.primary,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Suma de pares entrada–salida cerrados " +
                                "dentro de este día. No incluye turnos " +
                                "que cruzan medianoche ni equivale " +
                                "a horas de nómina.",
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )

                    if (snapshot.valueBool("needs_review")) {
                        Text(
                            text = "Hay marcaciones repetidas o " +
                                    "sin pareja. Pedí una revisión " +
                                    "al administrador.",
                            color = colors.error
                        )
                    }

                    if (snapshot.valueBool("open_entry")) {
                        Text(
                            text = "La última entrada del día " +
                                    "no tiene una salida posterior.",
                            color = colors.primary
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Movimientos",
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )

                    TextButton(
                        onClick = {
                            refreshKey++
                        }
                    ) {
                        Text("Actualizar")
                    }
                }

                Text(
                    text = "Actualizado: " +
                            snapshot.valueText("updated_at"),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }

            if (events.isEmpty()) {
                item {
                    EmployeeSection {
                        Text(
                            text = "Sin movimientos para este día",
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Tus marcaciones aparecerán " +
                                    "cuando el sistema de acceso " +
                                    "las registre."
                        )
                    }
                }
            }

            items(
                items = events,
                key = { it.valueText("id") }
            ) { event ->
                EmployeeSection {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = event.valueText("type"),
                            fontWeight = FontWeight.Bold,
                            color = if (
                                event.valueText("type") == "DENEGADO"
                            ) {
                                colors.error
                            } else {
                                colors.primary
                            }
                        )

                        Text(
                            text = event.valueText("time"),
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = event.valueText("door"),
                        color = colors.onSurfaceVariant
                    )
                }
            }

            if (snapshot.valueLong("total") > events.size) {
                item {
                    Text(
                        text = "Mostrando los últimos 100 movimientos " +
                                "de ${snapshot.valueLong("total")}. " +
                                "El resumen incluye todo el día.",
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onLogout,
                enabled = !signingOut,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = if (signingOut) {
                        "Cerrando sesión…"
                    } else {
                        "Cerrar sesión"
                    }
                )
            }
        }
    }
}

@Composable
private fun EmployeeSection(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
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
private fun EmployeeMetric(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}