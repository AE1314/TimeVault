package com.timevault.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeVaultApp(
    currentUserEmail: String,
    isDarkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    onLogout: () -> Unit,
    currentUserRole: String
) {
    var showSettings by rememberSaveable {
        mutableStateOf(false)
    }

    var selectedTab by rememberSaveable {
        mutableIntStateOf(0)
    }

    val navy = Color(0xFF0C192B)
    val mint = Color(0xFF63E1CC)

    val backgroundColor = if (isDarkTheme) {
        navy
    } else {
        Color(0xFFF2F6F8)
    }

    val cardColor = if (isDarkTheme) {
        Color(0xFF192B3D)
    } else {
        Color.White
    }

    val textColor = if (isDarkTheme) {
        Color(0xFFF2F6FA)
    } else {
        navy
    }

    val subTextColor = if (isDarkTheme) {
        Color(0xFFB6C6D5)
    } else {
        Color(0xFF526174)
    }

    val accentColor = if (isDarkTheme) {
        mint
    } else {
        Color(0xFF087F75)
    }

    val iconBackground = if (isDarkTheme) {
        Color(0xFF244B4C)
    } else {
        Color(0xFFDDF3ED)
    }

    val colorScheme = if (isDarkTheme) {
        darkColorScheme(
            primary = mint,
            onPrimary = navy,
            background = backgroundColor,
            onBackground = textColor,
            surface = cardColor,
            onSurface = textColor,
            onSurfaceVariant = subTextColor,
            secondary = mint,
            surfaceVariant = Color(0xFF24384A),
            outline = Color(0xFF7890A3)
        )
    } else {
        lightColorScheme(
            primary = accentColor,
            onPrimary = Color.White,
            background = backgroundColor,
            onBackground = textColor,
            surface = cardColor,
            onSurface = textColor,
            onSurfaceVariant = subTextColor,
            secondary = accentColor,
            surfaceVariant = Color(0xFFE8EFF2),
            outline = Color(0xFF70818F)
        )
    }

    MaterialTheme(colorScheme = colorScheme) {
        if (showSettings) {
            SettingsScreen(
                userEmail = currentUserEmail,
                userRole = currentUserRole,
                isDarkTheme = isDarkTheme,
                onThemeToggle = onThemeToggle,
                onLogoutClick = onLogout,
                onBackClick = {
                    showSettings = false
                }
            )
        } else {
            val tabLabels = listOf(
                "Accesos",
                "Personal",
                "Reportes"
            )

            val tabIcons = listOf(
                Icons.Default.List,
                Icons.Default.Person,
                Icons.Default.DateRange
            )

            Scaffold(
                containerColor = backgroundColor,
                topBar = {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment =
                                    Alignment.CenterVertically,
                                horizontalArrangement =
                                    Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(
                                            RoundedCornerShape(14.dp)
                                        )
                                        .background(
                                            mint.copy(alpha = 0.14f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector =
                                            Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = mint,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = "TimeVault",
                                        color = Color.White,
                                        fontSize = 23.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.6).sp
                                    )

                                    Text(
                                        text = "Tu empresa bajo control",
                                        color = Color(0xFFBDCDD9),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    showSettings = true
                                }
                            ) {
                                Icon(
                                    imageVector =
                                        Icons.Default.Settings,
                                    contentDescription = "Abrir ajustes",
                                    tint = mint
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = navy,
                            titleContentColor = Color.White,
                            actionIconContentColor = mint
                        )
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = cardColor,
                        tonalElevation = 0.dp
                    ) {
                        tabLabels.forEachIndexed { index, label ->
                            NavigationBarItem(
                                selected = selectedTab == index,
                                onClick = {
                                    selectedTab = index
                                },
                                icon = {
                                    Icon(
                                        imageVector = tabIcons[index],
                                        contentDescription = null
                                    )
                                },
                                label = {
                                    Text(
                                        text = label,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                colors =
                                    NavigationBarItemDefaults.colors(
                                        selectedIconColor = accentColor,
                                        selectedTextColor = accentColor,
                                        unselectedIconColor = subTextColor,
                                        unselectedTextColor = subTextColor,
                                        indicatorColor = iconBackground
                                    )
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding),
                    color = backgroundColor
                ) {
                    when (selectedTab) {
                        0 -> AccessScreen(
                            isDarkTheme = isDarkTheme
                        )

                        1 -> EmployeesScreen(
                            isDarkTheme = isDarkTheme
                        )

                        2 -> ReportsScreen(
                            isDarkTheme = isDarkTheme
                        )
                    }
                }
            }
        }
    }
}