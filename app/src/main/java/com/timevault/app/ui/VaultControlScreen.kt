package com.timevault.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timevault.app.VaultViewModel

@Composable
fun VaultControlScreen(
    viewModel: VaultViewModel,
    isDarkTheme: Boolean = true
) {
    val uiState by viewModel.uiState.collectAsState()

    val navy = Color(0xFF0C192B)
    val mint = Color(0xFF63E1CC)

    val backgroundColor = if (isDarkTheme) {
        navy
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

    val accentColor = if (isDarkTheme) {
        mint
    } else {
        Color(0xFF087F75)
    }

    val statusColor by animateColorAsState(
        targetValue = if (uiState.isLocked) Color(0xFFD32F2F) else Color(0xFF388E3C),
        label = "statusColor"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Encabezado
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "SEGURIDAD",
                color = accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Caja Fuerte",
                color = textColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Control remoto y estado de seguridad.",
                color = secondaryColor,
                fontSize = 15.sp
            )
        }

        // Estado de Conexión
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = cardColor,
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                val isOnline = uiState.deviceStatus == "ONLINE"
                Icon(
                    imageVector = if (isOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = if (isOnline) Color(0xFF388E3C) else secondaryColor
                )
                Column {
                    Text(
                        text = if (isOnline) "Dispositivo en línea" else "Dispositivo fuera de línea",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Text(
                        text = "ID: #VAULT-001",
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryColor
                    )
                }
            }
        }

        // Indicador Visual Circular
        Box(
            modifier = Modifier
                .size(220.dp)
                .background(statusColor.copy(alpha = 0.1f), CircleShape)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                color = statusColor,
                shadowElevation = 8.dp
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (uiState.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (uiState.isLocked) "BLOQUEADA" else "ABIERTA",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp
                    )
                }
            }
        }

        // Información de RFID
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = cardColor
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Seguridad RFID",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = borderColor
                )
                
                Text(
                    text = "Último UID detectado:",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryColor
                )
                
                Text(
                    text = uiState.lastScannedUid ?: "Sin lecturas recientes",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Botón de Acción Principal
        Button(
            onClick = { viewModel.toggleLock() },
            enabled = !uiState.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (uiState.isLocked) Color(0xFF388E3C) else Color(0xFFD32F2F),
                disabledContainerColor = secondaryColor.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (uiState.isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = null
                    )
                    Text(
                        text = if (uiState.isLocked) "DESBLOQUEAR AHORA" else "BLOQUEAR AHORA",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Mensaje de Error
        uiState.errorMessage?.let { error ->
            Text(
                text = error,
                color = Color(0xFFD32F2F),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

