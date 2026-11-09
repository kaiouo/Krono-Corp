package com.example.krono_corp.negocio

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.krono_corp.data.AppUser
import com.example.krono_corp.data.ConfigVentasRepository
import com.example.krono_corp.data.GraficoReporte
import com.example.krono_corp.data.PreguntaVenta
import com.example.krono_corp.reportes.abrirArchivo
import com.example.krono_corp.reportes.compartirArchivo
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// ==============================================================
// Modulo de Persona 4: Asistente (ChatBot) de DUENO y SUPERVISOR.
// El "cerebro" (registrar venta, consultar, exportar, eliminar) esta en VentasChat.kt;
// esta pantalla solo dibuja el chat y reparte los mensajes hacia VentasChat / Groq.
// Generar reporte y generar grafico usan las ventas REALES (tabla "registro_ventas"),
// no datos de ejemplo.
// ==============================================================

private val formatoFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

data class MensajeChatNegocio(
    val texto: String,
    val esUsuario: Boolean,
    val archivos: List<File> = emptyList(),
    val grafico: GraficoReporte? = null
)

/** Guarda el chat aunque el usuario cambie de pestaña dentro de la app. */
class AsistenteNegocioViewModel : ViewModel() {

    val mensajes = mutableStateListOf<MensajeChatNegocio>()
    var ocupado by mutableStateOf(false)
        private set

    private var chat: VentasChat? = null
    private var iniciado = false

    fun iniciar(
        usuario: AppUser,
        negocio: NegocioData,
        context: Context,
        onArchivoExportado: (String, String, List<File>, GraficoReporte?) -> Unit
    ) {
        if (iniciado) return
        iniciado = true
        chat = VentasChat(
            usuario = usuario,
            negocio = negocio,
            context = context,
            alExportar = { titulo, contenido, archivos, grafico ->
                onArchivoExportado(titulo, contenido, archivos, grafico)
            },
            alGuardarPreguntas = { lista: List<PreguntaVenta> ->
                viewModelScope.launch {
                    ConfigVentasRepository.guardarPreguntas(usuario.id, lista)
                }
            }
        )
        bot(
            "Hola, ${usuario.alias.ifBlank { usuario.email }}. Soy Krono, el asistente de " +
                    "${negocio.nombreNegocio}.\n\n" +
                    "Puedo registrar una venta, consultar tus ventas, generar reportes con gráfico (de barras o de pastel) y exportarlos " +
                    "a Excel o PDF.\n\n" +
                    "Prueba con los botones o escribe, por ejemplo: «registrar venta», «ventas de este mes» " +
                    "o «exporta mis ventas del mes pasado en Excel»."
        )
    }

    private fun bot(texto: String, archivos: List<File> = emptyList(), grafico: GraficoReporte? = null) {
        mensajes.add(MensajeChatNegocio(texto, esUsuario = false, archivos = archivos, grafico = grafico))
    }

    fun enviar(texto: String) {
        val pregunta = texto.trim()
        val motor = chat ?: return
        if (pregunta.isEmpty() || ocupado) return
        mensajes.add(MensajeChatNegocio(pregunta, esUsuario = true))
        viewModelScope.launch {
            ocupado = true
            try {
                val respuesta = motor.procesar(pregunta)
                if (respuesta != null) {
                    bot(respuesta.texto, archivos = respuesta.archivos, grafico = respuesta.grafico)
                } else {
                    bot(motor.preguntarIA(pregunta))
                }
            } catch (e: Exception) {
                bot("Ocurrió un error: ${e.message}")
            } finally {
                ocupado = false
            }
        }
    }
}

