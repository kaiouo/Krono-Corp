package com.example.krono_corp.Admin

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
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
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
import com.example.krono_corp.data.AppUser
import com.example.krono_corp.data.CuentaSupervisor
import com.example.krono_corp.data.GerenteRepository
import com.example.krono_corp.data.VentaRegistrada
import com.example.krono_corp.data.VentasEquipoRepository
import com.example.krono_corp.data.fechaHora
import com.example.krono_corp.ia.GestorIA
import com.example.krono_corp.ia.GroqService
import com.example.krono_corp.negocio.MiniBarChart
import com.example.krono_corp.reportes.VentasExcel
import com.example.krono_corp.reportes.VentasPdf
import com.example.krono_corp.reportes.abrirArchivo
import com.example.krono_corp.reportes.compartirArchivo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

// ==============================================================
// Asistente IA del GERENTE.
//
//  - Ve a los supervisores de su equipo Y a los que todavía no tienen gerente
//    (tabla "usuarios", rol SUPERVISOR)
//  - Analiza las ventas que ellos guardaron (tabla "registro_ventas")
//  - Exporta esas ventas a Excel, PDF y las muestra en gráficos
//  - Chat libre con Krono (Groq)
// ==============================================================

private val Azul = Color(0xFF1677FF)
private val Fondo = Color(0xFFF7F8FC)
private val TextoOscuro = Color(0xFF202633)
private val Gris = Color(0xFF747780)
private val BordeClaro = Color(0xFFD6DEEA)

data class MensajeChat(
    val texto: String,
    val esUsuario: Boolean,
    val archivos: List<File> = emptyList(),
    val grafico: List<Pair<String, Int>>? = null
)

enum class FormatoReporte { EXCEL, PDF }

private class Periodo(val desde: LocalDateTime?, val hasta: LocalDateTime?, val nombre: String)

// ---------------------------------------------------------------
// ViewModel: guarda el chat aunque cambies de pestaña en el panel
// ---------------------------------------------------------------
class AsistenteViewModel : ViewModel() {

    val mensajes = mutableStateListOf<MensajeChat>()

    /** null = libre; si tiene texto, Krono está trabajando y se muestra ese estado */
    var ocupado by mutableStateOf<String?>(null)
        private set

    private val gestor = GestorIA()
    private val groq = GroqService()
    private var iniciado = false
    private var gerenteId = ""
    private var alias = ""

    fun iniciar(usuario: AppUser) {
        if (iniciado) return
        iniciado = true
        gerenteId = usuario.id
        alias = usuario.alias
        bot(
            "Hola, ${usuario.alias}. Soy Krono, el asistente de KronoCorp.\n\n" +
                    "Puedo mostrarte a tus supervisores (y a los que aún no tienen gerente), " +
                    "analizar sus ventas, hacer gráficos y exportar a Excel o PDF.\n\n" +
                    "Prueba con los botones o escribe, por ejemplo: «¿quién vendió más este mes?» " +
                    "o «exporta las ventas del mes pasado en Excel»."
        )
    }

    private fun bot(
        texto: String,
        archivos: List<File> = emptyList(),
        grafico: List<Pair<String, Int>>? = null
    ) {
        mensajes.add(MensajeChat(texto, esUsuario = false, archivos = archivos, grafico = grafico))
    }

    private fun ejecutar(estado: String, accionUsuario: String?, bloque: suspend () -> Unit) {
        if (ocupado != null) return
        accionUsuario?.let { mensajes.add(MensajeChat(it, esUsuario = true)) }
        viewModelScope.launch {
            ocupado = estado
            try {
                bloque()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                bot("Ocurrió un error: ${e.message}")
            } finally {
                ocupado = null
            }
        }
    }

    // ---------- Supervisores ----------

    fun consultarSupervisores() = ejecutar("Consultando supervisores…", "Ver supervisores") {
        GerenteRepository.supervisoresVisibles(gerenteId).fold(
            onSuccess = { bot(textoSupervisores(it)) },
            onFailure = { bot(it.message ?: "No pude consultar los supervisores.") }
        )
    }

