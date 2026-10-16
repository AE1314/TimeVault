package com.timevault.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.timevault.app.ui.EmployeeHomeScreen
import com.timevault.app.ui.LoginScreen
import com.timevault.app.ui.TimeVaultApp
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*

class MainActivity : ComponentActivity() {

    private var isOpeningInvitation by mutableStateOf(false)
    private var invitationError by mutableStateOf<String?>(null)
    private var passwordUserId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        passwordUserId = savedInstanceState?.getString(
            "password_user_id"
        )

        invitationError = savedInstanceState?.getString(
            "invitation_error"
        )

        val preferences = getSharedPreferences(
            "timevault_preferences",
            MODE_PRIVATE
        )

        if (passwordUserId == null && invitationError == null) {
            handleInvitation(intent)
        }

        setContent {
            var isDarkTheme by remember {
                mutableStateOf(
                    preferences.getBoolean("dark_theme", false)
                )
            }

            var signingOut by remember {
                mutableStateOf(false)
            }

            val scope = rememberCoroutineScope()
            val auth = remember { SupabaseClient.client.auth }

            val sessionStatus by auth.sessionStatus.collectAsState(
                initial = SessionStatus.Initializing
            )

            val onLogout: () -> Unit = {
                if (!signingOut) {
                    signingOut = true

                    scope.launch {
                        try {
                            auth.signOut()

                            passwordUserId = null
                            invitationError = null
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            Toast.makeText(
                                this@MainActivity,
                                "No se pudo cerrar sesión. Intentá de nuevo.",
                                Toast.LENGTH_LONG
                            ).show()
                        } finally {
                            signingOut = false
                        }
                    }
                }
            }

            val colors = if (isDarkTheme) {
                darkColorScheme(
                    primary = Color(0xFF63E1CC),
                    background = Color(0xFF0C192B),
                    surface = Color(0xFF192B3D),
                    onBackground = Color(0xFFF4F7FB),
                    onSurface = Color(0xFFF4F7FB)
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF087F75),
                    background = Color(0xFFF3F6FA),
                    surface = Color.White,
                    onBackground = Color(0xFF142438),
                    onSurface = Color(0xFF142438)
                )
            }

            MaterialTheme(colorScheme = colors) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val authenticated =
                        sessionStatus as? SessionStatus.Authenticated

