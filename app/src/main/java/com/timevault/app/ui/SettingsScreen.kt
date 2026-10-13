package com.timevault.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userEmail: String,
    userRole: String,
    isDarkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    onLogoutClick: () -> Unit,
    onBackClick: () -> Unit
) {
    val navy = Color(0xFF0C192B)
    val mint = Color(0xFF63E1CC)

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
        navy
    }

    val secondaryText = if (isDarkTheme) {
        Color(0xFFB6C6D5)
    } else {
        Color(0xFF526174)
    }

    val accent = if (isDarkTheme) {
        mint
    } else {
        Color(0xFF087F75)
    }

    val danger = if (isDarkTheme) {
        Color(0xFFFFB4AB)
    } else {
        Color(0xFFB42318)
    }

    BackHandler(onBack = onBackClick)

    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Mi cuenta",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector =
                                Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = background,
                    titleContentColor = text,
                    navigationIconContentColor = text
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Encabezado de marca
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = navy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        navy,
                                        Color(0xFF17494D)
                                    )
                                )
                            )
                            .padding(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = mint,
                            modifier = Modifier.size(36.dp)
                        )

                        Spacer(Modifier.height(22.dp))

                        Text(
                            text = "Tu espacio.\nA tu manera.",
                            color = Color.White,
                            fontSize = 30.sp,
                            lineHeight = 35.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.7).sp
                        )

                        Spacer(Modifier.height(10.dp))

                        Text(
                            text = "Administrá tu cuenta y personalizá TimeVault.",
                            color = Color(0xFFBDCDD9),
                            fontSize = 16.sp,
                            lineHeight = 24.sp
                        )
                    }
                }

                Column {
                    Text(
                        text = "Perfil",
                        color = text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(12.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = card
                    ) {
                        Column(Modifier.padding(22.dp)) {
                            Text(
                                text = "Correo de tu cuenta",
                                color = secondaryText,
                                fontSize = 14.sp
                            )

                            Spacer(Modifier.height(8.dp))

                            Text(
                                text = userEmail.ifBlank {
                                    "Correo no disponible"
                                },
                                color = text,
                                fontSize = 18.sp,
                                lineHeight = 25.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(Modifier.height(22.dp))

                            Text(
                                text = "Rol",
                                color = secondaryText,
                                fontSize = 14.sp
                            )

                            Spacer(Modifier.height(8.dp))

                            Text(
                                text = userRole.ifBlank {
                                    "Sin rol verificado"
                                },
                                color = accent,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Column {
                    Text(
                        text = "Apariencia",
                        color = text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(12.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = card
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(22.dp),
                            verticalAlignment =
                                Alignment.CenterVertically,
                            horizontalArrangement =
                                Arrangement.spacedBy(16.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Modo oscuro",
                                    color = text,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Spacer(Modifier.height(6.dp))

                                Text(
                                    text = if (isDarkTheme) {
                                        "Tema oscuro activado"
                                    } else {
                                        "Tema claro activado"
                                    },
                                    color = secondaryText,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            }

                            Switch(
                                checked = isDarkTheme,
                                onCheckedChange = onThemeToggle,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = navy,
                                    checkedTrackColor = mint,
                                    checkedBorderColor =
                                        Color.Transparent,
                                    uncheckedThumbColor =
                                        secondaryText,
                                    uncheckedTrackColor = background,
                                    uncheckedBorderColor =
                                        secondaryText
                                )
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onLogoutClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = danger
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = danger.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        text = "Cerrar sesión",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = "TIMEVAULT",
                    modifier = Modifier.align(
                        Alignment.CenterHorizontally
                    ),
                    color = secondaryText,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}