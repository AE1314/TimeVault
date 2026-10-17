package com.timevault.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timevault.app.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

private data class DateCountRecord(
    val date: String,
    val count: Int
)

private data class EmployeeCard(
    val uid: String,
    val isActive: Boolean
)

private data class Employee(
    val id: String,
    val code: String,
    val name: String,
    val isActive: Boolean,
    val cards: List<EmployeeCard>
)

// ================= CONSULTAS A SUPABASE =================

private suspend fun loadEmployees(): List<Employee> {
    return SupabaseClient.client
        .from("personas")
        .select(
            columns = Columns.raw(
                "id,codigo,nombre,activo,tarjetas(uid,activa)"
            )
        )
        .decodeList<JsonObject>()
        .map { row ->
            Employee(
                id = row.getValue("id").jsonPrimitive.content,
                code = row.getValue("codigo").jsonPrimitive.content,
                name = row.getValue("nombre").jsonPrimitive.content,
                isActive = row.getValue("activo").jsonPrimitive.boolean,
                cards = (row["tarjetas"] as? JsonArray)
                    ?.map { element ->
                        val card = element.jsonObject
                        EmployeeCard(
                            uid = card.getValue("uid").jsonPrimitive.content,
                            isActive = card.getValue("activa").jsonPrimitive.boolean
                        )
                    }
                    .orEmpty()
            )
        }
        .sortedBy { it.name.lowercase() }
}

private suspend fun loadEmployeeAccessCounts(cardUids: List<String>): List<DateCountRecord> {
    if (cardUids.isEmpty()) return emptyList()

    val cleanUids = cardUids.map { it.trim().uppercase() }

    val rows = SupabaseClient.client
        .from("accesos")
        .select {
            filter {
                isIn("uid", cleanUids)
            }
        }
        .decodeList<JsonObject>()

    return rows.mapNotNull { row ->
        val rawDate = row["ocurrido_en"]?.jsonPrimitive?.contentOrNull
            ?: row["creado_en"]?.jsonPrimitive?.contentOrNull
            ?: row["created_at"]?.jsonPrimitive?.contentOrNull

        if (!rawDate.isNullOrBlank() && rawDate.length >= 10) {
            rawDate.substring(0, 10)
        } else null
    }
        .groupingBy { it }
        .eachCount()
        .map { (date, count) -> DateCountRecord(date, count) }
        .sortedByDescending { it.date }
}

private suspend fun registerPerson(
    name: String,
    code: String,
    cardUid: String
) {
    SupabaseClient.client.postgrest.rpc(
        function = "registrar_persona_con_tarjeta",
        parameters = buildJsonObject {
            put("p_nombre", name.trim())
            put("p_codigo", code.trim())
            put("p_uid", cardUid.trim())
        }
    )
}

private suspend fun updateEmployee(
    employeeId: String,
    name: String,
    code: String
) {
    SupabaseClient.client.postgrest.rpc(
        function = "update_employee",
        parameters = buildJsonObject {
            put("p_person_id", employeeId)
            put("p_name", name.trim())
            put("p_code", code.trim())
        }
    )
}

private suspend fun assignEmployeeCard(
    employeeId: String,
    cardUid: String
) {
    SupabaseClient.client.postgrest.rpc(
        function = "assign_employee_card",
        parameters = buildJsonObject {
            put("p_person_id", employeeId)
            put("p_uid", cardUid.trim())
        }
    )
}

private suspend fun deleteEmployee(employeeId: String) {
    SupabaseClient.client.postgrest.rpc(
        function = "delete_employee",
        parameters = buildJsonObject {
            put("p_person_id", employeeId)
        }
    )
}

private suspend fun updateEmployeeStatus(
    employeeId: String,
    isActive: Boolean
) {
    val result = SupabaseClient.client
        .from("personas")
        .update(
            buildJsonObject {
                put("activo", isActive)
            }
        ) {
            filter {
                eq("id", employeeId)
            }
            select()
        }
        .decodeList<JsonObject>()
        .singleOrNull()
        ?: error("No se pudo confirmar el cambio.")

    check(
        result["id"]?.jsonPrimitive?.content == employeeId &&
                result["activo"]?.jsonPrimitive?.boolean == isActive
    )
}

// ================= PANTALLA PRINCIPAL =================

