package com.example.krono_corp.negocio

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

// Paleta Figma KronoCorp
val KronoBlue = Color(0xFF007AFF)
val KronoBackground = Color(0xFFF8F9FA)
val KronoCardBg = Color(0xFFFFFFFF)
val KronoSurfaceVariant = Color(0xFFF0F3F6)
val KronoTextDark = Color(0xFF1A1D20)
val KronoTextMuted = Color(0xFF8A94A6)
val KronoGreenBadge = Color(0xFFE8F8ED)
val KronoGreenText = Color(0xFF1EB564)

@Composable
fun KronoBottomBar(
    navController: NavController,
    currentRoute: String
) {
    val context = LocalContext.current

    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 8.dp,
        modifier = Modifier.height(72.dp)
    ) {
        val items = listOf(
            Triple("Home", Icons.Outlined.Home, Screen.Home.route),
            Triple("AI Chat", Icons.Outlined.ChatBubbleOutline, Screen.Asistente.route),
            Triple("Ops", Icons.Outlined.Settings, Screen.Configuracion.route),
            Triple("Metrics", Icons.Outlined.TrendingUp, "metrics")
        )

        items.forEach { (label, icon, targetRoute) ->
            val selected = currentRoute == targetRoute

            NavigationBarItem(
                selected = selected,
                onClick = {
                    when (targetRoute) {
                        Screen.Home.route -> {
                            if (currentRoute != Screen.Home.route) {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Home.route) { inclusive = false }
                                    launchSingleTop = true
                                }
                            }
                        }
                        Screen.Configuracion.route -> {
                            if (currentRoute != Screen.Configuracion.route) {
                                navController.navigate(Screen.Configuracion.route) {
                                    launchSingleTop = true
                                }
                            }
                        }
                        Screen.Asistente.route -> {
                            if (currentRoute != Screen.Asistente.route) {
                                navController.navigate(Screen.Asistente.route) {
                                    launchSingleTop = true
                                }
                            }
                        }
                        "metrics" -> {
                            Toast.makeText(context, "Módulo de Métricas", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        modifier = Modifier.size(24.dp),
                        tint = if (selected) KronoBlue else KronoTextMuted
                    )
                },
                label = {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) KronoBlue else KronoTextMuted
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
fun KronoTopAvatarBar(
    letraAvatar: String = "I",
    onBackClick: (() -> Unit)? = null,
    onGearClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBackClick != null) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF0F3F6))
                        .clickable { onBackClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Regresar al Home",
                        tint = KronoTextDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE6F0FF)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = letraAvatar,
                    color = KronoBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }

        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0xFFF0F3F6))
                .clickable { onGearClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Configuración",
                tint = KronoTextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}