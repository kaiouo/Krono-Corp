
package com.example.krono_corp.Admin

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.krono_corp.data.AuthRepository
import com.example.krono_corp.data.Rol
import kotlinx.coroutines.launch

// Crea una cuenta real en Appwrite (Auth + perfil en "usuarios") con rol SUPERVISOR:
// esto es lo que la pantalla de Evaluación de administradores lista despues.
@Composable
fun NuevoAdministradorScreen() {
    var nombre by remember { mutableStateOf("") }
    var correo by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf("") }
    var esError by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
            Text(mensaje, color = if (esError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }

        Button(
            onClick = {
                when {
                    nombre.isBlank() || correo.isBlank() || telefono.isBlank() || password.isBlank() -> {
                        mensaje = "Completa todos los campos."
                        esError = true
                    }
                    password.length < 8 -> {
                        mensaje = "La contraseña debe tener al menos 8 caracteres (lo exige Appwrite)."
                        esError = true
                    }
                    else -> {
                        guardando = true
                        mensaje = ""
                        scope.launch {
                            AuthRepository.register(
                                email = correo.trim(),
                                phone = telefono.trim(),
                                password = password,
                                rol = Rol.SUPERVISOR,
                                aliasPersonalizado = nombre.trim()
                            ).onSuccess {
                                mensaje = "Administrador \"${it.alias}\" creado correctamente."
                                esError = false
                                nombre = ""; correo = ""; telefono = ""; password = ""
                            }.onFailure { e ->
                                mensaje = e.message ?: "No se pudo crear el administrador."
                                esError = true
                            }
                            guardando = false
                        }
                    }
                }
            },
            enabled = !guardando,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (guardando) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Guardar administrador")
            }
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
