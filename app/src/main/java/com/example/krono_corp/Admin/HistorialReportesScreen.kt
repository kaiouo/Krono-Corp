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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.krono_corp.data.AppUser
import com.example.krono_corp.data.CuentaSupervisor
import com.example.krono_corp.data.GerenteRepository
import com.example.krono_corp.data.ReporteRemoto
import com.example.krono_corp.data.ReportesRepository
import com.example.krono_corp.negocio.MiniBarChart
import kotlinx.coroutines.launch

/**
 * Reportes y gráficos que generan los supervisores del gerente (tabla "reportes").
 * El gerente puede buscarlos, filtrarlos por estado o supervisor y marcarlos como Revisado.
 */
@Composable
fun HistorialReportesScreen(usuario: AppUser) {
    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var supervisores by remember { mutableStateOf<List<CuentaSupervisor>>(emptyList()) }
    var reportes by remember { mutableStateOf<List<ReporteRemoto>>(emptyList()) }
    var busqueda by remember { mutableStateOf("") }
    var filtroEstado by remember { mutableStateOf("Todos") }
    var filtroSupervisor by remember { mutableStateOf<String?>(null) } // null = todos
    var seleccionado by remember { mutableStateOf<ReporteRemoto?>(null) }
    var recarga by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(recarga) {
        cargando = true
        error = null
        GerenteRepository.supervisoresDe(usuario.id)
            .onSuccess { supervisores = it }
            .onFailure { error = it.message }
        ReportesRepository.listar()
            .onSuccess { reportes = it }
            .onFailure { if (error == null) error = it.message }
        cargando = false
    }

    val ids = supervisores.map { it.id }.toSet()
    val misReportes = reportes.filter { it.supervisorId in ids }
    val filtrados = misReportes.filter { r ->
        (busqueda.isBlank() ||
                r.titulo.contains(busqueda, ignoreCase = true) ||
                r.supervisorNombre.contains(busqueda, ignoreCase = true)) &&
                (filtroEstado == "Todos" || r.estado == filtroEstado) &&
                (filtroSupervisor == null || r.supervisorId == filtroSupervisor)
    }
    val pendientes = misReportes.count { it.estado == ReportesRepository.ESTADO_PENDIENTE }

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
                    "Reportes de supervisores",
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

        item {
            OutlinedTextField(
                value = busqueda,
                onValueChange = { busqueda = it },
                label = { Text("Buscar por título o supervisor") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Text("Estado", fontSize = 12.sp, color = AdminGris)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf("Todos", ReportesRepository.ESTADO_PENDIENTE, ReportesRepository.ESTADO_REVISADO)) { estado ->
                    FilterChip(
                        selected = filtroEstado == estado,
                        onClick = { filtroEstado = estado },
                        label = { Text(estado) }
                    )
                }
            }
        }

        if (supervisores.isNotEmpty()) {
            item {
                Text("Supervisor", fontSize = 12.sp, color = AdminGris)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(
                            selected = filtroSupervisor == null,
                            onClick = { filtroSupervisor = null },
                            label = { Text("Todos") }
                        )
                    }
                    items(supervisores, key = { it.id }) { s ->
                        FilterChip(
                            selected = filtroSupervisor == s.id,
                            onClick = { filtroSupervisor = s.id },
                            label = { Text(s.alias) }
                        )
                    }
                }
            }
        }

        item {
            Text(
                "${filtrados.size} encontrados · $pendientes pendientes de revisar",
                fontSize = 12.sp,
                color = AdminGris
            )
        }

        if (!cargando && error == null && supervisores.isEmpty()) {
            item { TextoVacio("Aún no tienes supervisores asignados, así que no hay reportes que mostrar.") }
        } else if (!cargando && error == null && misReportes.isEmpty()) {
            item { TextoVacio("Tus supervisores todavía no han generado reportes ni gráficos.") }
        }

        items(filtrados, key = { it.id }) { r ->
            val sucursal = supervisores.firstOrNull { it.id == r.supervisorId }?.sucursal.orEmpty()
            TarjetaBlanca {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (r.tipo == ReportesRepository.TIPO_GRAFICO) "GRÁFICO" else "REPORTE",
                        fontSize = 11.sp,
                        color = AdminGris
                    )
                    Etiqueta(
                        r.estado,
                        if (r.estado == ReportesRepository.ESTADO_REVISADO) AdminVerde else AdminNaranja
                    )
                }
                Text(r.titulo, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AdminTexto)
                Text(
                    "Supervisor: ${r.supervisorNombre}" + if (sucursal.isNotBlank()) " · $sucursal" else "",
                    fontSize = 13.sp,
                    color = AdminGris
                )
                Text("Generado: ${r.fecha}", fontSize = 12.sp, color = AdminGris)
                TextButton(onClick = { seleccionado = r }) { Text("Ver detalles") }
            }
        }
    }

    seleccionado?.let { r ->
        AlertDialog(
            onDismissRequest = { seleccionado = null },
            title = { Text(r.titulo, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Supervisor: ${r.supervisorNombre}", fontSize = 13.sp)
                    Text("Generado: ${r.fecha}", fontSize = 13.sp, color = AdminGris)
                    Text("Estado: ${r.estado}", fontSize = 13.sp, color = AdminGris)
                    Spacer(Modifier.height(10.dp))
                    if (r.tipo == ReportesRepository.TIPO_GRAFICO) {
                        val datos = ReportesRepository.decodificarDatos(r.contenido)
                        MiniBarChart(datos = datos, altura = 110)
                        Spacer(Modifier.height(10.dp))
                        datos.forEach { (etiqueta, valor) -> Text("• $etiqueta: $valor", fontSize = 13.sp) }
                    } else {
                        Text(r.contenido, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val nuevo = if (r.estado == ReportesRepository.ESTADO_REVISADO)
                        ReportesRepository.ESTADO_PENDIENTE else ReportesRepository.ESTADO_REVISADO
                    seleccionado = null
                    scope.launch {
                        ReportesRepository.cambiarEstado(r.id, nuevo)
                            .onSuccess {
                                reportes = reportes.map { x -> if (x.id == r.id) x.copy(estado = nuevo) else x }
                            }
                            .onFailure { error = it.message }
                    }
                }) {
                    Text(
                        if (r.estado == ReportesRepository.ESTADO_REVISADO) "Marcar como pendiente"
                        else "Marcar como revisado"
                    )
                }
            },
            dismissButton = { TextButton(onClick = { seleccionado = null }) { Text("Cerrar") } }
        )
    }
}
