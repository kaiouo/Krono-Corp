package com.example.krono_corp.negocio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.krono_corp.data.NegocioRepository
import com.example.krono_corp.data.ProductoInventarioRemoto
import com.example.krono_corp.data.VentaRemota
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// ==============================================================
// Modulo de Persona 4: Asistente (ChatBot)
// Las respuestas con numero/boton usan los datos reales de ventas e inventario que se
// registran en la pantalla "Ventas e inventario" (coleccion "ventas" e "inventario" en
// Appwrite). Las preguntas libres todavia son simuladas por palabra clave: aqui se
// conectara una IA real mas adelante.
// ==============================================================

private const val STOCK_BAJO = 5
private val formatoFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
private val MESES_ES = listOf(
    "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
    "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
)

// La fecha se guarda como "dd/MM/yyyy HH:mm"; de ahi se saca el mes para agrupar ventas.
private fun mesDeFecha(fecha: String): String? =
    fecha.split("/").getOrNull(1)?.toIntOrNull()?.let { MESES_ES.getOrNull(it - 1) }

// La coleccion "graficos" guarda los datos como texto plano (no hay un tipo JSON anidado):
// "Camisa:12,Pantalón:7". Lo usan tanto el Asistente (al generar) como el Historial (al leer).
fun codificarDatosGrafico(datos: List<Pair<String, Int>>): String =
    datos.joinToString(",") { (etiqueta, valor) -> "${etiqueta.replace(":", "").replace(",", "")}:$valor" }

fun decodificarDatosGrafico(texto: String): List<Pair<String, Int>> =
    texto.split(",").mapNotNull { parte ->
        val (etiqueta, valor) = parte.split(":").let { it.getOrNull(0) to it.getOrNull(1) }
        val numero = valor?.toIntOrNull()
        if (etiqueta.isNullOrBlank() || numero == null) null else etiqueta to numero
    }

private data class MensajeChat(
    val texto: String,
    val esUsuario: Boolean,
    val grafico: List<Pair<String, Int>>? = null
)

private fun formatoDinero(valor: Double) = "$${"%.2f".format(valor)}"

