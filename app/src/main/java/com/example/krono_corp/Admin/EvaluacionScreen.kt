package com.example.krono_corp.Admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
fun EvaluacionScreen() {
    var busqueda by remember { mutableStateOf("") }

    val administradores = listOf(
        Triple("Elena Castro", "ADM-7721", "88.5%"),
        Triple("Marcus Ruiz", "ADM-7724", "74.2%"),
        Triple("Sofía Hernández", "ADM-7728", "92.0%")
    ).filter { it.first.contains(busqueda, ignoreCase = true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        item {
            Text(
                "Evaluación de gerente de Ventas",
                fontSize = 25.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            CampoTexto(
                valor = busqueda,
                alCambiar = { busqueda = it },
                etiqueta = "Buscar administradores..."
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Estadistica("Total Administradores", "124",
                    Modifier.weight(1f))
                Estadistica("Eficiencia Promedio", "92%",
                    Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
        }

        items(administradores) { admin ->
            Card(
                modifier = Modifier.fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(admin.first, fontWeight = FontWeight.Bold,
                        fontSize = 17.sp)
                    Text("ID: ${admin.second}", color = Gris)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Evaluación")
                        Text(admin.third, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = {
                            admin.third.removeSuffix("%").toFloat() / 100
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = Azul
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Estado: Evaluado", color = Gris)
                }
            }
        }
    }
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
        label = { Text(etiqueta) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
private fun Estadistica(
    titulo: String,
    valor: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = titulo,
                color = Gris
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = valor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}






