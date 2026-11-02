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
import com.example.krono_corp.data.GraficoRemoto
import com.example.krono_corp.data.NegocioRepository
import com.example.krono_corp.negocio.MiniBarChart
import com.example.krono_corp.negocio.decodificarDatosGrafico

// Vista del Gerente General: todos los graficos generados por los administradores,
// en cualquier negocio (coleccion "graficos", scope = "negocio").
@Composable
fun HistorialGraficosScreen() {
    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var graficos by remember { mutableStateOf<List<GraficoRemoto>>(emptyList()) }
    var seleccionado by remember { mutableStateOf<GraficoRemoto?>(null) }

    LaunchedEffect(Unit) {
        cargando = true
        NegocioRepository.listarGraficos(scope = "negocio")
            .onSuccess { graficos = it; error = "" }
            .onFailure { e -> error = e.message ?: "No se pudo cargar el historial de gráficos." }
        cargando = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Historial de gráficos",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Gráficos generados por los administradores en todos los negocios",
            fontSize = 13.sp,
            color = Color.Gray
        )
        Spacer(Modifier.height(16.dp))

        when {
            cargando -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error.isNotBlank() -> Text(error, color = Color(0xFFEB5757))
            graficos.isEmpty() -> Text("Todavía no hay gráficos generados.", color = Color.Gray)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(graficos, key = { it.id }) { grafico ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(grafico.titulo, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("Fecha: ${grafico.fecha}", color = Color.Gray)
                            Spacer(Modifier.height(10.dp))
                            MiniBarChart(datos = decodificarDatosGrafico(grafico.datosCodificados), altura = 70)
                        }
                    }
                }
            }
        }
    }
}
