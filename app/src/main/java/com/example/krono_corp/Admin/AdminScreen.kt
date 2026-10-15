package com.example.krono_corp.Admin



import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AdminScreen(){
    val fondo = Color(0xFFF7F8FC)
    val azul = Color(0xFF1677FF)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(fondo)
    ) {
        // Barra superior
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("☰", fontSize = 22.sp)
            Spacer(Modifier.width(20.dp))
            Text(
                text = "Administración",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp)
        ) {
            Text(
                text = "Administración de Empleados",
                fontSize = 25.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TarjetaAdmin(
                    "Asistente", Icons.Default.Chat,
                    Color(0xFF3984F5), Modifier.weight(1f)
                ) { }
                TarjetaAdmin(
                    "Historial de reportes",
                    Icons.Default.Description,
                    Color(0xFFE7A12B), Modifier.weight(1f)
                ) { }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TarjetaAdmin(
                    "Evaluaciones", Icons.Default.Assessment,
                    Color(0xFF24A779), Modifier.weight(1f)
                ) { }
                TarjetaAdmin(
                    "Nuevo gerente", Icons.Default.PersonAdd,
                    Color(0xFFA16AF3), Modifier.weight(1f)
                ) { }
            }
        }

        // Navegación inferior provisional
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Text("Inicio", color = azul, fontSize = 12.sp)
            Text("Asistente", color = Color.Gray, fontSize = 12.sp)
            Text("Gráficos", color = Color.Gray, fontSize = 12.sp)
            Text("Añadir", color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@Composable
fun TarjetaAdmin(
    titulo: String,
    icono: ImageVector,
    colorIcono: Color,
    modifier: Modifier = Modifier,
    alPulsar: () -> Unit
) {
    Card(
        modifier = modifier
            .height(112.dp)
            .clickable { alPulsar() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, Color(0xFFD6DEEA)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icono,
                contentDescription = titulo,
                tint = colorIcono,
                modifier = Modifier.size(26.dp)
            )
            Text(
                text = titulo,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF202633)
            )
        }
    }
}