@Composable
fun EmployeesScreen(isDarkTheme: Boolean = true) {
    var employees by remember {
        mutableStateOf<List<Employee>>(emptyList())
    }

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var refreshKey by remember { mutableIntStateOf(0) }

    var showAddDialog by remember { mutableStateOf(false) }
    var employeeToEdit by remember { mutableStateOf<Employee?>(null) }
    var employeeToAssignCard by remember { mutableStateOf<Employee?>(null) }
    var employeeToDelete by remember { mutableStateOf<Employee?>(null) }
    var employeeToToggle by remember { mutableStateOf<Employee?>(null) }

    val backgroundColor = if (isDarkTheme) Color(0xFF0C192B) else Color(0xFFF3F6FA)
    val cardColor = if (isDarkTheme) Color(0xFF192B3D) else Color.White
    val textColor = if (isDarkTheme) Color(0xFFF4F7FB) else Color(0xFF142438)
    val secondaryColor = if (isDarkTheme) Color(0xFFB3C1D1) else Color(0xFF58697B)
    val accentColor = if (isDarkTheme) Color(0xFF63E1CC) else Color(0xFF087F75)

    LaunchedEffect(refreshKey) {
        isLoading = true
        errorMessage = null

        try {
            employees = loadEmployees()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            employees = emptyList()
            errorMessage =
                "No pudimos cargar el personal. Revisá la conexión y los permisos de tu cuenta."
        } finally {
            isLoading = false
        }
    }

    val query = searchQuery.trim()

    val visibleEmployees = employees.filter { employee ->
        employee.name.contains(query, ignoreCase = true) ||
                employee.code.contains(query, ignoreCase = true) ||
                employee.cards.any {
                    it.uid.contains(query, ignoreCase = true)
                }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = "TU EQUIPO",
                    color = accentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "Personal y tarjetas",
                    color = textColor,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "Administrá el personal y su registro completo de accesos.",
                    color = secondaryColor,
                    fontSize = 15.sp
                )

                Spacer(Modifier.height(18.dp))

                Button(
                    onClick = {
                        successMessage = null
                        showAddDialog = true
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF087F75),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "+ Agregar persona",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                TextButton(
                    onClick = {
                        successMessage = null
                        refreshKey++
                    },
                    enabled = !isLoading
                ) {
                    Text(
                        text = "Actualizar",
                        color = accentColor
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(text = "Personal cargado", color = secondaryColor)
                    Text(
                        text = if (isLoading) "…" else employees.size.toString(),
                        color = textColor,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (!isLoading && errorMessage == null) {
                        val activeCount = employees.count { it.isActive }
                        val inactiveCount = employees.count { !it.isActive }

                        Text(
                            text = "$activeCount activos · $inactiveCount inactivos",
                            color = accentColor,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar nombre, código o tarjeta") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
        }

        successMessage?.let { message ->
            item { Text(text = message, color = accentColor) }
        }

        errorMessage?.let { message ->
            item { Text(text = message, color = MaterialTheme.colorScheme.error) }
        }

        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = accentColor)
                }
            }
        } else {
            if (visibleEmployees.isEmpty() && errorMessage == null) {
                item {
                    Text(
                        text = if (query.isBlank()) "Todavía no hay personal visible." else "No encontramos coincidencias.",
                        modifier = Modifier.padding(vertical = 20.dp),
                        color = secondaryColor
                    )
                }
            }

            items(
                items = visibleEmployees,
                key = { it.id }
            ) { employee ->
                EmployeeItemCard(
                    employee = employee,
                    cardColor = cardColor,
                    textColor = textColor,
                    secondaryColor = secondaryColor,
                    accentColor = accentColor,
                    onEdit = {
                        successMessage = null
                        employeeToEdit = employee
                    },
                    onAssignCard = {
                        successMessage = null
                        employeeToAssignCard = employee
                    },
                    onDelete = {
                        successMessage = null
                        employeeToDelete = employee
                    },
                    onToggleStatus = {
                        successMessage = null
                        employeeToToggle = employee
                    }
                )
            }
        }
    }

    // DIÁLOGOS
    if (showAddDialog) {
        EmployeeEditorDialog(
            employee = null,
            containerColor = cardColor,
            onDismiss = { showAddDialog = false },
            onSaved = {
                showAddDialog = false
                searchQuery = ""
                successMessage = "Persona registrada correctamente."
                refreshKey++
            }
        )
    }

    employeeToEdit?.let { employee ->
        key(employee.id) {
            EmployeeEditorDialog(
                employee = employee,
                containerColor = cardColor,
                onDismiss = { employeeToEdit = null },
                onSaved = {
                    employeeToEdit = null
                    successMessage = "Datos actualizados correctamente."
                    refreshKey++
                }
            )
        }
    }

    employeeToAssignCard?.let { employee ->
        key(employee.id) {
            AssignEmployeeCardDialog(
                employee = employee,
                containerColor = cardColor,
                onDismiss = { employeeToAssignCard = null },
                onAssigned = {
                    employeeToAssignCard = null
                    successMessage = "Tarjeta vinculada y activa para ${employee.name}."
                    refreshKey++
                }
            )
        }
    }

    employeeToDelete?.let { employee ->
        key(employee.id) {
            EmployeeActionDialog(
                title = "¿Eliminar persona?",
                message = "Vas a eliminar a ${employee.name}. Esta acción no se puede deshacer.\n\n" +
                        "Solo se permite si no tiene registros ni tarjetas vinculadas.",
                confirmText = "Eliminar",
                failureMessage = "No pudimos confirmar la eliminación. Puede tener registros o tarjetas activas.",
                isDestructive = true,
                containerColor = cardColor,
                action = { deleteEmployee(employee.id) },
                onDismiss = { employeeToDelete = null },
                onSuccess = {
                    employeeToDelete = null
                    successMessage = "Persona eliminada."
                    refreshKey++
                }
            )
        }
    }

    employeeToToggle?.let { employee ->
        val newStatus = !employee.isActive
        key(employee.id) {
            EmployeeActionDialog(
                title = if (newStatus) "¿Activar persona?" else "¿Desactivar persona?",
                message = "${employee.name} quedará " + (if (newStatus) "activa" else "inactiva") + " en el sistema.",
                confirmText = "Confirmar",
                failureMessage = "No pudimos confirmar el cambio de estado.",
                isDestructive = false,
                containerColor = cardColor,
                action = {
                    updateEmployeeStatus(
                        employeeId = employee.id,
                        isActive = newStatus
                    )
                },
                onDismiss = { employeeToToggle = null },
                onSuccess = {
                    employeeToToggle = null
                    successMessage = "Estado actualizado correctamente."
                    refreshKey++
                }
            )
        }
    }
}

