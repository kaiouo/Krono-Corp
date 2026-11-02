
package com.example.krono_corp.Admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.krono_corp.data.NegocioRepository
import com.example.krono_corp.data.ReporteRemoto

// Vista del Gerente General: todos los reportes que los dueños/supervisores han generado
// con su Asistente, de cualquier negocio (coleccion "reportes", scope = "negocio").
@Composable
fun HistorialReportesScreen() {
    var busqueda by remember { mutableStateOf("") }
    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var reportes by remember { mutableStateOf<List<ReporteRemoto>>(emptyList()) }
    var reporteSeleccionado by remember { mutableStateOf<ReporteRemoto?>(null) }

    LaunchedEffect(Unit) {
        cargando = true
        NegocioRepository.listarReportes(scope = "negocio")
            .onSuccess { reportes = it; error = "" }
            .onFailure { e -> error = e.message ?: "No se pudo cargar el historial de reportes." }
        cargando = false
    }

    val filtrados = reportes.filter { it.titulo.contains(busqueda, ignoreCase = true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Historial de reportes",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(4.dp))

        Text(
            "Reportes generados por los administradores en todos los negocios",
            fontSize = 13.sp,
            color = Color.Gray
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = busqueda,
            onValueChange = { busqueda = it },
            label = { Text("Buscar por título") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "${filtrados.size} reportes encontrados",
            color = Color.Gray
        )

        Spacer(Modifier.height(8.dp))

        when {
            cargando -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error.isNotBlank() -> Text(error, color = Color(0xFFEB5757))
            filtrados.isEmpty() -> Text("Todavía no hay reportes generados.", color = Color.Gray)
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtrados, key = { it.id }) { reporte ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = reporte.titulo,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Fecha: ${reporte.fecha}",
                                color = Color.Gray
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { reporteSeleccionado = reporte }) {
                                Text("Ver detalles")
                            }
                        }
                    }
                }
            }
        }
    }

    reporteSeleccionado?.let { reporte ->
        AlertDialog(
            onDismissRequest = { reporteSeleccionado = null },
            title = { Text(reporte.titulo) },
            text = {
                Column {
                    Text("Fecha: ${reporte.fecha}")
                    Spacer(Modifier.height(8.dp))
                    Text(reporte.contenido)
                }
            },
            confirmButton = {
                TextButton(onClick = { reporteSeleccionado = null }) {
                    Text("Cerrar")
                }
            }
        )
    }
}
