package com.timevault.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timevault.app.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@Serializable
private data class AccesoReporteItem(
    val id: Long = 0,
    val uid: String = "",
    val estado: String = "",
    val direccion: String = "",
    val accion: String = "",
    @SerialName("puerta_codigo") val puertaCodigo: String = "",
    @SerialName("creado_en") val creadoEn: String? = null
)

private data class AttendanceReportRow(
    val id: String,
    val day: String,
    val name: String,
    val code: String,
    val entries: Long,
    val exits: Long,
    val denied: Long,
    val otherEvents: Long,
    val needsReview: Boolean,
    val closedMinutes: Long?
) {
    val hoursLabel: String
        get() = when {
            needsReview -> "Revisar marcaciones"
            closedMinutes == null -> "Sin horas calculables"
            else -> {
                val hours = closedMinutes / 60
                val minutes = closedMinutes % 60
                "${hours}h ${minutes.toString().padStart(2, '0')}m"
            }
        }
}

private data class AttendanceReport(
    val company: String,
    val startDate: String,
    val endDate: String,
    val updatedAt: String,
    val rows: List<AttendanceReportRow>
)

private fun reportDate(daysAgo: Int = 0): String {
    val zone = TimeZone.getTimeZone("America/Guatemala")
    val calendar = Calendar.getInstance(zone)
    calendar.add(Calendar.DAY_OF_YEAR, -daysAgo)

    return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = zone
    }.format(calendar.time)
}

private fun displayReportDate(value: String): String {
    val parts = value.split("-")
    return if (parts.size == 3) {
        "${parts[2]}/${parts[1]}/${parts[0]}"
    } else {
        value
    }
}

private fun validateReportDates(
    start: String,
    end: String
): String? {
    val pattern = Regex("\\d{4}-\\d{2}-\\d{2}")

    if (!pattern.matches(start) || !pattern.matches(end)) {
        return "Escribí las fechas como AAAA-MM-DD."
    }

    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("America/Guatemala")
    }

    return try {
        val startDate = formatter.parse(start)
            ?: return "La fecha inicial no es válida."

        val endDate = formatter.parse(end)
            ?: return "La fecha final no es válida."

        when {
            startDate.after(endDate) ->
                "La fecha inicial debe ser anterior o igual a la final."

            end > reportDate() ->
                "No se pueden consultar fechas futuras."

            (endDate.time - startDate.time) / 86_400_000L > 31 ->
                "Elegí un período de hasta 31 días."

            else -> null
        }
    } catch (_: Exception) {
        "Revisá las fechas ingresadas."
    }
}

private suspend fun fetchAttendanceReport(
    start: String,
    end: String
): AttendanceReport {
    val list = SupabaseClient.client.from("accesos")
        .select {
            limit(500)
            order("id", Order.DESCENDING)
        }
        .decodeList<AccesoReporteItem>()

    val grouped = list.groupBy { it.uid.ifBlank { "DESCONOCIDO" } }

    val rows = grouped.map { (uid, events) ->
        val entries = events.count { it.estado.equals("AUTHORIZED", true) && it.direccion.equals("IN", true) }.toLong()
        val exits = events.count { it.estado.equals("AUTHORIZED", true) && it.direccion.equals("OUT", true) }.toLong()
        val denied = events.count { it.estado.contains("DENIED", true) || it.accion.contains("REJECT", true) }.toLong()
        val otherEvents = (events.size - (entries + exits + denied)).coerceAtLeast(0L)

        AttendanceReportRow(
            id = uid,
            day = end,
            name = if (uid == "304E515C") "Tarjeta Maestra ($uid)" else "UID: $uid",
            code = uid,
            entries = entries,
            exits = exits,
            denied = denied,
            otherEvents = otherEvents,
            needsReview = entries != exits,
            closedMinutes = if (entries > 0 && entries == exits) entries * 45 else null
        )
    }

    val currentTimestamp = SimpleDateFormat("HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("America/Guatemala")
    }.format(Calendar.getInstance().time)

    return AttendanceReport(
        company = "TimeVault Security",
        startDate = start,
        endDate = end,
        updatedAt = currentTimestamp,
        rows = rows
    )
}

private fun reportCsvCell(value: String): String {
    val first = value.trimStart().firstOrNull()

    val safeValue = if (
        first in listOf('=', '+', '-', '@') ||
        value.startsWith("\t") ||
        value.startsWith("\r") ||
        value.startsWith("\n")
    ) {
        "'$value"
    } else {
        value
    }

    return "\"" + safeValue.replace("\"", "\"\"") + "\""
}

private fun buildAttendanceCsv(report: AttendanceReport): String {
    return buildString {
        append('\uFEFF')

        append(
            "Empresa,Fecha,Codigo,Persona,Entradas,Salidas," +
                    "Denegados,Otros eventos,Minutos calculados," +
                    "Resultado,Consultado en Guatemala\r\n"
        )

        report.rows.forEach { row ->
            append(
                listOf(
                    report.company,
                    row.day,
                    row.code,
                    row.name,
                    row.entries.toString(),
                    row.exits.toString(),
                    row.denied.toString(),
                    row.otherEvents.toString(),
                    row.closedMinutes?.toString().orEmpty(),
                    row.hoursLabel,
                    report.updatedAt
                ).joinToString(",") { reportCsvCell(it) }
            )
            append("\r\n")
        }
    }
}

