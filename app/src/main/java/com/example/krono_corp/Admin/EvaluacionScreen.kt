
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
import com.example.krono_corp.data.AppUser
import com.example.krono_corp.data.AuthRepository
import com.example.krono_corp.data.Rol

// Lista real de administradores (usuarios con rol SUPERVISOR), leida de Appwrite.
@Composable
fun EvaluacionScreen() {
    var busqueda by remember { mutableStateOf("") }
    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var supervisores by remember { mutableStateOf<List<AppUser>>(emptyList()) }

    LaunchedEffect(Unit) {
        cargando = true
        AuthRepository.listarPorRol(Rol.SUPERVISOR)
            .onSuccess { supervisores = it; error = "" }
            .onFailure { e -> error = e.message ?: "No se pudo cargar la lista de administradores." }
        cargando = false
    }

    val administradores = supervisores.filter {
        it.alias.contains(busqueda, true) || it.email.contains(busqueda, true)
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

        when {
            cargando -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error.isNotBlank() -> Text(error, color = Color(0xFFEB5757))
            administradores.isEmpty() -> Text("Todavía no hay administradores registrados.", color = Color.Gray)
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(administradores, key = { it.id }) { admin ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        )
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                admin.alias,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                            Text("Correo: ${admin.email}")
                            Text("Teléfono: ${admin.phone}")
                        }
                    }
                }
            }
        }
    }
}
