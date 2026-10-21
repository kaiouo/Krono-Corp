package com.example.krono_corp.negocio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Description
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

// Modulo de Persona 4: historial de los reportes que el Asistente ha generado para este negocio.

@Composable
fun HistorialReportesScreen(
    navController: NavController,
    negocioData: NegocioData
) {
    var busqueda by remember { mutableStateOf("") }
    var seleccionado by remember { mutableStateOf<ReporteGenerado?>(null) }

    val filtrados = negocioData.historialReportes.filter {
        it.titulo.contains(busqueda, ignoreCase = true)
    }

    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            KronoBottomBar(navController = navController, currentRoute = Screen.HistorialReportes.route)
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
                    letraAvatar = "📄",
                    onBackClick = {
                        if (!navController.popBackStack()) navController.navigate(Screen.Home.route)
                    },
                    onGearClick = { navController.navigate(Screen.Configuracion.route) }
                )
            }

            item {
                Column {
                    Text(
                        text = "Historial de reportes",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = KronoTextDark
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Reportes que el Asistente ha generado para ${negocioData.nombreNegocio}",
                        fontSize = 13.sp,
                        color = KronoTextMuted
                    )
                }
            }

            if (negocioData.historialReportes.isNotEmpty()) {
                item {
                    OutlinedTextField(
                        value = busqueda,
                        onValueChange = { busqueda = it },
                        placeholder = { Text("Buscar reporte", color = KronoTextMuted, fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Buscar",
                                tint = KronoTextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = KronoSurfaceVariant,
                            focusedContainerColor = Color.White,
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = KronoBlue
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (negocioData.historialReportes.isEmpty()) {
                item {
                    EstadoVacioHistorial(
                        mensaje = "Aún no has generado ningún reporte.",
                        onIrAlAsistente = { navController.navigate(Screen.Asistente.route) }
                    )
                }
            } else if (filtrados.isEmpty()) {
                item {
                    Text(
                        "No se encontró ningún reporte con ese nombre.",
                        color = KronoTextMuted,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }

            items(filtrados, key = { it.id }) { reporte ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
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
                                .background(Color(0xFFFFF3E5)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.Description,
                                contentDescription = null,
                                tint = Color(0xFFE7A12B),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(reporte.titulo, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = KronoTextDark)
                            Text("Generado: ${reporte.fecha}", fontSize = 12.sp, color = KronoTextMuted)
                        }

                        TextButton(onClick = { seleccionado = reporte }) {
                            Text("Ver", fontSize = 13.sp)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    seleccionado?.let { reporte ->
        AlertDialog(
            onDismissRequest = { seleccionado = null },
            title = { Text(reporte.titulo, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Generado: ${reporte.fecha}", fontSize = 12.sp, color = KronoTextMuted)
                    Spacer(Modifier.height(10.dp))
                    Text(reporte.contenido, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { seleccionado = null }) { Text("Cerrar") }
            }
        )
    }
}

// Estado vacio compartido entre el historial de reportes y el de graficos.
@Composable
fun EstadoVacioHistorial(mensaje: String, onIrAlAsistente: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(mensaje, color = KronoTextMuted, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onIrAlAsistente,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Ir al Asistente")
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun HistorialReportesScreenPreview() {
    MaterialTheme {
        HistorialReportesScreen(
            navController = rememberNavController(),
            negocioData = NegocioData()
        )
    }
}
