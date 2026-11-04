package com.example.krono_corp.negocio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.krono_corp.data.AppUser
import com.example.krono_corp.data.VentaRegistrada
import com.example.krono_corp.data.VentasRepository
import com.example.krono_corp.data.fechaHora
import java.time.LocalDate
import java.util.Locale

// Items 6 y 7: area de Metricas, real para DUENO y SUPERVISOR (cada uno ve sus propias
// ventas, tabla "registro_ventas"). Antes el boton "Metrics" de la barra inferior solo
// mostraba un Toast.
@Composable
fun MetricasScreen(
    navController: NavController,
    negocioData: NegocioData,
    usuario: AppUser
) {
    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var ventas by remember { mutableStateOf<List<VentaRegistrada>>(emptyList()) }

    LaunchedEffect(usuario.id) {
        cargando = true
        VentasRepository.listar(usuario.id)
            .onSuccess { ventas = it; error = null }
            .onFailure { error = it.message }
        cargando = false
    }

    val hoy = LocalDate.now()
    val inicioMes = hoy.withDayOfMonth(1).atStartOfDay()
    val delMes = ventas.filter { it.fechaHora >= inicioMes }

    Scaffold(
        containerColor = KronoBackground,
        bottomBar = { KronoBottomBar(navController = navController, currentRoute = Screen.Metricas.route) }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            KronoTopAvatarBar(
                letraAvatar = "📈",
                onBackClick = { if (!navController.popBackStack()) navController.navigate(Screen.Home.route) },
                onGearClick = { navController.navigate(Screen.Configuracion.route) }
            )

            Text(
                text = "Métricas",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = KronoTextDark,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Text(
                text = "Tus ventas reales en ${negocioData.nombreNegocio}",
                fontSize = 12.sp,
                color = KronoTextMuted,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )

            when {
                cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = KronoBlue)
                }
                error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(error ?: "", color = Color(0xFFEB5757))
                }
                ventas.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Aún no tienes ventas registradas. Ve al Asistente y escribe «registrar venta».",
                        color = KronoTextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TarjetaMetrica("Vendido este mes", dinero(delMes.sumOf { it.total }), Modifier.weight(1f))
                            TarjetaMetrica("Ventas este mes", delMes.size.toString(), Modifier.weight(1f))
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TarjetaMetrica("Total histórico", dinero(ventas.sumOf { it.total }), Modifier.weight(1f))
                            val prom = if (ventas.isNotEmpty()) ventas.sumOf { it.total } / ventas.size else 0.0
                            TarjetaMetrica("Venta promedio", dinero(prom), Modifier.weight(1f))
                        }
                    }

                    val porProducto = delMes.groupBy { it.producto }
                        .map { (nombre, lista) -> nombre to lista.sumOf { it.cantidad } }
                        .sortedByDescending { it.second }
                        .take(6)
                    if (porProducto.isNotEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("Unidades vendidas por producto (este mes)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Spacer(Modifier.height(12.dp))
                                    MiniBarChart(datos = porProducto, altura = 100)
                                }
                            }
                        }
                    }

                    val porSucursal = delMes.filter { it.sucursal.isNotBlank() }
                        .groupBy { it.sucursal }
                        .map { (s, l) -> s to Math.round(l.sumOf { it.total }).toInt() }
                        .sortedByDescending { it.second }
                    if (porSucursal.size > 1) {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("Ventas por sucursal (este mes, en \$)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Spacer(Modifier.height(12.dp))
                                    MiniBarChart(datos = porSucursal, altura = 90)
                                }
                            }
                        }
                    }

                    item {
                        Text("Últimas ventas", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = KronoTextDark)
                    }
                    items(ventas.take(10)) { v ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("${v.cantidad} × ${v.producto}", fontSize = 13.sp, color = KronoTextDark)
                                Text(v.fecha.take(16).replace("T", " "), fontSize = 11.sp, color = KronoTextMuted)
                            }
                            Text(dinero(v.total), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaMetrica(titulo: String, valor: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(titulo, fontSize = 12.sp, color = KronoTextMuted)
            Spacer(Modifier.height(6.dp))
            Text(valor, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = KronoTextDark)
        }
    }
}

private fun dinero(v: Double) = "$" + String.format(Locale.US, "%,.2f", v)