@Composable
fun AsistenteScreen(
    navController: NavController,
    negocioData: NegocioData,
    usuario: AppUser,
    onReporteGenerado: (ReporteGenerado) -> Unit = {},
    // false = pantalla embebida (SUPERVISOR): sin flecha, avatar "K" ni ajustes
    mostrarBarras: Boolean = true
) {
    val context = LocalContext.current
    val vm: AsistenteNegocioViewModel = viewModel(key = usuario.id)
    val listState = rememberLazyListState()
    var textoEntrada by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(usuario.id) {
        vm.iniciar(usuario, negocioData, context) { titulo, contenido, archivos, grafico ->
            onReporteGenerado(
                ReporteGenerado(
                    id = 0,
                    titulo = titulo,
                    fecha = LocalDateTime.now().format(formatoFecha),
                    contenido = contenido,
                    archivos = archivos.map { it.absolutePath },
                    grafico = grafico
                )
            )
        }
    }

    val total = vm.mensajes.size + if (vm.ocupado) 1 else 0
    LaunchedEffect(total) {
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    val libre = !vm.ocupado

    fun enviar() {
        if (textoEntrada.isBlank() || !libre) return
        vm.enviar(textoEntrada)
        textoEntrada = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KronoBackground)
            .imePadding()
    ) {
        if (mostrarBarras) {
            KronoTopAvatarBar(
                letraAvatar = "K",
                onBackClick = {
                    if (!navController.popBackStack()) navController.navigate(Screen.Home.route)
                },
                onGearClick = { navController.navigate(Screen.Configuracion.route) }
            )
        } else {
            Spacer(Modifier.height(12.dp))
        }

        Text(
            text = "Asistente",
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = KronoTextDark,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Text(
            text = "Krono · registra y analiza las ventas reales de ${negocioData.nombreNegocio}",
            fontSize = 12.sp,
            color = KronoTextMuted,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(vm.mensajes) { mensaje ->
                BurbujaChatNegocio(
                    mensaje = mensaje,
                    alAbrir = { abrirArchivo(context, it) },
                    alCompartir = { compartirArchivo(context, it) }
                )
            }
            if (vm.ocupado) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text("Krono está trabajando…", fontSize = 12.sp, color = KronoTextMuted)
                        }
                    }
                }
            }
        }

        // Preguntas que el dueño configuró para este negocio (si hay)
        if (negocioData.preguntasIA.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                items(negocioData.preguntasIA.take(4)) { pregunta ->
                    AssistChip(
                        onClick = { if (libre) vm.enviar(pregunta) },
                        enabled = libre,
                        label = { Text(pregunta, fontSize = 11.sp) },
                        shape = RoundedCornerShape(12.dp),
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color.White)
                    )
                }
            }
        }

        // Acciones rápidas
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 6.dp)
        ) {
            item {
                ChipAccionNegocio("Registrar venta", Icons.AutoMirrored.Filled.PlaylistAdd, libre) { vm.enviar("registrar venta") }
            }
            item {
                ChipAccionNegocio("Ventas del mes", Icons.Default.Assessment, libre) { vm.enviar("ventas de este mes") }
            }
            item {
                // El reporte siempre lleva gráfico: Krono pregunta si lo quieres de barras o de pastel
                ChipAccionNegocio("Generar reporte", Icons.Default.Assessment, libre) { vm.enviar("generar reporte de este mes") }
            }
            item {
                ChipAccionNegocio("Excel", Icons.Default.TableChart, libre) { vm.enviar("exporta mis ventas de este mes en Excel") }
            }
            item {
                ChipAccionNegocio("PDF", Icons.Default.PictureAsPdf, libre) { vm.enviar("exporta mis ventas de este mes en PDF") }
            }
        }

        // Barra para escribir una pregunta libre
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = textoEntrada,
                onValueChange = { textoEntrada = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Escríbele a Krono…", fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { enviar() }),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = KronoSurfaceVariant,
                    focusedContainerColor = Color.White,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = KronoBlue
                )
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { enviar() },
                enabled = libre && textoEntrada.isNotBlank(),
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (libre && textoEntrada.isNotBlank()) KronoBlue else KronoSurfaceVariant)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Enviar",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ChipAccionNegocio(
    titulo: String,
    icono: ImageVector,
    habilitado: Boolean,
    alPulsar: () -> Unit
) {
    AssistChip(
        onClick = alPulsar,
        enabled = habilitado,
        label = { Text(titulo, fontSize = 12.sp) },
        leadingIcon = { Icon(icono, contentDescription = null, tint = KronoBlue, modifier = Modifier.size(16.dp)) },
        shape = RoundedCornerShape(12.dp),
        colors = AssistChipDefaults.assistChipColors(containerColor = Color.White)
    )
}

@Composable
private fun BurbujaChatNegocio(
    mensaje: MensajeChatNegocio,
    alAbrir: (File) -> Unit,
    alCompartir: (File) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mensaje.esUsuario) Arrangement.End else Arrangement.Start
    ) {
        Column(modifier = Modifier.widthIn(max = 300.dp)) {
            if (!mensaje.esUsuario) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)) {
                    Icon(Icons.Default.SmartToy, contentDescription = null, tint = KronoBlue, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Krono", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KronoTextMuted)
                }
            }
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (mensaje.esUsuario) 16.dp else 4.dp,
                            bottomEnd = if (mensaje.esUsuario) 4.dp else 16.dp
                        )
                    )
                    .background(if (mensaje.esUsuario) KronoBlue else Color(0xFFF0F3F6))
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = mensaje.texto,
                        fontSize = 13.sp,
                        color = if (mensaje.esUsuario) Color.White else KronoTextDark
                    )
                    mensaje.grafico?.let { grafico ->
                        Spacer(Modifier.height(10.dp))
                        GraficoReporteView(grafico = grafico, altura = 110)
                    }
                    mensaje.archivos.forEach { archivo ->
                        val tipo = if (archivo.extension.equals("pdf", ignoreCase = true)) "PDF" else "Excel"
                        Row {
                            TextButton(onClick = { alAbrir(archivo) }) { Text("Abrir $tipo") }
                            TextButton(onClick = { alCompartir(archivo) }) { Text("Compartir") }
                        }
                    }
                }
            }
        }
    }
}