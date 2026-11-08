package com.example.krono_corp.negocio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.krono_corp.data.PreguntaVenta
import kotlinx.coroutines.launch

@Composable
fun MiNegocioScreen(
    navController: NavController,
    negocioData: NegocioData,
    onGuardarCambios: (String, List<String>) -> Unit = { _, _ -> },
    onGuardarPreguntasVenta: (List<PreguntaVenta>) -> Unit = {},
    mostrarBarra: Boolean = true   // false = sin la barra Home / AI Chat / Ops / Metrics
) {
    var nombreNegocio by remember { mutableStateOf(negocioData.nombreNegocio) }
    val preguntasIA = remember { mutableStateListOf(*negocioData.preguntasIA.toTypedArray()) }
    val preguntasVenta = remember { mutableStateListOf(*negocioData.preguntasVenta.toTypedArray()) }
    var nuevaPreguntaIA by remember { mutableStateOf("") }
    var nuevaPreguntaVenta by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = KronoBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (mostrarBarra) {
                KronoBottomBar(navController = navController, currentRoute = Screen.MiNegocio.route)
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Store,
                        contentDescription = null,
                        tint = KronoBlue,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Configuración del Negocio",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = KronoTextDark
                    )
                }
            }

            // ---------------- Nombre del negocio ----------------
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = KronoCardBg),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Información General",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = KronoTextDark
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = nombreNegocio,
                            onValueChange = { nombreNegocio = it },
                            label = { Text("Nombre del Negocio") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // ---------------- SECCIÓN 1: preguntas para la IA del gerente ----------------
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = KronoCardBg),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Preguntas para mi asistente de IA",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = KronoTextDark
                        )
                        Text(
                            text = "Sugerencias que tú le puedes hacer a tu asistente.",
                            fontSize = 12.sp,
                            color = KronoTextDark.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        preguntasIA.forEachIndexed { index, pregunta ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                OutlinedTextField(
                                    value = pregunta,
                                    onValueChange = { preguntasIA[index] = it },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                IconButton(onClick = { preguntasIA.removeAt(index) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color.Red)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = nuevaPreguntaIA,
                                onValueChange = { nuevaPreguntaIA = it },
                                label = { Text("Nueva pregunta para mi IA") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            IconButton(onClick = {
                                if (nuevaPreguntaIA.isNotBlank()) {
                                    preguntasIA.add(nuevaPreguntaIA.trim())
                                    nuevaPreguntaIA = ""
                                }
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Agregar", tint = KronoBlue)
                            }
                        }
                    }
                }
            }

            // ---------------- SECCIÓN 2: preguntas que verán los supervisores ----------------
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = KronoCardBg),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Preguntas para supervisores",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = KronoTextDark
                        )
                        Text(
                            text = "Son las que verán tus supervisores al registrar una venta. " +
                                    "Solo tú puedes modificarlas.",
                            fontSize = 12.sp,
                            color = KronoTextDark.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        preguntasVenta.forEachIndexed { index, p ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                OutlinedTextField(
                                    value = p.texto,
                                    onValueChange = { nuevo -> preguntasVenta[index] = p.copy(texto = nuevo) },
                                    modifier = Modifier.weight(1f),
                                    textStyle = TextStyle(fontSize = 14.sp),
                                    supportingText = if (p.obligatoria) {
                                        { Text("Obligatoria: puedes reescribirla, no eliminarla") }
                                    } else null
                                )
                                if (p.obligatoria) {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = "Obligatoria",
                                        tint = KronoTextDark.copy(alpha = 0.4f),
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    )
                                } else {
                                    IconButton(onClick = { preguntasVenta.removeAt(index) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color.Red)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = nuevaPreguntaVenta,
                                onValueChange = { nuevaPreguntaVenta = it },
                                label = { Text("Nueva pregunta para supervisores") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            IconButton(onClick = {
                                if (nuevaPreguntaVenta.isNotBlank()) {
                                    preguntasVenta.add(
                                        PreguntaVenta(
                                            id = "CUSTOM_${System.currentTimeMillis()}",
                                            texto = nuevaPreguntaVenta.trim(),
                                            activa = true,
                                            obligatoria = false,
                                            personalizada = true
                                        )
                                    )
                                    nuevaPreguntaVenta = ""
                                }
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Agregar", tint = KronoBlue)
                            }
                        }
                    }
                }
            }

            // ---------------- Guardar ----------------
            item {
                Button(
                    onClick = {
                        val iaLimpias = preguntasIA.map { it.trim() }.filter { it.isNotEmpty() }

                        val ventaLimpias = preguntasVenta.mapNotNull { p ->
                            val t = p.texto.trim()
                            when {
                                t.isNotEmpty() -> p.copy(texto = t, activa = true)
                                // una obligatoria vacía vuelve a su texto original
                                p.obligatoria -> PreguntaVenta.porDefecto().firstOrNull { it.id == p.id }
                                else -> null
                            }
                        }

                        preguntasIA.clear(); preguntasIA.addAll(iaLimpias)
                        preguntasVenta.clear(); preguntasVenta.addAll(ventaLimpias)

                        onGuardarCambios(nombreNegocio.trim(), iaLimpias)
                        onGuardarPreguntasVenta(ventaLimpias)
                        scope.launch { snackbarHostState.showSnackbar("Cambios guardados") }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KronoBlue)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Guardar Cambios", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}