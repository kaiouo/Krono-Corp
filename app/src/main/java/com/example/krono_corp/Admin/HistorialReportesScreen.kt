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



private val Azul = Color(0xFF1260C4)
private val Fondo = Color(0xFFF6F7F9)
private val Texto = Color(0xFF202124)
private val Gris = Color(0xFF747780)


@Composable
fun HistorialReportesScreen() {
    var busqueda by remember { mutableStateOf("") }
    var filtro by remember { mutableStateOf("Todos") }
    var mostrarFiltros by remember { mutableStateOf(false) }

    val reportes = listOf(
        Triple("RPT-2023-1042", "Resumen de Evaluación de Ventas",
            "Completado"),
        Triple("RPT-2023-1041", "Auditoría de Operaciones Mensuales",
            "Pendiente"),
        Triple("RPT-2023-1039", "Revisión Anual de Cumplimiento",
            "Revisar")
    ).filter {
        it.second.contains(busqueda, ignoreCase = true) &&
                (filtro == "Todos" || it.third == filtro)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text(
            "Historial de reportes",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(16.dp))
        CampoTexto(busqueda, { busqueda = it }, "Buscar reportes...")

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { mostrarFiltros = !mostrarFiltros },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (mostrarFiltros) "Ocultar filtros  ▴"
            else "Filtrar  ☰")
        }

        if (mostrarFiltros) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Todos", "Completado", "Pendiente", "Revisar")
                    .forEach { opcion ->
                        FilterChip(
                            selected = filtro == opcion,
                            onClick = { filtro = opcion },
                            label = { Text(opcion, fontSize = 11.sp) }
                        )
                    }
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(reportes) { reporte ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(reporte.first, fontSize = 11.sp, color = Gris)
                        Spacer(Modifier.height(4.dp))
                        Text(reporte.second, fontWeight = FontWeight.Bold,
                            fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Fecha de ejemplo • Sistema Admin",
                            color = Gris,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        AssistChip(
                            onClick = {},
                            label = { Text(reporte.third) }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text("Ver  ◉", color = Azul)
                            Spacer(Modifier.width(18.dp))
                            Text("Descargar  ↓", color = Azul)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Estadistica(
    titulo: String,
    valor: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(titulo, fontSize = 11.sp, color = Gris)
            Spacer(Modifier.height(5.dp))
            Text(valor, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Etiqueta(texto: String) {
    Spacer(Modifier.height(12.dp))
    Text(texto, fontSize = 13.sp)
}

@Composable
private fun CampoTexto(
    valor: String,
    alCambiar: (String) -> Unit,
    etiqueta: String
) {
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        placeholder = { Text(etiqueta, fontSize = 13.sp) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(4.dp)
    )
}

@Composable
private fun Selector(
    valor: String,
    opciones: List<String>,
    alSeleccionar: (String) -> Unit,
    sugerencia: String
) {
    var expandido by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(
            onClick = { expandido = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                if (valor.isEmpty()) sugerencia else valor,
                modifier = Modifier.weight(1f)
            )
            Text("⌄")
        }

        DropdownMenu(
            expanded = expandido,
            onDismissRequest = { expandido = false }
        ) {
            opciones.forEach { opcion ->
                DropdownMenuItem(
                    text = { Text(opcion) },
                    onClick = {
                        alSeleccionar(opcion)
                        expandido = false
                    }
                )
            }
        }
    }
}