                    when {
                        isOpeningInvitation -> {
                            LoadingScreen("Validando invitación…")
                        }

                        invitationError != null -> {
                            AccessMessageScreen(
                                title = "No pudimos abrir el enlace",
                                message = invitationError.orEmpty(),
                                actionText = "Continuar",
                                onAction = {
                                    invitationError = null
                                }
                            )
                        }

                        sessionStatus is SessionStatus.Initializing -> {
                            LoadingScreen("Verificando tu acceso…")
                        }

                        passwordUserId != null -> {
                            val currentUser = authenticated?.session?.user

                            if (
                                currentUser != null &&
                                currentUser.id == passwordUserId
                            ) {
                                key(currentUser.id) {
                                    SetPasswordScreen(
                                        expectedUserId = currentUser.id,
                                        userEmail = currentUser.email.orEmpty(),
                                        signingOut = signingOut,
                                        onLogout = onLogout,
                                        onSaved = {
                                            passwordUserId = null

                                            Toast.makeText(
                                                this@MainActivity,
                                                "Contraseña guardada.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    )
                                }
                            } else {
                                AccessMessageScreen(
                                    title = "Sesión no disponible",
                                    message =
                                        "Volvé a abrir tu invitación. " +
                                                "Si venció, solicitá ayuda " +
                                                "al administrador.",
                                    actionText = "Volver",
                                    onAction = {
                                        passwordUserId = null
                                    }
                                )
                            }
                        }

                        authenticated != null -> {
                            val user = authenticated.session.user

                            key(user?.id) {
                                RoleAccessGate(
                                    userId = user?.id,
                                    userEmail = user?.email
                                        ?: "Correo no disponible",
                                    isDarkTheme = isDarkTheme,
                                    signingOut = signingOut,
                                    onThemeToggle = { enabled ->
                                        isDarkTheme = enabled

                                        preferences.edit()
                                            .putBoolean(
                                                "dark_theme",
                                                enabled
                                            )
                                            .apply()
                                    },
                                    onLogout = onLogout
                                )
                            }
                        }

                        else -> {
                            LoginScreen(
                                onLoginSuccess = {
                                    // Supabase actualiza la sesión
                                    // y cambia la pantalla automáticamente.
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (!isOpeningInvitation) {
            setIntent(intent)
            handleInvitation(intent)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("password_user_id", passwordUserId)
        outState.putString("invitation_error", invitationError)

        super.onSaveInstanceState(outState)
    }

    private fun handleInvitation(incomingIntent: Intent?) {
        val uri = incomingIntent?.data ?: return

        if (
            uri.scheme != "com.timevault.app" ||
            uri.host != "auth" ||
            uri.path != "/callback"
        ) {
            return
        }

        isOpeningInvitation = true
        invitationError = null
        passwordUserId = null

        lifecycleScope.launch {
            try {
                val auth = SupabaseClient.client.auth

                withTimeout(30_000L) {
                    // Esperar a que termine de restaurarse la sesión.
                    auth.sessionStatus.first {
                        it !is SessionStatus.Initializing
                    }

                    val parameters = Uri.parse(
                        "https://callback.invalid/?" +
                                uri.encodedFragment.orEmpty()
                    )

                    val linkError =
                        parameters.getQueryParameter("error")
                            ?: uri.getQueryParameter("error")

                    check(linkError == null) {
                        "El enlace fue rechazado."
                    }

                    val type = parameters.getQueryParameter("type")

                    check(type == "invite" || type == "recovery") {
                        "El enlace no corresponde a una invitación " +
                                "o recuperación."
                    }

                    val accessToken = parameters.getQueryParameter(
                        "access_token"
                    )

                    val refreshToken = parameters.getQueryParameter(
                        "refresh_token"
                    )

                    check(!accessToken.isNullOrBlank())
                    check(!refreshToken.isNullOrBlank())

                    // Verificar la identidad antes de importar la sesión.
                    val verifiedUser = auth.retrieveUser(accessToken)

                    auth.importAuthToken(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        retrieveUser = true
                    )

                    check(
                        auth.currentUserOrNull()?.id == verifiedUser.id
                    )

                    passwordUserId = verifiedUser.id
                }

                // Quitar los tokens del Intent después de procesarlos.
                incomingIntent.data = null
                setIntent(incomingIntent)
            } catch (_: TimeoutCancellationException) {
                invitationError =
                    "La validación tardó demasiado. " +
                            "Revisá tu conexión y volvé a abrir " +
                            "el enlace más reciente."

                incomingIntent.data = null
                setIntent(incomingIntent)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                invitationError =
                    "El enlace no pudo validarse. Revisá tu conexión. " +
                            "También podría estar vencido, usado o tener " +
                            "un formato distinto al esperado."

                incomingIntent.data = null
                setIntent(incomingIntent)
            } finally {
                isOpeningInvitation = false
            }
        }
    }
}

@Composable
private fun SetPasswordScreen(
    expectedUserId: String,
    userEmail: String,
    signingOut: Boolean,
    onLogout: () -> Unit,
    onSaved: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "TIMEVAULT",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Creá tu contraseña",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = "Estás configurando el acceso de:",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = userEmail,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                errorMessage = null
            },
            label = { Text("Nueva contraseña") },
            supportingText = { Text("Usá al menos 12 caracteres.") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password
            ),
            singleLine = true,
            enabled = !isSaving && !signingOut,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = confirmation,
            onValueChange = {
                confirmation = it
                errorMessage = null
            },
            label = { Text("Confirmar contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password
            ),
            singleLine = true,
            enabled = !isSaving && !signingOut,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )

        errorMessage?.let { message ->
            Spacer(Modifier.height(12.dp))

            Text(
                text = message,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(16.dp),
            enabled = !isSaving && !signingOut,
            onClick = {
                when {
                    password.length < 12 -> {
                        errorMessage =
                            "La contraseña debe tener al menos 12 caracteres."
                    }

                    password != confirmation -> {
                        errorMessage = "Las contraseñas no coinciden."
                    }

                    else -> {
                        val newPassword = password

                        isSaving = true
                        errorMessage = null

                        scope.launch {
                            var saved = false

                            try {
                                val auth = SupabaseClient.client.auth

                                check(
                                    auth.currentUserOrNull()?.id ==
                                            expectedUserId
                                )

                                auth.updateUser {
                                    this.password = newPassword
                                }

                                password = ""
                                confirmation = ""
                                saved = true
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                errorMessage =
                                    "No se pudo guardar la contraseña. " +
                                            "Revisá la conexión. La contraseña " +
                                            "también debe cumplir las reglas " +
                                            "configuradas en Supabase."
                            } finally {
                                isSaving = false
                            }

                            if (saved) {
                                onSaved()
                            }
                        }
                    }
                }
            }
        ) {
            Text(
                text = if (isSaving) {
                    "Guardando…"
                } else {
                    "Guardar contraseña"
                }
            )
        }

        TextButton(
            onClick = onLogout,
            enabled = !isSaving && !signingOut,
            modifier = Modifier.fillMaxWidth()
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

@Composable
private fun RoleAccessGate(
    userId: String?,
    userEmail: String,
    isDarkTheme: Boolean,
    signingOut: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    onLogout: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var isAuthorized by remember { mutableStateOf(false) }
    var selectedRole by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var retryKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(userId, retryKey) {
        isLoading = true
        isAuthorized = false
        selectedRole = null
        message = ""

        var etapa = "consultar el perfil"

        try {
            if (userId.isNullOrBlank()) {
                message =
                    "No pudimos identificar tu cuenta. " +
                            "Cerrá sesión y volvé a ingresar."

                return@LaunchedEffect
            }

            // Consultar el perfil de la cuenta autenticada.
            val profile = withTimeout(20_000L) {
                SupabaseClient.client
                    .from("perfiles")
                    .select {
                        filter {
                            eq("id", userId)
                        }
                    }
                    .decodeList<JsonObject>()
                    .singleOrNull()
            }

            if (profile == null) {
                message =
                    "Tu cuenta todavía no tiene un perfil disponible. " +
                            "Solicitá al administrador que revise su asignación."

                return@LaunchedEffect
            }

            etapa = "leer los datos del perfil"

            val profileIsActive = profile["activo"]
                ?.jsonPrimitive
                ?.booleanOrNull == true

            if (!profileIsActive) {
                message =
                    "Tu perfil está deshabilitado. " +
                            "Contactá al administrador."

                return@LaunchedEffect
            }

            val role = profile["rol"]
                ?.jsonPrimitive
                ?.contentOrNull

            val companyId = profile["empresa_id"]
                ?.jsonPrimitive
                ?.contentOrNull

            if (companyId.isNullOrBlank()) {
                message = "Tu perfil no tiene una empresa asignada."

                return@LaunchedEffect
            }

            if (role == "empleado") {
                val personId = profile["persona_id"]
                    ?.jsonPrimitive
                    ?.contentOrNull

                if (personId.isNullOrBlank()) {
                    message =
                        "Tu cuenta todavía no está vinculada " +
                                "a una persona. Contactá al administrador."

                    return@LaunchedEffect
                }

                // La función SQL del panel verifica que
                // la persona y la empresa estén activas.
                selectedRole = role
                isAuthorized = true

                return@LaunchedEffect
            }

            if (role != "admin") {
                message = "Tu cuenta no tiene un rol autorizado."

                return@LaunchedEffect
            }

            etapa = "consultar la empresa"

            // Verificar la empresa del administrador.
            val company = withTimeout(20_000L) {
                SupabaseClient.client
                    .from("empresas")
                    .select {
                        filter {
                            eq("id", companyId)
                        }
                    }
                    .decodeList<JsonObject>()
                    .singleOrNull()
            }

            etapa = "leer los datos de la empresa"

            val companyIsActive = company
                ?.get("activa")
                ?.jsonPrimitive
                ?.booleanOrNull == true

            if (!companyIsActive) {
                message =
                    "Tu empresa no está disponible. " +
                            "Puede estar deshabilitada o faltar permisos."

                return@LaunchedEffect
            }

            selectedRole = role
            isAuthorized = true
        } catch (_: TimeoutCancellationException) {
            message = if (BuildConfig.DEBUG) {
                "Se agotó el tiempo al $etapa.\n\n" +
                        "Revisá tu conexión y tocá Reintentar."
            } else {
                "La consulta tardó demasiado. " +
                        "Revisá tu conexión y volvé a intentar."
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // Mostrar solo nombres de excepciones.
            // No mostrar tokens ni solicitudes completas.
            val tipos = generateSequence(error as Throwable) {
                it.cause
            }
                .take(5)
                .map { it.javaClass.simpleName }
                .distinct()
                .joinToString(" → ")

            message = if (BuildConfig.DEBUG) {
                "Falló al $etapa.\n\n" +
                        "Diagnóstico: $tipos\n\n" +
                        "Tocá Reintentar. Si continúa, " +
                        "compartí este diagnóstico."
            } else {
                "No pudimos verificar tu acceso. " +
                        "Revisá la conexión y reintentá."
            }
        } finally {
            isLoading = false
        }
    }

    when {
        isLoading -> {
            LoadingScreen("Verificando tu acceso…")
        }

        isAuthorized && selectedRole == "empleado" -> {
            key(userId) {
                EmployeeHomeScreen(
                    userEmail = userEmail,
                    isDarkTheme = isDarkTheme,
                    signingOut = signingOut,
                    onThemeToggle = onThemeToggle,
                    onLogout = onLogout
                )
            }
        }

        isAuthorized && selectedRole == "admin" -> {
            TimeVaultApp(
                currentUserEmail = userEmail,
                currentUserRole = "Administrador",
                isDarkTheme = isDarkTheme,
                onThemeToggle = onThemeToggle,
                onLogout = onLogout
            )
        }

        else -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Acceso a TimeVault",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = message.ifBlank {
                        "No pudimos autorizar tu acceso."
                    },
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = { retryKey++ },
                    enabled = !signingOut,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reintentar")
                }

                TextButton(
                    onClick = onLogout,
                    enabled = !signingOut,
                    modifier = Modifier.fillMaxWidth()
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
}

@Composable
private fun LoadingScreen(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()

        Spacer(Modifier.height(16.dp))

        Text(
            text = message,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AccessMessageScreen(
    title: String,
    message: String,
    actionText: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = message,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        Button(onClick = onAction) {
            Text(actionText)
        }
    }
}