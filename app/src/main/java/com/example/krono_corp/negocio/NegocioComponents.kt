package com.example.krono_corp.negocio

import android.widget.Toast
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.text.style.TextOverflow
import com.example.krono_corp.data.GraficoReporte
import androidx.compose.foundation.shape.RoundedCornerShape
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

    // Sin altura fija: NavigationBar ya calcula su alto (80dp) MÁS el espacio de la barra de
    // navegación del sistema (botones o gestos). Con height(72.dp) ese espacio se comía el
    // contenido y los iconos/etiquetas quedaban cortados arriba y abajo.
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 8.dp
    ) {
        val items = listOf(
            Triple("Home", Icons.Outlined.Home, Screen.Home.route),
            Triple("AI Chat", Icons.Outlined.ChatBubbleOutline, Screen.Asistente.route),
            Triple("Ops", Icons.Outlined.Settings, Screen.Configuracion.route),
            Triple("Metrics", Icons.Outlined.TrendingUp, Screen.Metricas.route)
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
                        Screen.Metricas.route -> {
                            if (currentRoute != Screen.Metricas.route) {
                                navController.navigate(Screen.Metricas.route) {
                                    launchSingleTop = true
                                }
                            }
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
// Grafico de barras simple, reutilizado por el historial de graficos y por los asistentes
// (negocio y gerente) para mostrar datos reales sin depender de una libreria de graficos.
@Composable
fun MiniBarChart(
    datos: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
    altura: Int = 90
) {
    val maximo = (datos.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height((altura + 36).dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        datos.forEach { (etiqueta, valor) ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(valor.toString(), fontSize = 10.sp, color = KronoTextMuted)
                Spacer(Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height((altura * valor / maximo).coerceAtLeast(4).dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(KronoBlue)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    etiqueta,
                    fontSize = 10.sp,
                    color = KronoTextDark,
                    maxLines = 1
                )
            }
        }
    }
}

// Colores de las porciones del pastel
val KronoPaleta = listOf(
    Color(0xFF007AFF), Color(0xFF27AE60), Color(0xFFF2994A),
    Color(0xFF9B51E0), Color(0xFFEB5757), Color(0xFF56CCF2)
)

// Gráfico de pastel con leyenda (etiqueta, valor y porcentaje), sin librerías externas.
@Composable
fun MiniPieChart(
    datos: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
    diametro: Int = 120
) {
    val total = datos.sumOf { it.second }.coerceAtLeast(1)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(diametro.dp)) {
            var inicio = -90f
            datos.forEachIndexed { i, dato ->
                val barrido = dato.second.toFloat() / total * 360f
                drawArc(
                    color = KronoPaleta[i % KronoPaleta.size],
                    startAngle = inicio,
                    sweepAngle = barrido,
                    useCenter = true
                )
                inicio += barrido
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            datos.forEachIndexed { i, (etiqueta, valor) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(KronoPaleta[i % KronoPaleta.size])
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "$etiqueta: $valor (${valor * 100 / total}%)",
                        fontSize = 11.sp,
                        color = KronoTextDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// Muestra el gráfico de un reporte según su tipo (barras o pastel).
@Composable
fun GraficoReporteView(
    grafico: GraficoReporte,
    modifier: Modifier = Modifier,
    altura: Int = 110
) {
    if (grafico.esPastel) MiniPieChart(datos = grafico.datos, modifier = modifier, diametro = altura)
    else MiniBarChart(datos = grafico.datos, modifier = modifier, altura = altura)
}