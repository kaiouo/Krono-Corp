
package com.example.krono_corp.Admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class AdminDemo(
    val nombre: String,
    val correo: String,
    val telefono: String
)

@Composable
fun EvaluacionScreen() {
    var busqueda by remember { mutableStateOf("") }

    val administradores = listOf(
        AdminDemo("Elena Castro", "elena@ejemplo.com", "7000-1001"),
        AdminDemo("Marcus Ruiz", "marcus@ejemplo.com", "7000-1002"),
        AdminDemo("Sofía Hernández", "sofia@ejemplo.com", "7000-1003")
    ).filter {
        it.nombre.contains(busqueda, true) ||
                it.correo.contains(busqueda, true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Evaluación de administradores",
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = busqueda,
            onValueChange = { busqueda = it },
            label = { Text("Buscar administradores") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(12.dp))

        Text("Total de administradores: ${administradores.size}")

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(administradores) { admin ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            admin.nombre,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text("Correo: ${admin.correo}")
                        Text("Teléfono: ${admin.telefono}")
                        Text(
                            "Estado: Datos de demostración",
                            color = Color(0xFF1260C4)
                        )
                    }
                }
            }
        }
    }
}