    fun buscarSupervisor(busqueda: String) =
        ejecutar("Buscando…", "Buscar supervisor: $busqueda") {
            GerenteRepository.supervisoresVisibles(gerenteId).fold(
                onSuccess = { lista ->
                    val encontrados = lista.filter {
                        it.alias.contains(busqueda, ignoreCase = true) ||
                                it.email.contains(busqueda, ignoreCase = true) ||
                                it.phone.contains(busqueda) ||
                                it.sucursal.contains(busqueda, ignoreCase = true)
                    }
                    if (encontrados.isEmpty()) {
                        bot("No se encontró ningún supervisor con \"$busqueda\".")
                    } else {
                        bot(
                            "Se encontró ${encontrados.size} supervisor(es):\n\n" +
                                    encontrados.joinToString("\n\n") { ficha(it) }
                        )
                    }
                },
                onFailure = { bot(it.message ?: "No pude buscar supervisores.") }
            )
        }

    // ---------- Chat: reparte según lo que pide el gerente ----------

    fun preguntar(texto: String, context: Context) {
        val pregunta = texto.trim()
        if (pregunta.isEmpty()) return
        val app = context.applicationContext
        val p = normalizar(pregunta)

        ejecutar("Krono está trabajando…", pregunta) {
            when {
                esExportar(p) -> exportar(p, app)
                "grafic" in p -> grafico(p)
                esVentas(p) -> responderVentas(pregunta, p)
                else -> gestor.procesarPregunta(pregunta)
                    .onSuccess { bot(it) }
                    .onFailure { bot("No pude responder en este momento. ${it.message}") }
            }
        }
    }

    // ---------- Datos ----------

    /** Supervisores visibles (equipo + sin gerente) con todas sus ventas. null si algo falló. */
    private suspend fun cargarVentas(): Pair<List<CuentaSupervisor>, List<VentaRegistrada>>? {
        val sups = GerenteRepository.supervisoresVisibles(gerenteId).getOrElse {
            bot(it.message ?: "No pude leer los supervisores.")
            return null
        }
        if (sups.isEmpty()) {
            bot("Todavía no hay supervisores registrados. Cuando alguien se registre con el rol Supervisor de Ventas aparecerá aquí.")
            return null
        }
        val ventas = VentasEquipoRepository.listarDe(sups.map { it.id }.toSet()).getOrElse {
            bot(it.message ?: "No pude leer las ventas de tus supervisores.")
            return null
        }
        return sups to ventas
    }

    private fun filtrar(ventas: List<VentaRegistrada>, periodo: Periodo): List<VentaRegistrada> =
        ventas.filter {
            val f = it.fechaHora
            (periodo.desde == null || f >= periodo.desde) && (periodo.hasta == null || f < periodo.hasta)
        }

    // ---------- Análisis con IA (las cifras las calcula la app) ----------

    private suspend fun responderVentas(pregunta: String, p: String) {
        val (sups, todas) = cargarVentas() ?: return
        val periodo = periodoDe(p)
        val ventas = filtrar(todas, periodo)
        if (ventas.isEmpty()) {
            bot(
                "No encontré ventas ${periodo.nombre} de tus supervisores. " +
                        "Prueba con «de este mes», «del mes pasado» o «de todo el historial»."
            )
            return
        }

        val datos = construirDatos(ventas, sups, periodo)
        val prompt = "Datos calculados por la app (no inventes ni cambies cifras):\n$datos\n\n" +
                "Pregunta del gerente: $pregunta\n\n" +
                "Responde en español, claro y breve (máximo 4 frases), usando solo esos datos."
        val redaccion = groq.enviar(emptyList(), prompt).getOrNull()
            ?.replace("**", "")?.trim()?.takeIf { it.isNotBlank() }

        val pista = "\n\n💡 Puedes pedirme: «exporta las ventas ${periodo.nombre} en Excel o PDF» " +
                "o «gráfico de ventas por supervisor»."
        bot(if (redaccion != null) "$redaccion\n\n— Detalle —\n$datos$pista" else "$datos$pista")
    }

    // ---------- Gráfico ----------

    private suspend fun grafico(p: String) {
        val (sups, todas) = cargarVentas() ?: return
        val periodo = periodoDe(p)
        val ventas = filtrar(todas, periodo)
        if (ventas.isEmpty()) {
            bot("No encontré ventas ${periodo.nombre} para graficar.")
            return
        }

        if ("producto" in p) {
            val datos = ventas.groupBy { it.producto }
                .map { (nombre, lista) -> nombre to lista.sumOf { it.cantidad } }
                .sortedByDescending { it.second }
                .take(6)
            bot("Unidades vendidas por producto ${periodo.nombre}:", grafico = datos)
        } else {
            val datos = sups
                .map { s ->
                    s.alias to Math.round(ventas.filter { it.usuarioId == s.id }.sumOf { it.total }).toInt()
                }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .take(6)
            bot("Ventas por supervisor ${periodo.nombre} (en dólares):", grafico = datos)
        }
    }

