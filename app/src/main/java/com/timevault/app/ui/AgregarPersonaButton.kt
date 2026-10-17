package com.timevault.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.timevault.app.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import io.github.jan.supabase.postgrest.rpc

@Composable
fun AgregarPersonaButton(
    enabled: Boolean = true,
    onGuardado: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var abierto by remember { mutableStateOf(false) }
    var nombre by remember { mutableStateOf("") }
    var codigo by remember { mutableStateOf("") }
    var uid by remember { mutableStateOf("") }
    var guardando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Button(
        enabled = enabled && !guardando,
        shape = RoundedCornerShape(14.dp),
        onClick = {
            nombre = ""
            codigo = ""
            uid = ""
            error = null
            abierto = true
        }
    ) {
        Text("+ Agregar persona")
    }

    if (abierto) {
        AlertDialog(
            onDismissRequest = {
                if (!guardando) {
                    abierto = false
                }
            },
            title = {
                Text("Agregar persona")
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(
                        rememberScrollState()
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Se registrará como personal activo " +
                                "de tu empresa.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    OutlinedTextField(
                        value = nombre,
                        onValueChange = {
                            nombre = it.take(100)
                            error = null
                        },
                        enabled = !guardando,
                        label = {
                            Text("Nombre completo")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = codigo,
                        onValueChange = {
                            codigo = it.take(50)
                            error = null
                        },
                        enabled = !guardando,
                        label = {
                            Text("Código del empleado")
                        },
                        placeholder = {
                            Text("CYBER-001")
                        },
                        supportingText = {
                            Text("Usá un código que no esté registrado.")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = uid,
                        onValueChange = {
                            uid = it.take(100)
                            error = null
                        },
                        enabled = !guardando,
                        label = {
                            Text("UID de tarjeta (opcional)")
                        },
                        supportingText = {
                            Text(
                                "Copiá el UID real del lector. " +
                                        "Si no tenés tarjeta, dejalo vacío."
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Este registro no crea todavía una " +
                                "cuenta para iniciar sesión.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    error?.let { mensaje ->
                        Text(
                            text = mensaje,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !guardando,
                    onClick = {
                        val nombreLimpio = nombre.trim()
                        val codigoLimpio = codigo.trim()
                        val uidLimpio = uid.trim()

                        when {
                            nombreLimpio.length < 2 -> {
                                error = "Ingresá el nombre completo."
                            }

                            codigoLimpio.isBlank() -> {
                                error = "Ingresá el código del empleado."
                            }

                            else -> {
                                guardando = true
                                error = null

                                scope.launch {
                                    var registrado = false

                                    try {
                                        SupabaseClient.client.postgrest.rpc(
                                            function =
                                                "registrar_persona_con_tarjeta",
                                            parameters = buildJsonObject {
                                                put("p_nombre", nombreLimpio)
                                                put("p_codigo", codigoLimpio)
                                                put("p_uid", uidLimpio)
                                            }
                                        )

                                        registrado = true
                                    } catch (
                                        cancelled: CancellationException
                                    ) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        error =
                                            "No pudimos confirmar el registro. " +
                                                    "Comprobá tu conexión, tus permisos " +
                                                    "y que el código o tarjeta no existan. " +
                                                    "Antes de reintentar, cerrá este " +
                                                    "formulario y actualizá la lista."
                                    } finally {
                                        guardando = false
                                    }

                                    if (registrado) {
                                        abierto = false
                                        onGuardado()
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text(
                        text = if (guardando) {
                            "Guardando…"
                        } else {
                            "Guardar persona"
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !guardando,
                    onClick = {
                        abierto = false
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}