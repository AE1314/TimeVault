package com.timevault.app.ui

import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timevault.app.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var registering by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    val repository = remember { AuthRepository() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val navy = Color(0xFF0C192B)
    val teal = Color(0xFF087F75)
    val mint = Color(0xFF63E1CC)
    val muted = Color(0xFF526174)

    fun submit(createAccount: Boolean) {
        if (loading) return

        val cleanEmail = email.trim()

        if (!Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            isError = true
            message = "Ingresá un correo electrónico válido."
            return
        }

        if (password.isEmpty()) {
            isError = true
            message = "Ingresá tu contraseña."
            return
        }

        focusManager.clearFocus()
        loading = true
        registering = createAccount
        message = ""

        scope.launch {
            try {
                if (createAccount) {
                    val success = repository.signUp(
                        cleanEmail,
                        password
                    )

                    isError = !success
                    message = if (success) {
                        "Solicitud enviada. Revisá tu correo: " +
                                "puede ser necesario confirmar la cuenta."
                    } else {
                        "No pudimos crear la cuenta. " +
                                "Revisá los datos e intentá de nuevo."
                    }
                } else {
                    val success = repository.signIn(
                        cleanEmail,
                        password
                    )

                    if (success) {
                        password = ""
                        onLoginSuccess()
                    } else {
                        isError = true
                        message = "No pudimos iniciar sesión. " +
                                "Revisá tus datos, la confirmación " +
                                "de tu correo y tu conexión."
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                isError = true
                message = "No pudimos completar la solicitud. " +
                        "Verificá tu conexión e intentá de nuevo."
            } finally {
                loading = false
            }
        }
    }

    // Mantiene los colores del login aunque el teléfono use modo oscuro.
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = teal,
            onPrimary = Color.White,
            background = navy,
            surface = Color.White,
            onSurface = navy,
            onSurfaceVariant = muted,
            outline = Color(0xFFCBD5DF),
            error = Color(0xFFB42318)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            navy,
                            Color(0xFF132C40),
                            Color(0xFF16464A)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 460.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = mint.copy(alpha = 0.12f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = mint,
                                modifier = Modifier
                                    .padding(13.dp)
                                    .size(28.dp)
                            )
                        }

                        Text(
                            text = "TimeVault",
                            color = Color.White,
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.7).sp
                        )
                    }

                    Spacer(Modifier.height(36.dp))

                    Text(
                        text = "Tu empresa.\nBajo control.",
                        color = Color.White,
                        fontSize = 36.sp,
                        lineHeight = 41.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "Accesos, personal y reportes\nen un solo lugar.",
                        color = Color(0xFFBDCDD9),
                        fontSize = 17.sp,
                        lineHeight = 25.sp
                    )

                    Spacer(Modifier.height(30.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        color = Color.White,
                        shadowElevation = 10.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                text = "Bienvenido de nuevo",
                                color = navy,
                                fontSize = 25.sp,
                                lineHeight = 31.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(Modifier.height(8.dp))

                            Text(
                                text = "Ingresá a tu espacio de trabajo.",
                                color = muted,
                                fontSize = 16.sp
                            )

                            Spacer(Modifier.height(26.dp))

                            OutlinedTextField(
                                value = email,
                                onValueChange = {
                                    email = it
                                    message = ""
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !loading,
                                singleLine = true,
                                label = { Text("Correo electrónico") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Email,
                                        contentDescription = null
                                    )
                                },
                                shape = RoundedCornerShape(14.dp),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next
                                )
                            )

                            Spacer(Modifier.height(16.dp))

                            OutlinedTextField(
                                value = password,
                                onValueChange = {
                                    password = it
                                    message = ""
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !loading,
                                singleLine = true,
                                label = { Text("Contraseña") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = null
                                    )
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            showPassword = !showPassword
                                        },
                                        enabled = !loading
                                    ) {
                                        Icon(
                                            imageVector = if (showPassword) {
                                                Icons.Default.VisibilityOff
                                            } else {
                                                Icons.Default.Visibility
                                            },
                                            contentDescription =
                                                if (showPassword) {
                                                    "Ocultar contraseña"
                                                } else {
                                                    "Mostrar contraseña"
                                                }
                                        )
                                    }
                                },
                                visualTransformation = if (showPassword) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                shape = RoundedCornerShape(14.dp),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { submit(false) }
                                )
                            )

                            if (message.isNotEmpty()) {
                                Spacer(Modifier.height(16.dp))

                                Surface(
                                    color = if (isError) {
                                        Color(0xFFFFF0EE)
                                    } else {
                                        Color(0xFFE8F6F1)
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = message,
                                        modifier = Modifier.padding(14.dp),
                                        color = if (isError) {
                                            Color(0xFFB42318)
                                        } else {
                                            Color(0xFF17634E)
                                        },
                                        fontSize = 14.sp,
                                        lineHeight = 21.sp
                                    )
                                }
                            }

                            Spacer(Modifier.height(24.dp))

                            Button(
                                onClick = { submit(false) },
                                enabled = !loading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = teal,
                                    disabledContainerColor = teal.copy(
                                        alpha = 0.65f
                                    ),
                                    disabledContentColor = Color.White
                                )
                            ) {
                                if (loading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(12.dp))
                                }

                                Text(
                                    text = when {
                                        loading && registering ->
                                            "Creando cuenta…"
                                        loading -> "Ingresando…"
                                        else -> "Ingresar"
                                    },
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(Modifier.height(20.dp))

                            Text(
                                text = "¿Primera vez en TimeVault?",
                                modifier = Modifier.align(
                                    Alignment.CenterHorizontally
                                ),
                                color = muted,
                                fontSize = 14.sp
                            )

                            TextButton(
                                onClick = { submit(true) },
                                enabled = !loading,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .heightIn(min = 48.dp)
                            ) {
                                Text(
                                    text = "Crear una cuenta",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    Text(
                        text = "TIMEVAULT  /  CONTROL DE ACCESOS",
                        modifier = Modifier.align(
                            Alignment.CenterHorizontally
                        ),
                        color = Color(0xFFBDCDD9),
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}