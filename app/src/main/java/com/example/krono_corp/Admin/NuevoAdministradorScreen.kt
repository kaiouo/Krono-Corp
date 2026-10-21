package com.example.krono_corp.Admin


import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
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
fun NuevoAdministradorScreen() {
    var nombre by remember { mutableStateOf("") }
    var id by remember { mutableStateOf("") }
    var departamento by remember { mutableStateOf("") }
    var rol by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text(
            "Nuevo gerente de Ventas",
            fontSize = 26.sp,
            lineHeight = 31.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(22.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Nombre Completo")
                CampoTexto(nombre, { nombre = it }, "p. ej. Jane Doe")

                Etiqueta("ID de Empleado")
                CampoTexto(id, { id = it }, "p. ej. SA-8492")

                Etiqueta("Departamento")
                Selector(
                    valor = departamento,
                    opciones = listOf(
                        "Ventas", "Administración", "Operaciones",
                        "Recursos Humanos", "Tecnología"
                    ),
                    alSeleccionar = { departamento = it },
                    sugerencia = "Seleccionar Departamento"
                )

                Etiqueta("Nivel de Rol")
                Selector(
                    valor = rol,
                    opciones = listOf(
                        "Administrador", "Gerente", "Supervisor"
                    ),
                    alSeleccionar = { rol = it },
                    sugerencia = "Seleccionar Nivel de Rol"
                )

                Spacer(Modifier.height(20.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            nombre = ""
                            id = ""
                            departamento = ""
                            rol = ""
                            mensaje = ""
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar")
                    }

                    Button(
                        onClick = {
                            mensaje = "Vista de prueba: aún no se guardan datos."
                        },
                        modifier = Modifier.weight(1.4f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Azul
                        )
                    ) {
                        Text("Guardar Administrador")
                    }
                }

                if (mensaje.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(mensaje, color = Azul)
                }
            }
        }
    }
}



@Composable
private fun Selector(
    valor: String,
    opciones: List<String>,
    alSeleccionar: (String) -> Unit,
    sugerencia: String
) {
    var expandido by remember {
        mutableStateOf(false)
    }

    Box {
        OutlinedButton(
            onClick = { expandido = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = if (valor.isEmpty()) sugerencia else valor,
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

@Composable
private fun Etiqueta(texto: String) {
    Spacer(Modifier.height(12.dp))
    Text(
        text = texto,
        fontSize = 13.sp
    )
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
