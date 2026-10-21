
package com.example.krono_corp.Admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class Reporte(
    val id: String,
    val titulo: String,
    val fecha: String,
    val estado: String,
    val descripcion: String
)

private val reportesDemo = listOf(
    Reporte(
        "RPT-001",
        "Resumen de ventas",
        "20/10/2026",
        "Completado",
        "Reporte de demostración del resumen de ventas."
    ),
    Reporte(
        "RPT-002",
        "Auditoría de operaciones",
        "18/10/2026",
        "Pendiente",
        "Reporte de demostración pendiente de revisión."
    ),
    Reporte(
        "RPT-003",
        "Revisión de cumplimiento",
        "15/10/2026",
        "Revisar",
        "Reporte de demostración que requiere revisión."
    )
)

@Composable
fun HistorialReportesScreen() {
    var busqueda by remember { mutableStateOf("") }
    var filtro by remember { mutableStateOf("Todos") }
    var reporteSeleccionado by remember {
        mutableStateOf<Reporte?>(null)
    }

    val filtrados = reportesDemo.filter { reporte ->
        val coincideBusqueda =
            reporte.titulo.contains(busqueda, ignoreCase = true) ||
                    reporte.id.contains(busqueda, ignoreCase = true)

        val coincideEstado =
            filtro == "Todos" || reporte.estado == filtro

        coincideBusqueda && coincideEstado
    }

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

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = busqueda,
            onValueChange = { busqueda = it },
            label = { Text("Buscar por nombre o código") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(12.dp))

        Text("Filtrar por estado")

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Todos", "Completado", "Pendiente", "Revisar")
                .forEach { estado ->
                    FilterChip(
                        selected = filtro == estado,
                        onClick = { filtro = estado },
                        label = { Text(estado) }
                    )
                }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "${filtrados.size} reportes encontrados",
            color = Color.Gray
        )

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filtrados, key = { it.id }) { reporte ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Text(
                            text = reporte.id,
                            color = Color.Gray,
                            fontSize = 12.sp
                        )

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

                        AssistChip(
                            onClick = {},
                            label = { Text(reporte.estado) }
                        )

                        TextButton(
                            onClick = {
                                reporteSeleccionado = reporte
                            }
                        ) {
                            Text("Ver detalles")
                        }
                    }
                }
            }
        }
    }

    reporteSeleccionado?.let { reporte ->
        AlertDialog(
            onDismissRequest = {
                reporteSeleccionado = null
            },
            title = {
                Text(reporte.titulo)
            },
            text = {
                Column {
                    Text("Código: ${reporte.id}")
                    Text("Fecha: ${reporte.fecha}")
                    Text("Estado: ${reporte.estado}")

                    Spacer(Modifier.height(8.dp))

                    Text(reporte.descripcion)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        reporteSeleccionado = null
                    }
                ) {
                    Text("Cerrar")
                }
            }
        )
    }
}