    // ---------- Exportar a Excel / PDF ----------

    private suspend fun exportar(p: String, app: Context) {
        val (sups, todas) = cargarVentas() ?: return
        val periodo = periodoDe(p)
        val ventas = filtrar(todas, periodo)
        if (ventas.isEmpty()) {
            bot("No encontré ventas ${periodo.nombre} de tus supervisores para exportar.")
            return
        }

        val formatos = buildSet {
            if ("excel" in p || "xlsx" in p) add(FormatoReporte.EXCEL)
            if ("pdf" in p) add(FormatoReporte.PDF)
            // Sin formato indicado (o «ambos»): se generan los dos
            if (isEmpty() || "ambos" in p || "los dos" in p) {
                add(FormatoReporte.EXCEL)
                add(FormatoReporte.PDF)
            }
        }

        try {
            val archivos = withContext(Dispatchers.IO) {
                buildList {
                    if (FormatoReporte.EXCEL in formatos) add(VentasExcel.generar(app, ventas, periodo.nombre))
                    if (FormatoReporte.PDF in formatos) {
                        add(VentasPdf.generar(app, ventas, periodo.nombre, alias, "KronoCorp"))
                    }
                }
            }
            val conVentas = ventas.map { it.usuarioId }.distinct().size
            bot(
                "Listo ✅ Exporté ${ventas.size} venta(s) ${periodo.nombre} de $conVentas de " +
                        "${sups.size} supervisor(es). Total: ${dinero(ventas.sumOf { it.total })}.",
                archivos = archivos
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            bot("No pude generar el archivo: ${e.message}")
        }
    }

    // ---------- Textos ----------

    private fun etiquetaSucursal(s: CuentaSupervisor) =
        if (s.sucursal.isBlank()) "sin sucursal" else "sucursal ${s.sucursal}"

    private fun ficha(s: CuentaSupervisor) = buildString {
        append("• ${s.alias} — ${etiquetaSucursal(s)}")
        append(if (s.gerenteId.isBlank()) " · sin gerente" else " · en tu equipo")
        append("\n  ${s.email}")
        if (s.phone.isNotBlank()) append("\n  ${s.phone}")
    }

    private fun textoSupervisores(lista: List<CuentaSupervisor>): String {
        if (lista.isEmpty()) return "Todavía no hay supervisores registrados."
        val equipo = lista.filter { it.gerenteId == gerenteId }
        val sinGerente = lista.filter { it.gerenteId.isBlank() }
        return buildString {
            if (equipo.isNotEmpty()) {
                append("En tu equipo (${equipo.size}):\n\n")
                append(equipo.joinToString("\n\n") { ficha(it) })
            }
            if (sinGerente.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Registrados sin gerente (${sinGerente.size}):\n\n")
                append(sinGerente.joinToString("\n\n") { ficha(it) })
                append("\n\nPara asignarlos a tu equipo entra a «Nuevo supervisor».")
            }
        }
    }

    private fun construirDatos(
        ventas: List<VentaRegistrada>,
        sups: List<CuentaSupervisor>,
        periodo: Periodo
    ): String = buildString {
        val total = ventas.sumOf { it.total }
        appendLine("Periodo: ${periodo.nombre}")
        appendLine("Supervisores: ${sups.size}")
        appendLine("Ventas registradas: ${ventas.size}")
        appendLine("Unidades vendidas: ${ventas.sumOf { it.cantidad }}")
        appendLine("Total vendido: ${dinero(total)}")
        if (ventas.isNotEmpty()) appendLine("Venta promedio: ${dinero(total / ventas.size)}")

        appendLine("Por supervisor (ventas · total):")
        sups.map { s ->
            val propias = ventas.filter { it.usuarioId == s.id }
            Triple(s, propias.size, propias.sumOf { it.total })
        }.sortedByDescending { it.third }.forEach { (s, cantidad, subtotal) ->
            appendLine("- ${s.alias} (${etiquetaSucursal(s)}): $cantidad ventas · ${dinero(subtotal)}")
        }

        val porProducto = ventas.groupBy { it.producto }
            .map { (n, l) -> Triple(n, l.sumOf { it.cantidad }, l.sumOf { it.total }) }
            .sortedByDescending { it.second }
        appendLine("Productos más vendidos (unidades · total):")
        porProducto.take(5).forEach { appendLine("- ${it.first}: ${it.second} · ${dinero(it.third)}") }

        val porSucursal = ventas.filter { it.sucursal.isNotBlank() }.groupBy { it.sucursal }
        if (porSucursal.size > 1) {
            appendLine("Por sucursal:")
            porSucursal.forEach { (s, l) -> appendLine("- $s: ${dinero(l.sumOf { it.total })}") }
        }

        val porPago = ventas.filter { it.metodoPago.isNotBlank() }.groupBy { it.metodoPago }
        if (porPago.size > 1) {
            appendLine("Por método de pago:")
            porPago.forEach { (m, l) -> appendLine("- $m: ${dinero(l.sumOf { it.total })}") }
        }
    }.trim()

    // ---------- Intenciones y periodos ----------

    private fun esExportar(p: String) =
        "exporta" in p || "excel" in p || "xlsx" in p || "pdf" in p || "descarga" in p ||
                ("reporte" in p && ("genera" in p || "crea" in p || "dame" in p))

    private fun esVentas(p: String) = listOf(
        "venta", "vend", "supervisor", "producto", "sucursal", "ingreso", "ganancia",
        "rendimiento", "desempeno", "equipo", "analiza", "analisis", "total"
    ).any { it in p }

    private fun palabra(p: String, w: String) = Regex("\\b$w\\b").containsMatchIn(p)

    private fun periodoDe(p: String): Periodo {
        val hoy = LocalDate.now()
        return when {
            palabra(p, "ayer") ->
                Periodo(hoy.minusDays(1).atStartOfDay(), hoy.atStartOfDay(), "de ayer")
            palabra(p, "hoy") ->
                Periodo(hoy.atStartOfDay(), hoy.plusDays(1).atStartOfDay(), "de hoy")
            "semana" in p ->
                Periodo(hoy.with(DayOfWeek.MONDAY).atStartOfDay(), null, "de esta semana")
            "mes pasado" in p -> {
                val inicioActual = hoy.withDayOfMonth(1)
                Periodo(inicioActual.minusMonths(1).atStartOfDay(), inicioActual.atStartOfDay(), "del mes pasado")
            }
            palabra(p, "todas") || palabra(p, "todo") || "historial" in p || "siempre" in p ->
                Periodo(null, null, "de todo el historial")
            palabra(p, "ano") ->
                Periodo(hoy.withDayOfYear(1).atStartOfDay(), null, "de este año")
            else ->
                Periodo(hoy.withDayOfMonth(1).atStartOfDay(), null, "de este mes")
        }
    }

    private fun normalizar(t: String): String =
        Normalizer.normalize(t, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase().trim()

    private fun dinero(v: Double) = "$" + String.format(Locale.US, "%,.2f", v)
}

// ---------------------------------------------------------------
// Pantalla
// ---------------------------------------------------------------
@Composable
fun AsistenteIAScreen(
    usuario: AppUser,
    vm: AsistenteViewModel = viewModel(key = usuario.id)
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var texto by rememberSaveable { mutableStateOf("") }
    var mostrarBuscar by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.iniciar(usuario) }

    // Baja al último mensaje cuando llega uno nuevo
    val totalItems = vm.mensajes.size + if (vm.ocupado != null) 1 else 0
    LaunchedEffect(totalItems) {
        if (totalItems > 0) listState.animateScrollToItem(totalItems - 1)
    }

    val libre = vm.ocupado == null

    fun enviar() {
        if (texto.isBlank() || !libre) return
        vm.preguntar(texto, context)
        texto = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Fondo)
            .imePadding()
    ) {
        Text(
            text = "Asistente",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = TextoOscuro,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)
        )
        Text(
            text = "Krono · supervisores, ventas, reportes y preguntas libres",
            fontSize = 12.sp,
            color = Gris,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
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
                BurbujaChat(
                    mensaje = mensaje,
                    alAbrir = { abrirArchivo(context, it) },
                    alCompartir = { compartirArchivo(context, it) }
                )
            }
            vm.ocupado?.let { estado ->
                item { BurbujaEstado(estado) }
            }
        }

        // Acciones rápidas
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 6.dp)
        ) {
            item {
                ChipAccion("Ver supervisores", Icons.Default.Groups, libre) { vm.consultarSupervisores() }
            }
            item {
                ChipAccion("Buscar supervisor", Icons.Default.Search, libre) { mostrarBuscar = true }
            }
            item {
                ChipAccion("Análisis del mes", Icons.Default.Assessment, libre) {
                    vm.preguntar("Analiza las ventas de este mes", context)
                }
            }
            item {
                ChipAccion("Gráfico", Icons.Default.BarChart, libre) {
                    vm.preguntar("Gráfico de ventas por supervisor de este mes", context)
                }
            }
            item {
                ChipAccion("Excel", Icons.Default.TableChart, libre) {
                    vm.preguntar("Exporta las ventas de este mes en Excel", context)
                }
            }
            item {
                ChipAccion("PDF", Icons.Default.PictureAsPdf, libre) {
                    vm.preguntar("Exporta las ventas de este mes en PDF", context)
                }
            }
        }

        // Barra para escribir
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Escríbele a Krono…", fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { enviar() }),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Fondo,
                    focusedContainerColor = Color.White,
                    unfocusedBorderColor = BordeClaro,
                    focusedBorderColor = Azul
                )
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { enviar() },
                enabled = libre && texto.isNotBlank(),
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (libre && texto.isNotBlank()) Azul else BordeClaro)
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

    if (mostrarBuscar) {
        DialogoBuscar(
            alCerrar = { mostrarBuscar = false },
            alBuscar = {
                mostrarBuscar = false
                vm.buscarSupervisor(it)
            }
        )
    }
}

