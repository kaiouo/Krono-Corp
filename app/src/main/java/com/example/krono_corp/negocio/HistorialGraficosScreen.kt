package com.example.krono_corp.negocio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController

// Modulo de Persona 4: historial de los graficos que el Asistente ha generado para este negocio.

@Composable
fun HistorialGraficosScreen(
    navController: NavController,
    negocioData: NegocioData
) {
    var seleccionado by remember { mutableStateOf<GraficoGenerado?>(null) }

    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            KronoBottomBar(navController = navController, currentRoute = Screen.HistorialGraficos.route)
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
                Spacer(Modifier.height(8.dp))
                KronoTopAvatarBar(
                    letraAvatar = "📊",
                    onBackClick = {
                        if (!navController.popBackStack()) navController.navigate(Screen.Home.route)
                    },
                    onGearClick = { navController.navigate(Screen.Configuracion.route) }
                )
            }

            item {
                Column {
                    Text(
                        text = "Historial de gráficos",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = KronoTextDark
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Gráficos que el Asistente ha generado para ${negocioData.nombreNegocio}",
                        fontSize = 13.sp,
                        color = KronoTextMuted
                    )
                }
            }

            if (negocioData.historialGraficos.isEmpty()) {
                item {
                    EstadoVacioHistorial(
                        mensaje = "Aún no has generado ningún gráfico.",
                        onIrAlAsistente = { navController.navigate(Screen.Asistente.route) }
                    )
                }
            }

            items(negocioData.historialGraficos, key = { it.id }) { grafico ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { seleccionado = grafico },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFE8F8ED)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.BarChart,
                                    contentDescription = null,
                                    tint = Color(0xFF27AE60),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(grafico.titulo, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = KronoTextDark)
                                Text("Generado: ${grafico.fecha}", fontSize = 11.sp, color = KronoTextMuted)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        MiniBarChart(datos = grafico.datos, altura = 70)
                    }
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    seleccionado?.let { grafico ->
        AlertDialog(
            onDismissRequest = { seleccionado = null },
            title = { Text(grafico.titulo, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Generado: ${grafico.fecha}", fontSize = 12.sp, color = KronoTextMuted)
                    Spacer(Modifier.height(12.dp))
                    MiniBarChart(datos = grafico.datos, altura = 110)
                    Spacer(Modifier.height(12.dp))
                    grafico.datos.forEach { (etiqueta, valor) ->
                        Text("• $etiqueta: $valor", fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { seleccionado = null }) { Text("Cerrar") }
            }
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun HistorialGraficosScreenPreview() {
    MaterialTheme {
        HistorialGraficosScreen(
            navController = rememberNavController(),
            negocioData = NegocioData()
        )
    }
}