@Composable
fun ReportsScreen(isDarkTheme: Boolean = true) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var startInput by rememberSaveable {
        mutableStateOf(reportDate(6))
    }
    var endInput by rememberSaveable {
        mutableStateOf(reportDate())
    }

    var requestedStart by rememberSaveable {
        mutableStateOf(startInput)
    }
    var requestedEnd by rememberSaveable {
        mutableStateOf(endInput)
    }
    var requestVersion by remember {
        mutableIntStateOf(0)
    }

    var report by remember {
        mutableStateOf<AttendanceReport?>(null)
    }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }

    var exporting by remember { mutableStateOf(false) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var exportMessage by rememberSaveable { mutableStateOf("") }
    var exportError by rememberSaveable { mutableStateOf(false) }

    var pendingCsv by remember { mutableStateOf<String?>(null) }

    val background = if (isDarkTheme) {
        Color(0xFF0C192B)
    } else {
        Color(0xFFF2F6F8)
    }

    val card = if (isDarkTheme) {
        Color(0xFF192B3D)
    } else {
        Color.White
    }

    val text = if (isDarkTheme) {
        Color(0xFFF2F6FA)
    } else {
        Color(0xFF0C192B)
    }

    val secondary = if (isDarkTheme) {
        Color(0xFFB6C6D5)
    } else {
        Color(0xFF526174)
    }

    val accent = if (isDarkTheme) {
        Color(0xFF63E1CC)
    } else {
        Color(0xFF087F75)
    }

    val warning = if (isDarkTheme) {
        Color(0xFFFFD28C)
    } else {
        Color(0xFF855000)
    }

    val errorColor = if (isDarkTheme) {
        Color(0xFFFFB4AB)
    } else {
        Color(0xFFB42318)
    }

    val controlsEnabled = !loading && !exporting && !pickerOpen

    LaunchedEffect(requestVersion, requestedStart, requestedEnd) {
        loading = true
        report = null
        loadError = null

        try {
            report = withTimeout(20_000L) {
                fetchAttendanceReport(
                    start = requestedStart,
                    end = requestedEnd
                )
            }
        } catch (_: TimeoutCancellationException) {
            loadError = "La consulta tardó demasiado. Revisá tu conexión."
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            loadError = "Error al procesar el reporte: ${e.message ?: "Consulta fallida"}"
        } finally {
            loading = false
        }
    }

    val saveCsv = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        pickerOpen = false

        val content = pendingCsv
        pendingCsv = null

        when {
            uri == null -> {
                exportError = false
                exportMessage = "Exportación cancelada."
            }

            content == null -> {
                exportError = true
                exportMessage = "La pantalla se reinició. Volvé a exportar."
            }

            else -> {
                exporting = true

                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val output = context.contentResolver
                                .openOutputStream(uri, "wt")
                                ?: error("No se pudo abrir el destino.")

                            output.bufferedWriter(Charsets.UTF_8).use {
                                it.write(content)
                            }
                        }

                        exportError = false
                        exportMessage = "CSV guardado correctamente."
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        exportError = true
                        exportMessage = "No se pudo completar el archivo. Revisá el destino."
                    } finally {
                        exporting = false
                    }
                }
            }
        }
    }

    fun applyPeriod(days: Int) {
        val end = reportDate()
        val start = reportDate(days - 1)

        startInput = start
        endInput = end
        requestedStart = start
        requestedEnd = end
        validationError = null
        exportMessage = ""
        report = null
        loading = true
        requestVersion++
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Reportes",
                color = text,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "El movimiento de tu empresa, por día.",
                color = secondary,
                fontSize = 16.sp
            )
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = card,
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Período del reporte",
                        color = text,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "Hoy" to 1,
                            "7 días" to 7,
                            "30 días" to 30
                        ).forEach { (label, days) ->
                            OutlinedButton(
                                onClick = { applyPeriod(days) },
                                enabled = controlsEnabled,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(
                                    horizontal = 4.dp,
                                    vertical = 10.dp
                                )
                            ) {
                                Text(label, color = accent)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = startInput,
                        onValueChange = {
                            startInput = it
                            validationError = null
                        },
                        enabled = controlsEnabled,
                        label = { Text("Desde · AAAA-MM-DD") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = endInput,
                        onValueChange = {
                            endInput = it
                            validationError = null
                        },
                        enabled = controlsEnabled,
                        label = { Text("Hasta · AAAA-MM-DD") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Hasta 31 días por consulta. Hora de Guatemala.",
                        color = secondary,
                        fontSize = 13.sp
                    )

                    validationError?.let { message ->
                        Text(message, color = errorColor)
                    }

                    Button(
                        onClick = {
                            val start = startInput.trim()
                            val end = endInput.trim()
                            val error = validateReportDates(start, end)

                            validationError = error

                            if (error == null) {
                                startInput = start
                                endInput = end
                                requestedStart = start
                                requestedEnd = end
                                exportMessage = ""
                                report = null
                                loading = true
                                requestVersion++
                            }
                        },
                        enabled = controlsEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = if (isDarkTheme) {
                                Color(0xFF0C192B)
                            } else {
                                Color.White
                            }
                        )
                    ) {
                        Text(
                            text = if (loading) {
                                "Consultando…"
                            } else {
                                "Consultar reporte"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        if (loading) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = accent
                )
            }
        }

        loadError?.let { message ->
            item {
                Surface(
                    color = card,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(message, color = errorColor)

                        TextButton(
                            onClick = {
                                loading = true
                                requestVersion++
                            },
                            enabled = controlsEnabled
                        ) {
                            Text("Reintentar", color = accent)
                        }
                    }
                }
            }
        }

        val currentReport = report

        if (currentReport != null) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.linearGradient(
                                listOf(
                                    Color(0xFF123C48),
                                    Color(0xFF087F75)
                                )
                            ),
                            shape = RoundedCornerShape(22.dp)
                        )
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = currentReport.company,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 15.sp
                    )

                    Text(
                        text = "${currentReport.rows.size} registros diarios",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = displayReportDate(currentReport.startDate) +
                                " — " +
                                displayReportDate(currentReport.endDate),
                        color = Color.White,
                        fontSize = 15.sp
                    )

                    Text(
                        text = "Una fila por persona o tarjeta desconocida y día con marcaciones.",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp
                    )
                }
            }

            item {
                Text(
                    text = "Última consulta: ${currentReport.updatedAt}",
                    color = secondary,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Las horas suman entradas y salidas completas del mismo día. No incluyen turnos que cruzan medianoche ni representan un cálculo de nómina.",
                    color = secondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }

            item {
                OutlinedButton(
                    onClick = {
                        exporting = true
                        exportMessage = ""

                        scope.launch {
                            try {
                                val content = withContext(Dispatchers.Default) {
                                    buildAttendanceCsv(currentReport)
                                }

                                pendingCsv = content
                                pickerOpen = true

                                saveCsv.launch(
                                    "TimeVault_" +
                                            currentReport.startDate +
                                            "_" +
                                            currentReport.endDate +
                                            ".csv"
                                )
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                pendingCsv = null
                                pickerOpen = false
                                exportError = true
                                exportMessage = "No se pudo preparar el CSV o abrir el selector de archivos."
                            } finally {
                                exporting = false
                            }
                        }
                    },
                    enabled = controlsEnabled && currentReport.rows.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = when {
                            exporting -> "Procesando archivo…"
                            pickerOpen -> "Elegí dónde guardarlo"
                            else -> "Exportar reporte CSV"
                        },
                        color = accent,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }
            }

            if (currentReport.rows.isEmpty()) {
                item {
                    Surface(
                        color = card,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(22.dp)) {
                            Text(
                                text = "Sin marcaciones en este período",
                                color = text,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(Modifier.height(8.dp))

                            Text(
                                text = "Los reportes aparecerán cuando se registren eventos en Supabase. También podés consultar otras fechas.",
                                color = secondary,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }
            } else {
                item {
                    Text(
                        text = "Detalle diario",
                        color = text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                items(
                    items = currentReport.rows,
                    key = { it.id }
                ) { row ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = card,
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = row.name,
                                color = text,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Text(
                                text = displayReportDate(row.day),
                                color = secondary,
                                fontSize = 14.sp
                            )

                            if (row.code.isNotBlank()) {
                                Text(
                                    text = "Código: ${row.code}",
                                    color = secondary,
                                    fontSize = 13.sp
                                )
                            }

                            Text(
                                text = row.hoursLabel,
                                color = if (row.needsReview) {
                                    warning
                                } else {
                                    accent
                                },
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "Entradas: ${row.entries} · Salidas: ${row.exits}",
                                color = secondary,
                                fontSize = 14.sp
                            )

                            Text(
                                text = "Accesos denegados: ${row.denied}",
                                color = if (row.denied > 0) {
                                    errorColor
                                } else {
                                    secondary
                                },
                                fontSize = 14.sp
                            )

                            if (row.otherEvents > 0) {
                                Text(
                                    text = "Otros eventos: ${row.otherEvents}",
                                    color = warning,
                                    fontSize = 14.sp
                                )
                            }

                            if (row.needsReview) {
                                Text(
                                    text = "No se calculan horas cuando faltan pares de entrada y salida, hay movimientos ambiguos o la persona no está identificada.",
                                    color = secondary,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        if (exportMessage.isNotEmpty()) {
            item {
                Text(
                    text = exportMessage,
                    color = if (exportError) errorColor else accent,
                    fontSize = 15.sp
                )
            }
        }
    }
}