// ---------------------------------------------------------------
// Componentes
// ---------------------------------------------------------------
@Composable
private fun ChipAccion(
    titulo: String,
    icono: ImageVector,
    habilitado: Boolean,
    alPulsar: () -> Unit
) {
    AssistChip(
        onClick = alPulsar,
        enabled = habilitado,
        label = { Text(titulo, fontSize = 12.sp) },
        leadingIcon = {
            Icon(icono, contentDescription = null, tint = Azul, modifier = Modifier.size(16.dp))
        },
        shape = RoundedCornerShape(12.dp),
        colors = AssistChipDefaults.assistChipColors(containerColor = Color.White)
    )
}

@Composable
private fun BurbujaChat(
    mensaje: MensajeChat,
    alAbrir: (File) -> Unit,
    alCompartir: (File) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mensaje.esUsuario) Arrangement.End else Arrangement.Start
    ) {
        Column(modifier = Modifier.widthIn(max = 300.dp)) {
            if (!mensaje.esUsuario) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                ) {
                    Icon(Icons.Default.SmartToy, contentDescription = null, tint = Azul, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Krono", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Gris)
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
                    .background(if (mensaje.esUsuario) Azul else Color.White)
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = if (mensaje.esUsuario) mensaje.texto else limpiarMarkdown(mensaje.texto),
                        fontSize = 13.sp,
                        color = if (mensaje.esUsuario) Color.White else TextoOscuro
                    )
                    mensaje.grafico?.let { datos ->
                        Spacer(Modifier.size(10.dp))
                        MiniBarChart(datos = datos, altura = 110)
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

@Composable
private fun BurbujaEstado(estado: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(estado, fontSize = 12.sp, color = Gris)
        }
    }
}

/** La IA responde con Markdown; aquí solo se quitan los símbolos más comunes. */
private fun limpiarMarkdown(texto: String): String =
    texto.replace("**", "")
        .replace("__", "")
        .replace("`", "")
        .lines()
        .joinToString("\n") { it.trimStart('#').trimStart() }

@Composable
private fun DialogoBuscar(alCerrar: () -> Unit, alBuscar: (String) -> Unit) {
    var consulta by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Buscar supervisor") },
        text = {
            OutlinedTextField(
                value = consulta,
                onValueChange = { consulta = it },
                label = { Text("Nombre, correo, teléfono o sucursal") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                enabled = consulta.isNotBlank(),
                onClick = { alBuscar(consulta.trim()) }
            ) { Text("Buscar") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } }
    )
}