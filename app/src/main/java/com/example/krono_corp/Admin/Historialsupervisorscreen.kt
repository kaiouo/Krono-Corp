package com.example.krono_corp.Admin

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.krono_corp.data.AppUser
import com.example.krono_corp.data.ReporteRemoto
import com.example.krono_corp.data.ReportesRepository
import com.example.krono_corp.negocio.MiniBarChart
import com.example.krono_corp.reportes.abrirDescarga
import com.example.krono_corp.reportes.compartirDescarga
import com.example.krono_corp.reportes.guardarDescarga
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

enum class TipoHistorial { REPORTES, GRAFICOS }

private fun esExcel(nombre: String) =
    nombre.endsWith(".xlsx", ignoreCase = true) || nombre.endsWith(".xls", ignoreCase = true)

private fun esPdf(nombre: String) = nombre.endsWith(".pdf", ignoreCase = true)

private fun etiquetaFormato(r: ReporteRemoto): String {
    if (r.tipo == ReportesRepository.TIPO_GRAFICO) return "GRÁFICO"
    val excel = r.archivoNombres.any { esExcel(it) }
    val pdf = r.archivoNombres.any { esPdf(it) }
    return when {
        excel && pdf -> "EXCEL + PDF"
        excel -> "EXCEL"
        pdf -> "PDF"
        else -> "REPORTE"
    }
}

/** "13/11/2026 10:30" -> "Noviembre 2026" (para agrupar el historial por mes). */
private fun mesDe(fecha: String): String {
    val m = Regex("^(\\d{2})/(\\d{2})/(\\d{4})").find(fecha) ?: return "Sin fecha"
    val mes = m.groupValues[2].toIntOrNull()?.coerceIn(1, 12) ?: return "Sin fecha"
    val nombre = Month.of(mes).getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-SV"))
    return nombre.replaceFirstChar { it.uppercase() } + " " + m.groupValues[3]
}

/**
 * Historial PERSONAL del supervisor: solo lo que él generó con la IA.
 *  - REPORTES: reportes de texto y los Excel/PDF exportados (se pueden volver a descargar)
 *  - GRAFICOS: gráficos generados
 * Ordenado del más reciente al más antiguo y agrupado por mes.
 */
