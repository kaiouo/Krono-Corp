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
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// ==============================================================
// Modulo de Persona 4: Asistente (ChatBot)
// Version beta: las respuestas son simuladas y usan datos de ejemplo,
// todavia no se conecta a ninguna IA real.
// ==============================================================

private data class VentaEjemplo(val producto: String, val cantidad: Int, val total: Double)
private data class ProductoEjemplo(val nombre: String, val stock: Int)
private data class MesEjemplo(val mes: String, val total: Double)

private val ventasEjemplo = listOf(
    VentaEjemplo("Camisa", 12, 180.00),
    VentaEjemplo("Pantalón", 7, 175.00),
    VentaEjemplo("Zapatos", 4, 200.00),
    VentaEjemplo("Gorra", 15, 90.00)
)

private val inventarioEjemplo = listOf(
    ProductoEjemplo("Camisa", 40),
    ProductoEjemplo("Pantalón", 25),
    ProductoEjemplo("Zapatos", 3),
    ProductoEjemplo("Gorra", 60)
)

private val mesesEjemplo = listOf(
    MesEjemplo("Enero", 1200.00),
    MesEjemplo("Febrero", 950.00),
    MesEjemplo("Marzo", 1580.00),
    MesEjemplo("Abril", 1100.00)
)

private const val STOCK_BAJO = 5
private val formatoFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

private data class MensajeChat(
    val texto: String,
    val esUsuario: Boolean,
    val grafico: List<Pair<String, Int>>? = null
)

private fun formatoDinero(valor: Double) = "$${"%.2f".format(valor)}"

private fun sinAcentos(texto: String): String =
    Normalizer.normalize(texto, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

private fun textoResumenVentas(): String = buildString {
    appendLine("Resumen de ventas (datos de ejemplo):")
    ventasEjemplo.forEach { appendLine("• ${it.producto}: ${it.cantidad} unidades — ${formatoDinero(it.total)}") }
    append("Total vendido: ${formatoDinero(ventasEjemplo.sumOf { it.total })}")
}

private fun textoProductoMasVendido(): String {
    val top = ventasEjemplo.maxBy { it.cantidad }
    return "El producto más vendido es ${top.producto}, con ${top.cantidad} unidades."
}

private fun textoInventario(): String = buildString {
    appendLine("Inventario (datos de ejemplo):")
    inventarioEjemplo.forEach { appendLine("• ${it.nombre}: ${it.stock} unidades") }
}.trim()

private fun textoPocoStock(): String {
    val bajos = inventarioEjemplo.filter { it.stock <= STOCK_BAJO }
    if (bajos.isEmpty()) return "Todos los productos tienen stock suficiente."
    return "Productos con poco stock ($STOCK_BAJO o menos):\n" +
        bajos.joinToString("\n") { "• ${it.nombre}: quedan ${it.stock} unidades" }
}

private fun textoMeses(): String {
    val mejor = mesesEjemplo.maxBy { it.total }
    val lista = mesesEjemplo.sortedByDescending { it.total }
        .joinToString("\n") { "• ${it.mes}: ${formatoDinero(it.total)}" }
    return "Meses con mayores ventas (datos de ejemplo):\n$lista\n\nEl mejor mes fue ${mejor.mes}."
}

// Respuesta simulada para una pregunta libre o configurada por el negocio.
// Aqui se conectara la IA mas adelante.
private fun responderPregunta(pregunta: String): String {
    val p = sinAcentos(pregunta)
    return when {
        Regex("\\bmes(es)?\\b").containsMatchIn(p) -> textoMeses()
        "poco" in p && "stock" in p -> textoPocoStock()
        "vendido" in p || "producto" in p -> textoProductoMasVendido()
        "venta" in p -> textoResumenVentas()
        "inventario" in p || "stock" in p -> textoInventario()
        "reporte" in p -> "Puedo generar un reporte de ventas. Toca el botón \"Generar reporte\" aquí abajo."
        "grafico" in p -> "Puedo generar un gráfico de ventas. Toca el botón \"Generar gráfico\" aquí abajo."
        else -> "Soy Krono (beta). Todavía no puedo responder eso libremente, pero pronto podré analizar los datos reales del negocio. Por ahora prueba con una de las opciones de abajo."
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
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun enviarUsuario(texto: String) {
        if (texto.isBlank()) return
        mensajes.add(MensajeChat(texto.trim(), esUsuario = true))
        mensajes.add(MensajeChat(responderPregunta(texto), esUsuario = false))
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
                text = "Versión beta — respuestas simuladas con datos de ejemplo",
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
                        val contenido = textoResumenVentas()
                        val nuevo = ReporteGenerado(
                            id = (negocioData.historialReportes.maxOfOrNull { it.id } ?: 0) + 1,
                            titulo = "Reporte de ventas",
                            fecha = LocalDateTime.now().format(formatoFecha),
                            contenido = contenido
                        )
                        negocioData.historialReportes.add(0, nuevo)
                        agregarRespuestaBot("Listo, generé el reporte y lo guardé en el historial de reportes.\n\n$contenido")
                    },
                    alPulsarDerecha = {
                        val datos = ventasEjemplo.map { it.producto to it.cantidad }
                        val nuevo = GraficoGenerado(
                            id = (negocioData.historialGraficos.maxOfOrNull { it.id } ?: 0) + 1,
                            titulo = "Gráfico de ventas por producto",
                            fecha = LocalDateTime.now().format(formatoFecha),
                            datos = datos
                        )
                        negocioData.historialGraficos.add(0, nuevo)
                        agregarRespuestaBot(
                            "Listo, generé el gráfico de unidades vendidas por producto y lo guardé en el historial de gráficos.",
                            grafico = datos
                        )
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
