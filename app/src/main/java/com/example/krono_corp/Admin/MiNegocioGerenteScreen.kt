
package com.example.krono_corp.Admin

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.krono_corp.data.AppUser
import com.example.krono_corp.data.NegocioRepository
import com.example.krono_corp.negocio.KronoTextMuted
import kotlinx.coroutines.launch
import com.example.krono_corp.negocio.KronoTextDark
//import com.example.krono_corp.negocio.PreguntaVenta



private val AzulGerente = Color(0xFF1677FF)
private val GrisGerente = Color(0xFF747780)
private val TextoGerente = Color(0xFF202633)

@Composable
fun MiNegocioGerenteScreen(
    usuario: AppUser,
    alVolver: () -> Unit
) {
    var nombreNegocio by remember { mutableStateOf("") }

    val preguntas = remember {
        mutableStateListOf<String>()
    }

    val preguntasVenta = remember {
        mutableStateListOf(
            "¿Quién realizó la venta?",
            "¿Qué producto o servicio se vendió?",
            "¿Cuántas unidades se vendieron?",
            "¿Cuál fue el total de la venta?",
            "¿Cuál fue el método de pago?"
        )
    }

    var nuevaPreguntaTexto by remember { mutableStateOf("") }
    var nuevaPreguntaVenta by remember { mutableStateOf("") }

    var indiceEditando by remember { mutableStateOf<Int?>(null) }
    var textoEditando by remember { mutableStateOf("") }

    var indiceVentaEditando by remember { mutableStateOf<Int?>(null) }
    var textoVentaEditando by remember { mutableStateOf("") }

    var cargando by remember { mutableStateOf(true) }
    var guardando by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    // Cargar información del negocio
    LaunchedEffect(usuario.id) {
        NegocioRepository.cargar(usuario.id)
            .onSuccess { negocio ->
                if (negocio != null) {
                    nombreNegocio = negocio.nombreNegocio
                    preguntas.clear()
                    preguntas.addAll(
                        negocio.preguntasIA ?: emptyList()
                    )
                }
            }
            .onFailure {
                mensaje = it.message ?: "Error al cargar el negocio."
            }

        cargando = false
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {

        // ENCABEZADO
        item {
            TextButton(onClick = alVolver) {
                Text("← Volver al panel")
            }

            Text(
                text = "Mi negocio",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = TextoGerente
            )

            Text(
                text = "Administra la información de tu empresa.",
                fontSize = 13.sp,
                color = GrisGerente
            )
        }

        if (cargando) {
            item {
                CircularProgressIndicator()
            }
        } else {

            // NOMBRE DEL NEGOCIO
            item {
                Text(
                    "NOMBRE DE LA EMPRESA",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = GrisGerente
                )

                OutlinedTextField(
                    value = nombreNegocio,
                    onValueChange = { nombreNegocio = it },
                    placeholder = {
                        Text("Ej. Empresa Central")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // PREGUNTAS PARA IA
            item {
                Text(
                    "PREGUNTAS PARA LA IA",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextoGerente
                )

                Text(
                    "Configura las preguntas que utilizará el asistente.",
                    fontSize = 12.sp,
                    color = GrisGerente
                )
            }

            itemsIndexed(preguntas) { index, pregunta ->

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${index + 1}. $pregunta",
                            modifier = Modifier.weight(1f),
                            fontSize = 13.sp
                        )

                        IconButton(
                            onClick = {
                                textoEditando = pregunta
                                indiceEditando = index
                            }
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Editar"
                            )
                        }

                        IconButton(
                            onClick = {
                                preguntas.removeAt(index)
                            }
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Eliminar",
                                tint = Color.Red
                            )
                        }
                    }
                }
            }

            // AGREGAR PREGUNTA IA
            item {
                OutlinedTextField(
                    value = nuevaPreguntaTexto,
                    onValueChange = { nuevaPreguntaTexto = it },
                    label = { Text("Nueva pregunta para la IA") },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        if (nuevaPreguntaTexto.isNotBlank()) {
                            preguntas.add(nuevaPreguntaTexto.trim())
                            nuevaPreguntaTexto = ""
                        }
                    }
                ) {
                    Text("Agregar pregunta")
                }
            }

            // PREGUNTAS DE VENTAS
            item {
                Spacer(Modifier.height(8.dp))

                Text(
                    "PREGUNTAS AL REGISTRAR UNA VENTA",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextoGerente
                )

                Text(
                    "Información que se solicitará al registrar cada venta.",
                    fontSize = 12.sp,
                    color = GrisGerente
                )
            }



            itemsIndexed(preguntasVenta) { index, pregunta ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}. $pregunta",
                            fontSize = 13.sp,
                            color = KronoTextDark,
                            modifier = Modifier.weight(1f)
                        )

                        Switch(
                            checked = pregunta in preguntasVenta,
                            onCheckedChange = { activar ->
                                if (activar && pregunta !in preguntasVenta) {
                                    preguntasVenta.add(pregunta)
                                } else if (!activar) {
                                    preguntasVenta.remove(pregunta)
                                }
                            }
                        )
                    }
                }
            }

            // AGREGAR PREGUNTA DE VENTA
            item {
                OutlinedTextField(
                    value = nuevaPreguntaVenta,
                    onValueChange = { nuevaPreguntaVenta = it },
                    label = { Text("Nueva pregunta de venta") },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        if (nuevaPreguntaVenta.isNotBlank()) {
                            preguntasVenta.add(nuevaPreguntaVenta.trim())
                            nuevaPreguntaVenta = ""
                        }
                    }
                ) {
                    Text("Agregar")
                }
            }

            // GUARDAR
            item {
                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (nombreNegocio.isBlank()) {
                            mensaje = "Escribe el nombre de tu empresa."
                        } else {
                            scope.launch {
                                guardando = true
                                mensaje = null

                                NegocioRepository.guardar(
                                    ownerId = usuario.id,
                                    nombreNegocio = nombreNegocio,
                                    preguntasIA = preguntas.toList()
                                ).onSuccess {
                                    mensaje = "¡Empresa guardada correctamente!"
                                }.onFailure {
                                    mensaje = it.message
                                        ?: "No se pudieron guardar los cambios."
                                }

                                guardando = false
                            }
                        }
                    },
                    enabled = !guardando,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AzulGerente
                    )
                ) {
                    Text(
                        if (guardando) "Guardando..." else "Guardar cambios"
                    )
                }

                mensaje?.let {
                    Text(
                        it,
                        color = AzulGerente,
                        fontSize = 13.sp
                    )
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // EDITAR PREGUNTA DE IA
    indiceEditando?.let { indice ->
        AlertDialog(
            onDismissRequest = {
                indiceEditando = null
            },
            title = {
                Text("Editar pregunta")
            },
            text = {
                OutlinedTextField(
                    value = textoEditando,
                    onValueChange = { textoEditando = it },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (textoEditando.isNotBlank()) {
                            preguntas[indice] = textoEditando.trim()
                        }
                        indiceEditando = null
                    }
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        indiceEditando = null
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // EDITAR PREGUNTA DE VENTA
    indiceVentaEditando?.let { indice ->
        AlertDialog(
            onDismissRequest = {
                indiceVentaEditando = null
            },
            title = {
                Text("Editar pregunta de venta")
            },
            text = {
                OutlinedTextField(
                    value = textoVentaEditando,
                    onValueChange = { textoVentaEditando = it },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (textoVentaEditando.isNotBlank()) {
                            preguntasVenta[indice] = textoVentaEditando.trim()
                        }
                        indiceVentaEditando = null
                    }
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        indiceVentaEditando = null
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}
