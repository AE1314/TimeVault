package com.timevault.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF00A884),
    background = Color(0xFF0D1B2A),
    surface = Color(0xFF1B263B)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF00A884),
    background = Color(0xFFF5F7FA),
    surface = Color(0xFFFFFFFF)
)

@Composable
fun TimeVaultTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}