@Composable
fun HistorialSupervisorScreen(usuario: AppUser, tipo: TipoHistorial) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var todos by remember { mutableStateOf<List<ReporteRemoto>>(emptyList()) }
    var busqueda by remember { mutableStateOf("") }
    var filtro by remember { mutableStateOf("Todos") }
    var seleccionado by remember { mutableStateOf<ReporteRemoto?>(null) }
    var descargando by remember { mutableStateOf<String?>(null) }
    var recarga by remember { mutableStateOf(0) }

    LaunchedEffect(recarga) {
        cargando = true
        error = null
        ReportesRepository.listarDe(usuario.id)
            .onSuccess { todos = it }
            .onFailure { error = it.message }
        cargando = false
    }

    fun bajarArchivo(archivoId: String, nombre: String, compartir: Boolean) {
        if (descargando != null) return
        scope.launch {
            descargando = nombre
            error = null
            ReportesRepository.descargar(archivoId)
                .onSuccess { bytes ->
                    try {
                        val guardado = withContext(Dispatchers.IO) {
                            guardarDescarga(context, nombre, bytes)
                        }
                        if (compartir) {
                            compartirDescarga(context, guardado)
                        } else {
                            if (guardado.uri != null) {
                                Toast.makeText(
                                    context,
                                    "Guardado en Descargas/KronoCorp",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            abrirDescarga(context, guardado)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = e.message ?: "No se pudo guardar el archivo."
                    }
                }
                .onFailure { error = it.message }
            descargando = null
        }
    }

    val esGraficos = tipo == TipoHistorial.GRAFICOS
    val tipoBuscado = if (esGraficos) ReportesRepository.TIPO_GRAFICO else ReportesRepository.TIPO_REPORTE
    val delTipo = todos.filter { it.tipo == tipoBuscado }
    val filtrados = delTipo.filter { r ->
        (busqueda.isBlank() || r.titulo.contains(busqueda, ignoreCase = true)) &&
                when (filtro) {
                    "Excel" -> r.archivoNombres.any { esExcel(it) }
                    "PDF" -> r.archivoNombres.any { esPdf(it) }
                    else -> true
                }
    }
    // La lista ya viene del más reciente al más antiguo; groupBy conserva ese orden
    val porMes = filtrados.groupBy { mesDe(it.fecha) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (esGraficos) "Historial de gráficos" else "Historial de reportes",
                    fontSize = 24.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = AdminTexto,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { recarga++ }, enabled = !cargando) { Text("Actualizar") }
            }
            Text(
                if (esGraficos) "Los gráficos que generaste con el asistente"
                else "Los reportes, Excel y PDF que generaste con el asistente",
                fontSize = 13.sp,
                color = AdminGris
            )
        }

        if (cargando) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        descargando?.let { nombre ->
            item { Text("Descargando $nombre…", fontSize = 12.sp, color = AdminGris) }
        }
        error?.let { mensaje -> item { Text(mensaje, color = AdminRojo, fontSize = 13.sp) } }

        if (delTipo.isNotEmpty()) {
            item {
                OutlinedTextField(
                    value = busqueda,
                    onValueChange = { busqueda = it },
                    label = { Text("Buscar por título") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (!esGraficos) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("Todos", "Excel", "PDF")) { opcion ->
                            FilterChip(
                                selected = filtro == opcion,
                                onClick = { filtro = opcion },
                                label = { Text(opcion) }
                            )
                        }
                    }
                }
            }
            item {
                Text("${filtrados.size} de ${delTipo.size} mostrados", fontSize = 12.sp, color = AdminGris)
            }
        }

        if (!cargando && error == null && delTipo.isEmpty()) {
            item {
                TextoVacio(
                    if (esGraficos)
                        "Todavía no has generado gráficos. Ve a «Asistente IA» y toca «Generar gráfico»."
                    else
                        "Todavía no has generado reportes. Ve a «Asistente IA» y toca «Generar reporte», " +
                                "o pídele que exporte tus ventas a Excel o PDF."
                )
            }
        } else if (!cargando && delTipo.isNotEmpty() && filtrados.isEmpty()) {
            item { TextoVacio("No hay resultados con ese filtro.") }
        }

        porMes.forEach { (mes, lista) ->
            item(key = "mes-$mes") {
                Text(mes, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AdminGris)
            }
            items(lista, key = { it.id }) { r ->
                TarjetaBlanca {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(etiquetaFormato(r), fontSize = 11.sp, color = AdminGris)
                        Etiqueta(
                            r.estado,
                            if (r.estado == ReportesRepository.ESTADO_REVISADO) AdminVerde else AdminNaranja
                        )
                    }
                    Text(r.titulo, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AdminTexto)
                    Text("Generado: ${r.fecha}", fontSize = 12.sp, color = AdminGris)
                    if (r.tipo == ReportesRepository.TIPO_GRAFICO) {
                        val datos = ReportesRepository.decodificarDatos(r.contenido)
                        if (datos.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            MiniBarChart(datos = datos, altura = 70)
                        }
                    } else if (r.archivoIds.isNotEmpty()) {
                        Text(
                            "${r.archivoIds.size} archivo(s) disponible(s) para descargar",
                            fontSize = 12.sp,
                            color = AdminAzul
                        )
                    }
                    TextButton(onClick = { seleccionado = r }) { Text("Ver detalles") }
                }
            }
        }
    }

    seleccionado?.let { r ->
        AlertDialog(
            onDismissRequest = { seleccionado = null },
            title = { Text(r.titulo, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Generado: ${r.fecha}", fontSize = 13.sp, color = AdminGris)
                    Text(
                        "Estado: ${r.estado}" +
                                if (r.estado == ReportesRepository.ESTADO_REVISADO) " por tu gerente" else "",
                        fontSize = 13.sp,
                        color = AdminGris
                    )
                    Spacer(Modifier.height(10.dp))
                    if (r.tipo == ReportesRepository.TIPO_GRAFICO) {
                        val datos = ReportesRepository.decodificarDatos(r.contenido)
                        MiniBarChart(datos = datos, altura = 110)
                        Spacer(Modifier.height(10.dp))
                        datos.forEach { (etiqueta, valor) -> Text("• $etiqueta: $valor", fontSize = 13.sp) }
                    } else {
                        Text(r.contenido, fontSize = 13.sp)
                        if (r.archivoIds.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text("Archivos", fontSize = 12.sp, color = AdminGris)
                            r.archivoIds.forEachIndexed { i, id ->
                                val nombre = r.archivoNombres.getOrElse(i) { "reporte_${i + 1}" }
                                Text(nombre, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Row {
                                    TextButton(
                                        enabled = descargando == null,
                                        onClick = { bajarArchivo(id, nombre, compartir = false) }
                                    ) { Text(if (descargando == nombre) "Descargando…" else "Descargar y abrir") }
                                    TextButton(
                                        enabled = descargando == null,
                                        onClick = { bajarArchivo(id, nombre, compartir = true) }
                                    ) { Text("Compartir") }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { seleccionado = null }) { Text("Cerrar") } }
        )
    }
}