private fun sinAcentos(texto: String): String =
    Normalizer.normalize(texto, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

private fun textoResumenVentas(ventas: List<VentaRemota>): String {
    if (ventas.isEmpty()) return "Aún no se ha registrado ninguna venta. Ve a \"Ventas e inventario\" para registrar la primera."
    return buildString {
        appendLine("Resumen de ventas:")
        ventas.groupBy { it.producto }.forEach { (producto, lista) ->
            appendLine("• $producto: ${lista.sumOf { it.cantidad }} unidades — ${formatoDinero(lista.sumOf { it.total })}")
        }
        append("Total vendido: ${formatoDinero(ventas.sumOf { it.total })}")
    }
}

private fun textoProductoMasVendido(ventas: List<VentaRemota>): String {
    val top = ventas.groupBy { it.producto }
        .mapValues { it.value.sumOf { v -> v.cantidad } }
        .maxByOrNull { it.value }
        ?: return "Aún no hay ventas registradas."
    return "El producto más vendido es ${top.key}, con ${top.value} unidades."
}

private fun textoInventario(inventario: List<ProductoInventarioRemoto>): String {
    if (inventario.isEmpty()) return "Aún no has registrado productos en el inventario."
    return buildString {
        appendLine("Inventario:")
        inventario.forEach { appendLine("• ${it.nombre}: ${it.stock} unidades") }
    }.trim()
}

private fun textoPocoStock(inventario: List<ProductoInventarioRemoto>): String {
    if (inventario.isEmpty()) return "Aún no has registrado productos en el inventario."
    val bajos = inventario.filter { it.stock <= STOCK_BAJO }
    if (bajos.isEmpty()) return "Todos los productos tienen stock suficiente."
    return "Productos con poco stock ($STOCK_BAJO o menos):\n" +
        bajos.joinToString("\n") { "• ${it.nombre}: quedan ${it.stock} unidades" }
}

private fun textoMeses(ventas: List<VentaRemota>): String {
    val porMes = ventas.mapNotNull { v -> mesDeFecha(v.fecha)?.let { it to v.total } }
        .groupBy({ it.first }, { it.second })
        .mapValues { it.value.sum() }
    val mejor = porMes.maxByOrNull { it.value } ?: return "Aún no hay suficientes ventas registradas para calcular los meses."
    val lista = porMes.entries.sortedByDescending { it.value }.joinToString("\n") { "• ${it.key}: ${formatoDinero(it.value)}" }
    return "Meses con mayores ventas:\n$lista\n\nEl mejor mes fue ${mejor.key}."
}

// Respuesta simulada para una pregunta libre o configurada por el negocio, construida con
// los datos reales de ventas e inventario. Aqui se conectara una IA real mas adelante.
private fun responderPregunta(pregunta: String, ventas: List<VentaRemota>, inventario: List<ProductoInventarioRemoto>): String {
    val p = sinAcentos(pregunta)
    return when {
        Regex("\\bmes(es)?\\b").containsMatchIn(p) -> textoMeses(ventas)
        "poco" in p && "stock" in p -> textoPocoStock(inventario)
        "vendido" in p || "producto" in p -> textoProductoMasVendido(ventas)
        "venta" in p -> textoResumenVentas(ventas)
        "inventario" in p || "stock" in p -> textoInventario(inventario)
        "reporte" in p -> "Puedo generar un reporte de ventas. Toca el botón \"Generar reporte\" aquí abajo."
        "grafico" in p -> "Puedo generar un gráfico de ventas. Toca el botón \"Generar gráfico\" aquí abajo."
        else -> "Soy Krono. Todavía no puedo responder eso libremente, pero sí puedo mostrarte los datos reales de tu negocio con las opciones de abajo."
    }
}

@Composable
fun AsistenteScreen(
    navController: NavController,
    negocioData: NegocioData
) {
    val mensajes = remember {
        mutableStateListOf(
            MensajeChat(
                texto = "Hola, soy Krono, el asistente de ${negocioData.nombreNegocio}.\n\n" +
                    "¿Qué te gustaría hacer hoy? Toca una opción, o escríbeme tu pregunta.",
                esUsuario = false
            )
        )
    }
    var textoEntrada by remember { mutableStateOf("") }
    var ventas by remember { mutableStateOf<List<VentaRemota>>(emptyList()) }
    var inventario by remember { mutableStateOf<List<ProductoInventarioRemoto>>(emptyList()) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Carga las ventas y el inventario reales del negocio para que el Asistente responda con ellos
    LaunchedEffect(negocioData.id) {
        if (negocioData.id.isEmpty()) return@LaunchedEffect
        NegocioRepository.listarVentas(negocioData.id).onSuccess { ventas = it }
        NegocioRepository.listarInventario(negocioData.id).onSuccess { inventario = it }
    }

    fun enviarUsuario(texto: String) {
        if (texto.isBlank()) return
        mensajes.add(MensajeChat(texto.trim(), esUsuario = true))
        mensajes.add(MensajeChat(responderPregunta(texto, ventas, inventario), esUsuario = false))
        scope.launch { listState.animateScrollToItem(mensajes.lastIndex) }
    }

    fun agregarRespuestaBot(texto: String, grafico: List<Pair<String, Int>>? = null) {
        mensajes.add(MensajeChat(texto, esUsuario = false, grafico = grafico))
        scope.launch { listState.animateScrollToItem(mensajes.lastIndex) }
    }

    Scaffold(
        containerColor = KronoBackground,
        bottomBar = {
            KronoBottomBar(navController = navController, currentRoute = Screen.Asistente.route)
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            KronoTopAvatarBar(
                letraAvatar = "K",
                onBackClick = {
                    if (!navController.popBackStack()) navController.navigate(Screen.Home.route)
                },
                onGearClick = { navController.navigate(Screen.Configuracion.route) }
            )

            Text(
                text = "Asistente",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = KronoTextDark,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Text(
                text = "Analiza las ventas y el inventario reales de tu negocio",
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
                items(mensajes) { mensaje -> BurbujaChat(mensaje) }
            }

            // Preguntas que el dueño configuró para este negocio (si hay)
            if (negocioData.preguntasIA.isNotEmpty()) {
                Text(
                    text = "Preguntas de tu negocio",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = KronoTextMuted,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    negocioData.preguntasIA.take(2).forEach { pregunta ->
                        AssistChip(
                            onClick = { enviarUsuario(pregunta) },
                            label = {
                                Text(
                                    pregunta,
                                    fontSize = 11.sp,
                                    maxLines = 2
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            // Menu de opciones rapidas (igual que el prototipo de consola, ahora como botones reales)
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                FilaAccionesRapidas(
                    izquierda = "Resumen de ventas" to Icons.Outlined.ShowChart,
                    derecha = "Producto más vendido" to Icons.Outlined.Inventory2,
                    alPulsarIzquierda = { enviarUsuario("Resumen de ventas") },
                    alPulsarDerecha = { enviarUsuario("Producto más vendido") }
                )
                Spacer(Modifier.height(8.dp))
                FilaAccionesRapidas(
                    izquierda = "Inventario" to Icons.Outlined.Inventory2,
                    derecha = "Poco stock" to Icons.Outlined.Inventory2,
                    alPulsarIzquierda = { enviarUsuario("Inventario") },
                    alPulsarDerecha = { enviarUsuario("Poco stock") }
                )
                Spacer(Modifier.height(8.dp))
                FilaAccionesRapidas(
                    izquierda = "Generar reporte" to Icons.Outlined.Description,
                    derecha = "Generar gráfico" to Icons.Outlined.BarChart,
                    alPulsarIzquierda = {
                        if (ventas.isEmpty()) {
                            agregarRespuestaBot("Aún no hay ventas registradas para generar un reporte. Ve a \"Ventas e inventario\" primero.")
                        } else {
                            val contenido = textoResumenVentas(ventas)
                            val fecha = LocalDateTime.now().format(formatoFecha)
                            scope.launch {
                                NegocioRepository.crearReporte(
                                    scope = "negocio",
                                    ownerId = negocioData.ownerId,
                                    titulo = "Reporte de ventas",
                                    fecha = fecha,
                                    contenido = contenido
                                ).onSuccess { remoto ->
                                    negocioData.historialReportes.add(
                                        0,
                                        ReporteGenerado(remoto.id, remoto.titulo, remoto.fecha, remoto.contenido)
                                    )
                                    agregarRespuestaBot("Listo, generé el reporte y lo guardé en el historial de reportes.\n\n$contenido")
                                }.onFailure { e ->
                                    agregarRespuestaBot("No pude guardar el reporte: ${e.message}")
                                }
                            }
                        }
                    },
                    alPulsarDerecha = {
                        val datos = ventas.groupBy { it.producto }.map { (p, lista) -> p to lista.sumOf { it.cantidad } }
                        if (datos.isEmpty()) {
                            agregarRespuestaBot("Aún no hay ventas registradas para generar un gráfico. Ve a \"Ventas e inventario\" primero.")
                        } else {
                            val fecha = LocalDateTime.now().format(formatoFecha)
                            scope.launch {
                                NegocioRepository.crearGrafico(
                                    scope = "negocio",
                                    ownerId = negocioData.ownerId,
                                    titulo = "Gráfico de ventas por producto",
                                    fecha = fecha,
                                    datosCodificados = codificarDatosGrafico(datos)
                                ).onSuccess { remoto ->
                                    negocioData.historialGraficos.add(
                                        0,
                                        GraficoGenerado(remoto.id, remoto.titulo, remoto.fecha, datos)
                                    )
                                    agregarRespuestaBot(
                                        "Listo, generé el gráfico de unidades vendidas por producto y lo guardé en el historial de gráficos.",
                                        grafico = datos
                                    )
                                }.onFailure { e ->
                                    agregarRespuestaBot("No pude guardar el gráfico: ${e.message}")
                                }
                            }
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { navController.navigate(Screen.HistorialReportes.route) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Ver reportes", fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = { navController.navigate(Screen.HistorialGraficos.route) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Ver gráficos", fontSize = 12.sp) }
                }
            }

            // Barra para escribir una pregunta libre
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textoEntrada,
                    onValueChange = { textoEntrada = it },
                    placeholder = { Text("Escríbele a Krono…", fontSize = 13.sp) },
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = KronoSurfaceVariant,
                        focusedContainerColor = Color.White,
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = KronoBlue
                    ),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        enviarUsuario(textoEntrada)
                        textoEntrada = ""
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(KronoBlue)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
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
private fun FilaAccionesRapidas(
    izquierda: Pair<String, ImageVector>,
    derecha: Pair<String, ImageVector>,
    alPulsarIzquierda: () -> Unit,
    alPulsarDerecha: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BotonAccion(izquierda.first, izquierda.second, Modifier.weight(1f), alPulsarIzquierda)
        BotonAccion(derecha.first, derecha.second, Modifier.weight(1f), alPulsarDerecha)
    }
}

@Composable
private fun BotonAccion(
    titulo: String,
    icono: ImageVector,
    modifier: Modifier = Modifier,
    alPulsar: () -> Unit
) {
    OutlinedButton(
        onClick = alPulsar,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Icon(icono, contentDescription = null, tint = KronoBlue, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(titulo, fontSize = 11.sp, color = KronoTextDark, maxLines = 1)
    }
}

@Composable
private fun BurbujaChat(mensaje: MensajeChat) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mensaje.esUsuario) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            if (!mensaje.esUsuario) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp, start = 4.dp)) {
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
                    if (mensaje.grafico != null) {
                        Spacer(Modifier.height(10.dp))
                        MiniBarChart(datos = mensaje.grafico)
                    }
                }
            }
        }
    }
}

// Grafico de barras real, reutilizado tambien en el historial de graficos.
@Composable
fun MiniBarChart(
    datos: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
    altura: Int = 90
) {
    val maximo = (datos.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height((altura + 36).dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        datos.forEach { (etiqueta, valor) ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(valor.toString(), fontSize = 10.sp, color = KronoTextMuted)
                Spacer(Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height((altura * valor / maximo).coerceAtLeast(4).dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(KronoBlue)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    etiqueta,
                    fontSize = 10.sp,
                    color = KronoTextDark,
                    maxLines = 1
                )
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun AsistenteScreenPreview() {
    MaterialTheme {
        AsistenteScreen(
            navController = rememberNavController(),
            negocioData = NegocioData()
        )
    }
}
