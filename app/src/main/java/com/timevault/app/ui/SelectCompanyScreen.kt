package com.timevault.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SelectCompanyScreen(
    onCompanyCreated: (nombreEmpresa: String) -> Unit,
    onCompanyJoined: (codigoEmpresa: String) -> Unit
) {
    var isCreating by remember { mutableStateOf(true) }
    var companyName by remember { mutableStateOf("") }
    var companyCode by remember { mutableStateOf("") }

    val backgroundColor = Color(0xFF0D1B2A)
    val cardColor = Color(0xFF1B263B)
    val primaryGreen = Color(0xFF00A884)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardColor),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (isCreating) "Crear Empresa (Admin)" else "Unirse a Empresa",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = if (isCreating) "Serás el Administrador principal" else "Ingresa el código que te dio tu Administrador",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (isCreating) {
                    OutlinedTextField(
                        value = companyName,
                        onValueChange = { companyName = it },
                        label = { Text("Nombre de la empresa", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryGreen,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { if (companyName.isNotBlank()) onCompanyCreated(companyName.trim()) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryGreen),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Crear Empresa", color = Color.White)
                    }
                } else {
                    OutlinedTextField(
                        value = companyCode,
                        onValueChange = { companyCode = it },
                        label = { Text("Código de la empresa", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryGreen,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { if (companyCode.isNotBlank()) onCompanyJoined(companyCode.trim()) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryGreen),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Unirme a Empresa", color = Color.White)
                    }
                }

                TextButton(onClick = { isCreating = !isCreating }) {
                    Text(
                        text = if (isCreating) "¿Tienes un código? Únete aquí" else "¿Eres Jefe? Crea tu empresa",
                        color = primaryGreen,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}