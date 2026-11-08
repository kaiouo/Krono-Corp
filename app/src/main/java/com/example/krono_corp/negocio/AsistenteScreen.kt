package com.example.krono_corp.negocio

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlaylistAdd
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.krono_corp.data.AppUser
import com.example.krono_corp.data.ConfigVentasRepository
import com.example.krono_corp.data.PreguntaVenta
import com.example.krono_corp.ia.AdjuntosIA
import com.example.krono_corp.ia.ArchivoAdjunto
import com.example.krono_corp.ia.GroqService
import com.example.krono_corp.reportes.abrirArchivo
import com.example.krono_corp.reportes.compartirArchivo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val grafico: List<Pair<String, Int>>? = null,
    val adjuntoNombre: String? = null
)

/** Guarda el chat aunque el usuario cambie de pestaña dentro de la app. */
class AsistenteNegocioViewModel : ViewModel() {

    val mensajes = mutableStateListOf<MensajeChatNegocio>()
    var ocupado by mutableStateOf(false)
        private set

    private var chat: VentasChat? = null
    private var iniciado = false
    private var appContext: Context? = null
    private val groq = GroqService()

    /** Archivo que el usuario adjuntó y todavía no ha enviado */
    var adjunto by mutableStateOf<ArchivoAdjunto?>(null)
        private set

    fun adjuntar(context: Context, uri: Uri) {
        val app = context.applicationContext
        viewModelScope.launch {
            AdjuntosIA.leer(app, uri)
                .onSuccess { leido ->
                    adjunto = leido
                    if (leido.truncado) {
                        bot(
                            "«${leido.nombre}» es muy largo: solo leí los primeros ${AdjuntosIA.MAX_CARACTERES} " +
                                    "caracteres. Puedo analizarlo, pero para editarlo súbelo más corto."
                        )
                    }
                }
                .onFailure { bot(it.message ?: "No pude leer el archivo.") }
        }
    }

    fun quitarAdjunto() {
        adjunto = null
    }

