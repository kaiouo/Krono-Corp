package com.example.krono_corp.negocio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController

@Composable
fun MiNegocioScreen(
    navController: NavController,
    negocioData: NegocioData,
    onGuardarCambios: (String, List<String>) -> Unit = { _, _ -> }
) {
    var nombreNegocio by remember { mutableStateOf(negocioData.nombreNegocio) }
    val preguntas = remember { mutableStateListOf(*negocioData.preguntasIA.toTypedArray()) }
    var nuevaPreguntaTexto by remember { mutableStateOf("") }

    // Estados para editar pregunta existente
    var mostrarDialogoEditar by remember { mutableStateOf(false) }
    var textoPreguntaEditando by remember { mutableStateOf("") }
    var indiceEditando by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            KronoBottomBar(navController = navController, currentRoute = Screen.MiNegocio.route)
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                KronoTopAvatarBar(
                    letraAvatar = "I",
                    onBackClick = {
                        if (!navController.popBackStack()) {
                            navController.navigate(Screen.Home.route)
                        }
                    },
                    onGearClick = { navController.navigate(Screen.Configuracion.route) }
                )
            }

            // Título
            item {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "Mi Negocio",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = KronoTextDark
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Headline",
                        fontSize = 13.sp,
                        color = KronoTextMuted,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }

            // Campo Nombre de Negocio
            item {
                Text(
                    text = "NOMBRE DEL NEGOCIO",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = KronoTextMuted
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = nombreNegocio,
                    onValueChange = { nombreNegocio = it },
                    placeholder = { Text("ej. Panadería Central", color = KronoTextMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                        focusedBorderColor = KronoBlue,
                        unfocusedContainerColor = KronoSurfaceVariant,
                        focusedContainerColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Encabezado preguntas
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "CONFIGURACIÓN DE PREGUNTAS PARA LA IA",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = KronoTextMuted
                )
            }

            // Lista de preguntas
            itemsIndexed(preguntas) { index, itemPregunta ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}. $itemPregunta",
                            fontSize = 13.sp,
                            color = KronoTextDark,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                textoPreguntaEditando = itemPregunta
                                indiceEditando = index
                                mostrarDialogoEditar = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Editar",
                                tint = KronoBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = { preguntas.removeAt(index) }) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Eliminar",
                                tint = Color(0xFFEB5757),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Input para nueva pregunta
            item {
                Text(
                    text = "Pregunta ${preguntas.size + 1} (vacía para agregar nueva)",
                    fontSize = 12.sp,
                    color = KronoTextMuted
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = nuevaPreguntaTexto,
                        onValueChange = { nuevaPreguntaTexto = it },
                        placeholder = { Text("Ingresa otra pregunta...", fontSize = 13.sp, color = KronoTextMuted) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color(0xFFE2E8F0),
                            focusedBorderColor = KronoBlue,
                            unfocusedContainerColor = KronoSurfaceVariant,
                            focusedContainerColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (nuevaPreguntaTexto.trim().isNotEmpty()) {
                                preguntas.add(nuevaPreguntaTexto.trim())
                                nuevaPreguntaTexto = ""
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE8F1FF),
                            contentColor = KronoBlue
                        ),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text("Agregar", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // Botón Guardar Cambios
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        onGuardarCambios(nombreNegocio, preguntas.toList())
                        navController.popBackStack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KronoBlue)
                ) {
                    Text(
                        text = "Guardar Cambios",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // Modal para editar la pregunta seleccionada
    if (mostrarDialogoEditar) {
        AlertDialog(
            onDismissRequest = { mostrarDialogoEditar = false },
            title = { Text("Editar pregunta para la IA", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = textoPreguntaEditando,
                    onValueChange = { textoPreguntaEditando = it },
                    label = { Text("Pregunta") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val i = indiceEditando
                        if (i != null && textoPreguntaEditando.trim().isNotEmpty()) {
                            preguntas[i] = textoPreguntaEditando.trim()
                        }
                        mostrarDialogoEditar = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KronoBlue)
                ) {
                    Text("Actualizar")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogoEditar = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MiNegocioScreenPreview() {
    MaterialTheme {
        MiNegocioScreen(
            navController = rememberNavController(),
            negocioData = NegocioData()
        )
    }
}