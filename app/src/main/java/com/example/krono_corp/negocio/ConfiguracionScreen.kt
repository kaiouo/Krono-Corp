package com.example.krono_corp.negocio

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.krono_corp.data.AuthRepository
import kotlinx.coroutines.launch

data class ConfigOptionFigma(
    val titulo: String,
    val icono: androidx.compose.ui.graphics.vector.ImageVector,
    val iconBgColor: Color,
    val iconColor: Color,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit
)

// Items 4 y 5: pantalla de Configuración, compartida por DUENO y SUPERVISOR.
// Antes todas las opciones (menos "Mi Cuenta") eran un Toast que no hacía nada;
// ahora cada una hace algo real.
private const val PREFS = "krono_prefs"

@Composable
fun ConfiguracionScreen(
    navController: NavController,
    negocioData: NegocioData,
    onCerrarSesion: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    var mostrarCuenta by remember { mutableStateOf(false) }
    var mostrarNotificaciones by remember { mutableStateOf(false) }
    var notificaciones by remember { mutableStateOf(prefs.getBoolean("notificaciones", true)) }
    var enviandoVerificacion by remember { mutableStateOf(false) }
    var enviandoRecuperacion by remember { mutableStateOf(false) }

    val opciones = listOf(
        ConfigOptionFigma("Mi Cuenta", Icons.Outlined.Person, Color(0xFF007AFF), Color.White) {
            mostrarCuenta = true
        },
        ConfigOptionFigma("Seguridad y Contraseña", Icons.Outlined.Shield, Color(0xFF27AE60), Color.White) {
            if (enviandoRecuperacion) return@ConfigOptionFigma
            scope.launch {
                enviandoRecuperacion = true
                AuthRepository.solicitarRecuperacion(negocioData.propietarioEmail).fold(
                    onSuccess = {
                        Toast.makeText(
                            context,
                            "Te enviamos un correo a ${negocioData.propietarioEmail} para cambiar tu contraseña.",
                            Toast.LENGTH_LONG
                        ).show()
                    },
                    onFailure = { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                )
                enviandoRecuperacion = false
            }
        },
        ConfigOptionFigma("Notificaciones", Icons.Outlined.Notifications, Color(0xFFF2994A), Color.White) {
            mostrarNotificaciones = true
        },
        ConfigOptionFigma("Idioma", Icons.Outlined.Language, Color(0xFF2D9CDB), Color.White) {
            Toast.makeText(context, "Por ahora Krono Corp solo está disponible en Español (El Salvador).", Toast.LENGTH_SHORT).show()
        },
        ConfigOptionFigma("Reportar un fallo", Icons.Outlined.BugReport, Color(0xFF6E7987), Color.White) {
            Toast.makeText(
                context,
                "Escríbenos a soporte@kronocorp.app contándonos qué pasó. ¡Gracias por avisarnos!",
                Toast.LENGTH_LONG
            ).show()
        },
        ConfigOptionFigma(
            "Cerrar Sesión",
            Icons.AutoMirrored.Filled.Logout,
            Color(0xFFFFECEC),
            Color(0xFFEB5757),
            isDestructive = true
        ) {
            // Antes esto solo navegaba al Home sin cerrar sesión de verdad.
            onCerrarSesion()
        }
    )

    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            KronoBottomBar(navController = navController, currentRoute = Screen.Configuracion.route)
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                KronoTopAvatarBar(
                    letraAvatar = "U",
                    onBackClick = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onGearClick = { }
                )
            }

            // Título
            item {
                Column {
                    Text(
                        text = "Configuración",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = KronoTextDark
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Gestiona tu cuenta y preferencias de la app",
                        fontSize = 13.sp,
                        color = KronoTextMuted
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Lista de opciones estilo Figma
            items(opciones) { opt ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { opt.onClick() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0F3F6))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(opt.iconBgColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = opt.icono,
                                contentDescription = opt.titulo,
                                tint = opt.iconColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Text(
                            text = opt.titulo,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (opt.isDestructive) Color(0xFFEB5757) else KronoTextDark,
                            modifier = Modifier.weight(1f)
                        )

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = if (opt.isDestructive) Color(0xFFEB5757).copy(alpha = 0.5f) else Color(0xFFCBD5E1),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Figma Settings UI • v2.4.0",
                        fontSize = 11.sp,
                        color = KronoTextMuted
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (mostrarCuenta) {
        AlertDialog(
            onDismissRequest = { mostrarCuenta = false },
            title = { Text("Mi Cuenta") },
            text = {
                Column {
                    Text("Correo: ${negocioData.propietarioEmail}")
                    Spacer(Modifier.height(8.dp))
                    Text("Negocio: ${negocioData.nombreNegocio}")
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (enviandoVerificacion) return@Button
                            scope.launch {
                                enviandoVerificacion = true
                                AuthRepository.enviarVerificacion().fold(
                                    onSuccess = {
                                        Toast.makeText(
                                            context,
                                            "Te enviamos un correo para verificar tu cuenta.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    },
                                    onFailure = { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                                )
                                enviandoVerificacion = false
                            }
                        },
                        enabled = !enviandoVerificacion,
                        colors = ButtonDefaults.buttonColors(containerColor = KronoBlue)
                    ) {
                        Text(if (enviandoVerificacion) "Enviando…" else "Verificar mi correo")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { mostrarCuenta = false }) { Text("Cerrar") }
            }
        )
    }

    if (mostrarNotificaciones) {
        AlertDialog(
            onDismissRequest = { mostrarNotificaciones = false },
            title = { Text("Notificaciones") },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Avisarme de nuevas ventas y reportes")
                    Switch(
                        checked = notificaciones,
                        onCheckedChange = {
                            notificaciones = it
                            prefs.edit().putBoolean("notificaciones", it).apply()
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { mostrarNotificaciones = false }) { Text("Listo") }
            }
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun ConfiguracionScreenPreview() {
    MaterialTheme {
        ConfiguracionScreen(
            navController = rememberNavController(),
            negocioData = NegocioData(),
            onCerrarSesion = {}
        )
    }
}