// ================= TARJETA INDIVIDUAL CON CONTEO POR FECHA =================

@Composable
private fun EmployeeItemCard(
    employee: Employee,
    cardColor: Color,
    textColor: Color,
    secondaryColor: Color,
    accentColor: Color,
    onEdit: () -> Unit,
    onAssignCard: () -> Unit,
    onDelete: () -> Unit,
    onToggleStatus: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var isLogsExpanded by remember { mutableStateOf(false) }
    var dateCounts by remember { mutableStateOf<List<DateCountRecord>>(emptyList()) }
    var loadingLogs by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = employee.name,
                        color = textColor,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = "Código: ${employee.code}",
                        color = secondaryColor,
                        fontSize = 14.sp
                    )
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.semantics {
                            contentDescription = "Opciones de ${employee.name}"
                        }
                    ) {
                        Text(text = "⋮", fontSize = 28.sp, color = secondaryColor)
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Editar persona") },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Asignar tarjeta") },
                            enabled = employee.isActive,
                            onClick = {
                                menuExpanded = false
                                onAssignCard()
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Eliminar persona", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "TARJETAS ASIGNADAS",
                color = secondaryColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(6.dp))

            if (employee.cards.isEmpty()) {
                Text(
                    text = "Sin tarjeta asignada",
                    color = secondaryColor,
                    fontSize = 14.sp
                )
            } else {
                employee.cards.forEach { card ->
                    val status = if (card.isActive) "Activa" else "Inactiva"
                    Text(
                        text = "${card.uid} · $status",
                        color = if (card.isActive) accentColor else secondaryColor,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(text = "Estado del personal", color = secondaryColor, fontSize = 12.sp)
                    Text(
                        text = if (employee.isActive) "Activo" else "Inactivo",
                        color = if (employee.isActive) accentColor else secondaryColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Switch(
                    checked = employee.isActive,
                    onCheckedChange = { onToggleStatus() }
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = secondaryColor.copy(alpha = 0.2f))
            Spacer(Modifier.height(8.dp))

            // REGISTROS AGRUPADOS POR FECHA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Registros por fecha",
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    fontSize = 14.sp
                )

                TextButton(
                    onClick = {
                        val next = !isLogsExpanded
                        isLogsExpanded = next
                        if (next && dateCounts.isEmpty() && employee.cards.isNotEmpty()) {
                            loadingLogs = true
                            scope.launch {
                                try {
                                    dateCounts = loadEmployeeAccessCounts(employee.cards.map { it.uid })
                                } catch (_: Exception) {
                                    dateCounts = emptyList()
                                } finally {
                                    loadingLogs = false
                                }
                            }
                        }
                    }
                ) {
                    Text(
                        text = if (isLogsExpanded) "Ocultar" else "Ver registros",
                        color = accentColor,
                        fontSize = 13.sp
                    )
                }
            }

            if (isLogsExpanded) {
                if (loadingLogs) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = accentColor)
                    }
                } else if (employee.cards.isEmpty()) {
                    Text(
                        text = "Sin tarjetas asignadas.",
                        color = secondaryColor,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else if (dateCounts.isEmpty()) {
                    Text(
                        text = "0 registros encontrados.",
                        color = secondaryColor,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        dateCounts.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(secondaryColor.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.date,
                                    color = textColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${item.count} marcaciones",
                                    color = accentColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ================= FORMULARIO ASIGNAR TARJETA =================

@Composable
private fun AssignEmployeeCardDialog(
    employee: Employee,
    containerColor: Color,
    onDismiss: () -> Unit,
    onAssigned: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var cardUid by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        containerColor = containerColor,
        title = { Text("Asignar tarjeta") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = employee.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Código del empleado: ${employee.code}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = cardUid,
                    onValueChange = {
                        cardUid = it
                        errorMessage = null
                    },
                    enabled = !isSaving,
                    label = { Text("UID de la tarjeta") },
                    supportingText = { Text("Copiá el UID real que muestra el lector.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                errorMessage?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSaving && cardUid.isNotBlank(),
                onClick = {
                    val uid = cardUid.trim()
                    if (uid.isBlank() || uid.length > 100) {
                        errorMessage = "Ingresá un UID válido."
                    } else {
                        isSaving = true
                        errorMessage = null
                        scope.launch {
                            var assigned = false
                            try {
                                assignEmployeeCard(employeeId = employee.id, cardUid = uid)
                                assigned = true
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                errorMessage = "No pudimos confirmar la asignación. Verificá si el UID ya está en uso."
                            } finally {
                                isSaving = false
                            }
                            if (assigned) onAssigned()
                        }
                    }
                }
            ) {
                Text(text = if (isSaving) "Asignando…" else "Asignar tarjeta")
            }
        },
        dismissButton = {
            TextButton(enabled = !isSaving, onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

// ================= FORMULARIO AGREGAR / EDITAR =================

@Composable
private fun EmployeeEditorDialog(
    employee: Employee?,
    containerColor: Color,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val isEditing = employee != null
    var name by remember { mutableStateOf(employee?.name.orEmpty()) }
    var code by remember { mutableStateOf(employee?.code.orEmpty()) }
    var cardUid by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        containerColor = containerColor,
        title = { Text(if (isEditing) "Editar persona" else "Agregar persona") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.take(100)
                        errorMessage = null
                    },
                    enabled = !isSaving,
                    label = { Text("Nombre completo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = it.take(50)
                        errorMessage = null
                    },
                    enabled = !isSaving,
                    label = { Text("Código del empleado") },
                    placeholder = { Text("CYBER-001") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!isEditing) {
                    OutlinedTextField(
                        value = cardUid,
                        onValueChange = {
                            cardUid = it
                            errorMessage = null
                        },
                        enabled = !isSaving,
                        label = { Text("UID de tarjeta (opcional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                errorMessage?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSaving,
                onClick = {
                    when {
                        name.trim().length < 2 -> errorMessage = "Ingresá el nombre completo."
                        code.isBlank() -> errorMessage = "Ingresá el código del empleado."
                        else -> {
                            isSaving = true
                            errorMessage = null
                            scope.launch {
                                var saved = false
                                try {
                                    if (employee == null) {
                                        registerPerson(name = name, code = code, cardUid = cardUid)
                                    } else {
                                        updateEmployee(employeeId = employee.id, name = name, code = code)
                                    }
                                    saved = true
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    errorMessage = "Error al guardar. Verificá que el código o UID no estén repetidos."
                                } finally {
                                    isSaving = false
                                }
                                if (saved) onSaved()
                            }
                        }
                    }
                }
            ) {
                Text(text = if (isSaving) "Guardando…" else "Guardar")
            }
        },
        dismissButton = {
            TextButton(enabled = !isSaving, onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

// ================= ACCIONES (ELIMINAR / ACTIVAR) =================

@Composable
private fun EmployeeActionDialog(
    title: String,
    message: String,
    confirmText: String,
    failureMessage: String,
    isDestructive: Boolean,
    containerColor: Color,
    action: suspend () -> Unit,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isWorking by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        containerColor = containerColor,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(message)
                errorMessage?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isWorking && errorMessage == null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ),
                onClick = {
                    isWorking = true
                    scope.launch {
                        var completed = false
                        try {
                            action()
                            completed = true
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            errorMessage = failureMessage
                        } finally {
                            isWorking = false
                        }
                        if (completed) onSuccess()
                    }
                }
            ) {
                Text(text = if (isWorking) "Procesando…" else confirmText)
            }
        },
        dismissButton = {
            TextButton(enabled = !isWorking, onClick = onDismiss) {
                Text(text = if (errorMessage == null) "Cancelar" else "Cerrar")
            }
        }
    )
}