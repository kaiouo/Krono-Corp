
package com.example.krono_corp.Admin

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NuevoAdministradorScreen() {
    var nombre by remember { mutableStateOf("") }
    var correo by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Nuevo administrador",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = nombre,
            onValueChange = { nombre = it },
            label = { Text("Nombre completo") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = correo,
            onValueChange = { correo = it },
            label = { Text("Correo electrónico") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = telefono,
            onValueChange = { telefono = it },
            label = { Text("Teléfono") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        if (mensaje.isNotBlank()) {
            Text(mensaje, color = MaterialTheme.colorScheme.primary)
        }

        Button(
            onClick = {
                mensaje = if (
                    nombre.isBlank() ||
                    correo.isBlank() ||
                    telefono.isBlank() ||
                    password.isBlank()
                ) {
                    "Completa todos los campos."
                } else {
                    "Formulario de prueba completado."
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Guardar administrador")
        }

        OutlinedButton(
            onClick = {
                nombre = ""
                correo = ""
                telefono = ""
                password = ""
                mensaje = ""
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Limpiar")
        }
    }
}
