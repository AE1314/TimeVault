package com.timevault.app.ui

import android.util.Patterns
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.timevault.app.BuildConfig
import com.timevault.app.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL

private data class InvitationResult(
    val success: Boolean,
    val message: String
)

private suspend fun sendEmployeeInvitation(
    personId: String,
    email: String
): InvitationResult {
    val token = SupabaseClient.client.auth
        .currentSessionOrNull()
        ?.accessToken
        ?: return InvitationResult(
            success = false,
            message = "Tu sesión no está disponible. Volvé a iniciar sesión."
        )

    return withContext(Dispatchers.IO) {
        val endpoint = URL(
            BuildConfig.SUPABASE_URL.trimEnd('/') +
                    "/functions/v1/invite-employee"
        )

        require(endpoint.protocol == "https")

        val connection = endpoint.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = false
            connection.doOutput = true

            connection.setRequestProperty(
                "Authorization",
                "Bearer $token"
            )
            connection.setRequestProperty(
                "apikey",
                BuildConfig.SUPABASE_KEY
            )
            connection.setRequestProperty(
                "Content-Type",
                "application/json; charset=utf-8"
            )
            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            val requestBody = buildJsonObject {
                put("person_id", personId)
                put("email", email.trim())
            }.toString()

            connection.outputStream
                .bufferedWriter(Charsets.UTF_8)
                .use { writer ->
                    writer.write(requestBody)
                }

            val status = connection.responseCode

            val responseStream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseText = responseStream
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()

            val response = try {
                Json.parseToJsonElement(responseText) as? JsonObject
            } catch (_: Exception) {
                null
            }

            val success = status in 200..299 &&
                    response?.get("success")
                        ?.jsonPrimitive
                        ?.booleanOrNull == true

            val message = if (success) {
                response?.get("message")
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?: "Invitación enviada."
            } else {
                response?.get("error")
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?: when (status) {
                        401 -> "La sesión fue rechazada. " +
                                "Volvé a iniciar sesión."
                        403 -> "Tu cuenta no tiene permiso para invitar."
                        429 -> "Hay demasiadas solicitudes. " +
                                "Esperá antes de volver a intentar."
                        else -> "No pudimos confirmar el envío " +
                                "(HTTP $status). Revisá los registros " +
                                "de invite-employee antes de reintentar."
                    }
            }

            InvitationResult(success, message)
        } finally {
            connection.disconnect()
        }
    }
}

@Composable
fun InviteEmployeeDialog(
    personId: String,
    personName: String,
    onDismiss: () -> Unit,
    onInvited: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var invitationSent by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = {
            if (!isSending) {
                if (invitationSent) onInvited() else onDismiss()
            }
        },
        title = {
            Text(
                if (invitationSent) {
                    "Invitación enviada"
                } else {
                    "Invitar a la app"
                }
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(
                    rememberScrollState()
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(personName)

                if (!invitationSent) {
                    Text(
                        text = "Ingresá el correo de esta persona. " +
                                "Recibirá un enlace para crear su contraseña " +
                                "y su cuenta tendrá el rol de empleado."
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it.take(254)
                            message = null
                        },
                        enabled = !isSending,
                        label = { Text("Correo electrónico") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                message?.let {
                    Text(
                        text = it,
                        color = if (invitationSent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }

                if (invitationSent) {
                    Text(
                        text = "Pedile que revise su bandeja de entrada " +
                                "y la carpeta de spam."
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSending,
                onClick = {
                    if (invitationSent) {
                        onInvited()
                    } else {
                        val address = email.trim()

                        if (
                            !Patterns.EMAIL_ADDRESS
                                .matcher(address)
                                .matches()
                        ) {
                            message = "Ingresá un correo válido."
                        } else {
                            isSending = true
                            message = null

                            scope.launch {
                                try {
                                    val result = sendEmployeeInvitation(
                                        personId = personId,
                                        email = address
                                    )

                                    invitationSent = result.success
                                    message = result.message
                                } catch (
                                    cancelled: CancellationException
                                ) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    message =
                                        "No pudimos confirmar el envío. " +
                                                "Revisá la conexión y comprobá " +
                                                "si llegó el correo antes de " +
                                                "volver a intentar."
                                } finally {
                                    isSending = false
                                }
                            }
                        }
                    }
                }
            ) {
                Text(
                    when {
                        isSending -> "Enviando…"
                        invitationSent -> "Listo"
                        else -> "Enviar invitación"
                    }
                )
            }
        },
        dismissButton = {
            if (!invitationSent) {
                TextButton(
                    enabled = !isSending,
                    onClick = onDismiss
                ) {
                    Text("Cancelar")
                }
            }
        }
    )
}