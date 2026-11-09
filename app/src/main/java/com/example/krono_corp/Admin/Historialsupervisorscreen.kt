package com.example.krono_corp.Admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import com.example.krono_corp.data.graficoMostrado
import com.example.krono_corp.negocio.GraficoReporteView
import com.example.krono_corp.reportes.abrirDescarga
import com.example.krono_corp.reportes.compartirDescarga
import com.example.krono_corp.reportes.guardarDescarga
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Historial de reportes del SUPERVISOR (solo los suyos). Cada reporte puede traer sus archivos
 * (PDF/Excel, que se bajan del bucket de Appwrite) y su gráfico de barras o de pastel.
 * Reemplaza a las dos pantallas anteriores (historial de reportes e historial de gráficos).
 */
@Composable
fun HistorialSupervisorScreen(
    perfil: AppUser,
    // Cambia cuando el asistente termina de guardar un reporte nuevo: vuelve a leer la lista
    version: Int = 0
) {
    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reportes by remember { mutableStateOf<List<ReporteRemoto>>(emptyList()) }
    var busqueda by remember { mutableStateOf("") }
    var seleccionado by remember { mutableStateOf<ReporteRemoto?>(null) }
    var recarga by remember { mutableStateOf(0) }
    var descargando by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun bajarArchivo(archivoId: String, nombre: String, compartir: Boolean) {
        if (descargando != null) return
        scope.launch {
            descargando = nombre
            error = null
            ReportesRepository.descargar(archivoId)
                .onSuccess { bytes ->
                    try {
                        val guardado = withContext(Dispatchers.IO) { guardarDescarga(context, nombre, bytes) }
                        if (compartir) compartirDescarga(context, guardado) else abrirDescarga(context, guardado)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = e.message ?: "No se pudo guardar el archivo."
                    }
                }
                .onFailure { error = it.message ?: "No se pudo descargar el archivo." }
            descargando = null
        }
    }

    LaunchedEffect(recarga, version) {
        cargando = true
        error = null
        ReportesRepository.listarDe(perfil.id)
            .onSuccess { reportes = it }
            .onFailure { error = it.message }
        cargando = false
    }

    val filtrados = reportes.filter { busqueda.isBlank() || it.titulo.contains(busqueda, ignoreCase = true) }

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
                    "Historial de reportes",
                    fontSize = 24.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = AdminTexto,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { recarga++ }, enabled = !cargando) { Text("Actualizar") }
            }
        }

        if (cargando) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        error?.let { mensaje -> item { Text(mensaje, color = AdminRojo, fontSize = 13.sp) } }

        if (reportes.isNotEmpty()) {
            item {
                OutlinedTextField(
                    value = busqueda,
                    onValueChange = { busqueda = it },
                    label = { Text("Buscar por título") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (!cargando && error == null && reportes.isEmpty()) {
            item { TextoVacio("Aún no has generado ningún reporte. Pídele uno al Asistente IA.") }
        } else if (!cargando && reportes.isNotEmpty() && filtrados.isEmpty()) {
            item { TextoVacio("No se encontró ningún reporte con ese título.") }
        }

        items(filtrados, key = { it.id }) { r ->
            val grafico = r.graficoMostrado()
            val etiquetaTipo = when {
                r.tipo == ReportesRepository.TIPO_GRAFICO -> "GRÁFICO"
                grafico != null -> "REPORTE · GRÁFICO DE ${grafico.nombreTipo.uppercase()}"
                else -> "REPORTE"
            }
            val tiposArchivo = r.archivoNombres
                .map { if (it.endsWith(".pdf", ignoreCase = true)) "PDF" else "Excel" }
                .distinct()
            TarjetaBlanca {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(etiquetaTipo, fontSize = 11.sp, color = AdminGris)
                    Etiqueta(
                        r.estado,
                        if (r.estado == ReportesRepository.ESTADO_REVISADO) AdminVerde else AdminNaranja
                    )
                }
                Text(r.titulo, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AdminTexto)
                Text("Generado: ${r.fecha}", fontSize = 12.sp, color = AdminGris)
                if (tiposArchivo.isNotEmpty()) {
                    Text("Archivo: ${tiposArchivo.joinToString(" · ")}", fontSize = 12.sp, color = AdminGris)
                }
                TextButton(onClick = { error = null; seleccionado = r }) { Text("Ver detalles") }
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
                    Text("Estado: ${r.estado}", fontSize = 13.sp, color = AdminGris)
                    Spacer(Modifier.height(10.dp))
                    // Filas antiguas de tipo GRAFICO guardaban solo los datos, sin texto
                    if (r.tipo != ReportesRepository.TIPO_GRAFICO) {
                        Text(r.contenido, fontSize = 13.sp)
                    }
                    r.graficoMostrado()?.let { g ->
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Gráfico de ${g.nombreTipo.lowercase()}: unidades por producto",
                            fontSize = 12.sp,
                            color = AdminGris
                        )
                        Spacer(Modifier.height(6.dp))
                        GraficoReporteView(grafico = g, altura = 110)
                        Spacer(Modifier.height(10.dp))
                        g.datos.forEach { (etiqueta, valor) -> Text("• $etiqueta: $valor", fontSize = 13.sp) }
                    }
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
                    error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, fontSize = 12.sp, color = AdminRojo)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { seleccionado = null }) { Text("Cerrar") } }
        )
    }
}