    fun iniciar(usuario: AppUser, negocio: NegocioData, context: Context, onArchivoExportado: (String, String, List<File>) -> Unit) {
        if (iniciado) return
        iniciado = true
        appContext = context.applicationContext
        chat = VentasChat(
            usuario = usuario,
            negocio = negocio,
            context = context,
            alExportar = { titulo, contenido, archivos ->
                onArchivoExportado(titulo, contenido, archivos)
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
                    "Puedo registrar una venta, consultar tus ventas, generar gráficos y exportarlas " +
                    "a Excel o PDF.\n\n" +
                    "Prueba con los botones o escribe, por ejemplo: «registrar venta», «ventas de este mes» " +
                    "o «exporta mis ventas del mes pasado en Excel»."
        )
    }

    private fun bot(texto: String, archivos: List<File> = emptyList(), grafico: List<Pair<String, Int>>? = null) {
        mensajes.add(MensajeChatNegocio(texto, esUsuario = false, archivos = archivos, grafico = grafico))
    }

    fun enviar(texto: String) {
        val pregunta = texto.trim()
        val motor = chat ?: return
        if (ocupado) return
        val adj = adjunto
        if (adj != null) {
            enviarConArchivo(pregunta, adj)
            return
        }
        if (pregunta.isEmpty()) return
        mensajes.add(MensajeChatNegocio(pregunta, esUsuario = true))
        viewModelScope.launch {
            ocupado = true
            try {
                val respuesta = motor.procesar(pregunta)
                if (respuesta != null) {
                    bot(respuesta.texto, archivos = respuesta.archivos)
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

    /** Analiza o edita con la IA el archivo adjunto y devuelve el archivo editado en el chat. */
    private fun enviarConArchivo(texto: String, adj: ArchivoAdjunto) {
        val app = appContext ?: return
        val instruccion = texto.ifEmpty { "Resume este archivo y dime qué se podría mejorar." }
        adjunto = null
        mensajes.add(MensajeChatNegocio(instruccion, esUsuario = true, adjuntoNombre = adj.nombre))
        viewModelScope.launch {
            ocupado = true
            try {
                procesarArchivo(adj, instruccion, app)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                bot("Ocurrió un error: ${e.message}")
            } finally {
                ocupado = false
            }
        }
    }

    private suspend fun procesarArchivo(adj: ArchivoAdjunto, instruccion: String, app: Context) {
        var respuesta = groq.enviar(emptyList(), AdjuntosIA.construirPrompt(adj, instruccion)).getOrElse {
            bot("No pude procesar el archivo en este momento. ${it.message}")
            return
        }
        var r = AdjuntosIA.separarRespuesta(respuesta)

        // La IA a veces dice que editó pero no devuelve el archivo: se lo pido una vez más
        if (r.contenido == null && !r.incompleto && !adj.truncado && AdjuntosIA.pareceEdicion(instruccion)) {
            groq.enviar(emptyList(), AdjuntosIA.construirReintento(adj, instruccion)).getOrNull()?.let { segunda ->
                val r2 = AdjuntosIA.separarRespuesta(segunda)
                if (r2.contenido != null || r2.incompleto) {
                    respuesta = segunda
                    r = r2
                }
            }
        }

        val resp = r
        val contenido = resp.contenido
        when {
            contenido != null && !adj.truncado -> {
                val res = withContext(Dispatchers.IO) { AdjuntosIA.editar(app, adj, contenido) }
                val cambios = res.cambios
                if (cambios != null && cambios.isEmpty()) {
                    res.archivo.delete()
                    bot(
                        "No hice ningún cambio: el archivo que devolvió la IA es idéntico al original. " +
                                "Prueba siendo más específico, por ejemplo: «en la hoja Ventas cambia el valor de B3 a 120»."
                    )
                } else {
                    bot(
                        resp.texto.ifBlank { "Listo, edité «${adj.nombre}»." } + AdjuntosIA.resumenCambios(res),
                        archivos = listOf(res.archivo)
                    )
                }
            }
            resp.incompleto -> bot(
                "La respuesta se cortó antes de terminar el archivo, así que no lo guardé para no perder datos. " +
                        "Prueba con un archivo más corto o pide cambios más pequeños."
            )
            AdjuntosIA.pareceEdicion(instruccion) && !adj.truncado -> bot(
                "No pude aplicar los cambios: la IA no devolvió el archivo editado, así que no modifiqué nada. " +
                        "Intenta de nuevo con una instrucción más concreta."
            )
            else -> bot(resp.texto.ifBlank { respuesta.trim() })
        }
    }

    fun generarReporte(onListo: (ReporteGenerado) -> Unit) {
        val motor = chat ?: return
        if (ocupado) return
        mensajes.add(MensajeChatNegocio("Generar reporte", esUsuario = true))
        viewModelScope.launch {
            ocupado = true
            motor.reporteDelMes().fold(
                onSuccess = { reporte ->
                    if (reporte == null) {
                        bot("Aún no tienes ventas este mes. Escribe «registrar venta» para empezar.")
                    } else {
                        bot(reporte.contenido)
                        onListo(
                            ReporteGenerado(
                                id = 0,
                                titulo = reporte.titulo,
                                fecha = LocalDateTime.now().format(formatoFecha),
                                contenido = reporte.contenido
                            )
                        )
                    }
                },
                onFailure = { bot(it.message ?: "No pude generar el reporte.") }
            )
            ocupado = false
        }
    }

    fun generarGrafico(onListo: (GraficoGenerado) -> Unit) {
        val motor = chat ?: return
        if (ocupado) return
        mensajes.add(MensajeChatNegocio("Generar gráfico", esUsuario = true))
        viewModelScope.launch {
            ocupado = true
            motor.reporteDelMes().fold(
                onSuccess = { reporte ->
                    if (reporte == null || reporte.datosGrafico.isEmpty()) {
                        bot("Aún no tienes ventas este mes para graficar.")
                    } else {
                        bot("Unidades vendidas por producto este mes:", grafico = reporte.datosGrafico)
                        onListo(
                            GraficoGenerado(
                                id = 0,
                                titulo = "Gráfico de ventas - ${reporte.titulo.substringAfter("- ")}",
                                fecha = LocalDateTime.now().format(formatoFecha),
                                datos = reporte.datosGrafico
                            )
                        )
                    }
                },
                onFailure = { bot(it.message ?: "No pude generar el gráfico.") }
            )
            ocupado = false
        }
    }
}

@Composable
fun AsistenteScreen(
    navController: NavController,
    negocioData: NegocioData,
    usuario: AppUser,
    onReporteGenerado: (ReporteGenerado) -> Unit = {},
    onGraficoGenerado: (GraficoGenerado) -> Unit = {},
    // false = pantalla embebida (SUPERVISOR): sin flecha, avatar "K" ni ajustes
    mostrarBarras: Boolean = true
) {
    val context = LocalContext.current
    val vm: AsistenteNegocioViewModel = viewModel(key = usuario.id)
    val listState = rememberLazyListState()
    var textoEntrada by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(usuario.id) {
        vm.iniciar(usuario, negocioData, context) { titulo, contenido, archivos ->
            onReporteGenerado(
                ReporteGenerado(
                    id = 0,
                    titulo = titulo,
                    fecha = LocalDateTime.now().format(formatoFecha),
                    contenido = contenido,
                    archivos = archivos.map { it.absolutePath }
                )
            )
        }
    }

    val total = vm.mensajes.size + if (vm.ocupado) 1 else 0
    LaunchedEffect(total) {
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    val libre = !vm.ocupado
    val puedeEnviar = libre && (textoEntrada.isNotBlank() || vm.adjunto != null)

    val selectorArchivo = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.adjuntar(context, uri) }

    fun enviar() {
        if (!puedeEnviar) return
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
                ChipAccionNegocio("Registrar venta", Icons.Default.PlaylistAdd, libre) { vm.enviar("registrar venta") }
            }
            item {
                ChipAccionNegocio("Ventas del mes", Icons.Default.Assessment, libre) { vm.enviar("ventas de este mes") }
            }
            item {
                ChipAccionNegocio("Generar reporte", Icons.Default.Assessment, libre) { vm.generarReporte(onReporteGenerado) }
            }
            item {
                ChipAccionNegocio("Generar gráfico", Icons.Default.BarChart, libre) { vm.generarGrafico(onGraficoGenerado) }
            }
            item {
                ChipAccionNegocio("Excel", Icons.Default.TableChart, libre) { vm.enviar("exporta mis ventas de este mes en Excel") }
            }
            item {
                ChipAccionNegocio("PDF", Icons.Default.PictureAsPdf, libre) { vm.enviar("exporta mis ventas de este mes en PDF") }
            }
        }

        // Barra para escribir una pregunta libre (con clip para adjuntar archivos)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
        ) {
            vm.adjunto?.let { adj ->
                ChipAdjuntoNegocio(nombre = adj.nombre, alQuitar = { vm.quitarAdjunto() })
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { selectorArchivo.launch(arrayOf("*/*")) },
                    enabled = libre,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Adjuntar archivo",
                        tint = if (libre) KronoBlue else KronoTextMuted
                    )
                }
                OutlinedTextField(
                    value = textoEntrada,
                    onValueChange = { textoEntrada = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            if (vm.adjunto != null) "¿Qué hago con el archivo?" else "Escríbele a Krono…",
                            fontSize = 13.sp
                        )
                    },
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
                    enabled = puedeEnviar,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (puedeEnviar) KronoBlue else KronoSurfaceVariant)
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
private fun ChipAdjuntoNegocio(nombre: String, alQuitar: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(start = 12.dp, top = 8.dp, end = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(KronoSurfaceVariant)
            .padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Description, contentDescription = null, tint = KronoBlue, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = nombre,
            fontSize = 12.sp,
            color = KronoTextDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 240.dp)
        )
        IconButton(onClick = alQuitar, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Quitar archivo", tint = KronoTextMuted, modifier = Modifier.size(16.dp))
        }
    }
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
                    mensaje.adjuntoNombre?.let { nombre ->
                        Text(
                            text = "📎 $nombre",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        text = mensaje.texto,
                        fontSize = 13.sp,
                        color = if (mensaje.esUsuario) Color.White else KronoTextDark
                    )
                    mensaje.grafico?.let { datos ->
                        Spacer(Modifier.height(10.dp))
                        MiniBarChart(datos = datos, altura = 110)
                    }
                    mensaje.archivos.forEach { archivo ->
                        val tipo = AdjuntosIA.etiquetaTipo(archivo)
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