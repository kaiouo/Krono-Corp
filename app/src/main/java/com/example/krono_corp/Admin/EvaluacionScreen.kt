package com.example.krono_corp.Admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.krono_corp.data.AppUser
import com.example.krono_corp.data.CuentaSupervisor
import com.example.krono_corp.data.EQUIPOS
import com.example.krono_corp.data.GerenteRepository
import com.example.krono_corp.data.ReporteRemoto
import com.example.krono_corp.data.ReportesRepository
import com.example.krono_corp.data.SupervisoresRepository
import com.example.krono_corp.data.VentaIntegrante
import com.example.krono_corp.negocio.MiniBarChart
import kotlinx.coroutines.launch

private data class RendimientoSucursal(
    val sucursal: String,
    val supervisores: List<String>,
    val integrantes: Int,
    val totalVentas: Int,
    val promedio: Double,
    val reportes: Int
)

/**
 * Supervisores del gerente + rendimiento por sucursal.
 *  - Lista: cuentas con rol SUPERVISOR cuyo gerenteId es este gerente (tabla "usuarios")
 *  - Rendimiento: ventas por sucursal (tabla "ventas") y reportes enviados (tabla "reportes")
 */
@Composable
fun EvaluacionScreen(usuario: AppUser) {
    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var supervisores by remember { mutableStateOf<List<CuentaSupervisor>>(emptyList()) }
    var reportes by remember { mutableStateOf<List<ReporteRemoto>>(emptyList()) }
    var ventas by remember { mutableStateOf<List<VentaIntegrante>>(emptyList()) }
    var busqueda by remember { mutableStateOf("") }
    var porQuitar by remember { mutableStateOf<CuentaSupervisor?>(null) }
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
        SupervisoresRepository.listarVentas()
            .onSuccess { ventas = it }
            .onFailure { if (error == null) error = it.message }
        cargando = false
    }

    val idsSupervisores = supervisores.map { it.id }.toSet()
    val reportesEquipo = reportes.filter { it.supervisorId in idsSupervisores }

    val rendimiento = EQUIPOS.map { sucursal ->
        val deSucursal = supervisores.filter { it.sucursal.equals(sucursal, ignoreCase = true) }
        val ventasSucursal = ventas.filter { it.equipo.equals(sucursal, ignoreCase = true) }
        val total = ventasSucursal.sumOf { it.ventasRealizadas }
        RendimientoSucursal(
            sucursal = sucursal,
            supervisores = deSucursal.map { it.alias },
            integrantes = ventasSucursal.size,
            totalVentas = total,
            promedio = if (ventasSucursal.isEmpty()) 0.0 else total.toDouble() / ventasSucursal.size,
            reportes = reportesEquipo.count { r -> deSucursal.any { it.id == r.supervisorId } }
        )
    }

    val visibles = supervisores.filter {
        busqueda.isBlank() ||
                it.alias.contains(busqueda, ignoreCase = true) ||
                it.email.contains(busqueda, ignoreCase = true) ||
                it.sucursal.contains(busqueda, ignoreCase = true)
    }

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
                    "Supervisores y rendimiento",
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

        // ---------- Rendimiento por sucursal ----------
        item {
            Text("Rendimiento por sucursal", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AdminTexto)
        }
        item {
            TarjetaBlanca {
                Text("Total de ventas por sucursal", fontSize = 12.sp, color = AdminGris)
                Spacer(Modifier.height(8.dp))
                MiniBarChart(datos = rendimiento.map { it.sucursal to it.totalVentas }, altura = 90)
            }
        }
        items(rendimiento) { r -> TarjetaRendimiento(r) }

        // ---------- Lista de supervisores ----------
        item {
            Spacer(Modifier.height(4.dp))
            Text(
                "Mis supervisores (${supervisores.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = AdminTexto
            )
        }
        if (supervisores.isNotEmpty()) {
            item {
                OutlinedTextField(
                    value = busqueda,
                    onValueChange = { busqueda = it },
                    label = { Text("Buscar por nombre, correo o sucursal") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (!cargando && supervisores.isEmpty()) {
            item {
                TextoVacio(
                    "Aún no tienes supervisores. Entra a «Nuevo supervisor» para asignar " +
                            "a las personas que se registraron con el rol Supervisor de Ventas."
                )
            }
        }
        items(visibles, key = { it.id }) { s ->
            val suyos = reportes.filter { it.supervisorId == s.id }
            TarjetaBlanca {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        s.alias,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = AdminTexto,
                        modifier = Modifier.weight(1f)
                    )
                    Etiqueta(s.sucursal.ifBlank { "Sin sucursal" }, AdminAzul)
                }
                Text(s.email, fontSize = 13.sp, color = AdminGris)
                if (s.phone.isNotBlank()) Text(s.phone, fontSize = 13.sp, color = AdminGris)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Reportes: ${suyos.count { it.tipo == ReportesRepository.TIPO_REPORTE }} · " +
                            "Gráficos: ${suyos.count { it.tipo == ReportesRepository.TIPO_GRAFICO }} · " +
                            "Último: ${suyos.firstOrNull()?.fecha ?: "—"}",
                    fontSize = 12.sp,
                    color = AdminTexto
                )
                TextButton(onClick = { porQuitar = s }) {
                    Text("Quitar de mi equipo", color = AdminRojo)
                }
            }
        }
    }

    porQuitar?.let { s ->
        AlertDialog(
            onDismissRequest = { porQuitar = null },
            title = { Text("¿Quitar a ${s.alias}?") },
            text = {
                Text("Dejará de aparecer en tu equipo. Su cuenta no se borra y podrás volver a asignarlo.")
            },
            confirmButton = {
                TextButton(onClick = {
                    porQuitar = null
                    scope.launch {
                        GerenteRepository.quitar(s.id)
                            .onSuccess { recarga++ }
                            .onFailure { error = it.message }
                    }
                }) { Text("Quitar", color = AdminRojo) }
            },
            dismissButton = { TextButton(onClick = { porQuitar = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun TarjetaRendimiento(r: RendimientoSucursal) {
    TarjetaBlanca {
        Text("Sucursal ${r.sucursal}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AdminTexto)
        Text(
            if (r.supervisores.isEmpty()) "Sin supervisor asignado"
            else "Supervisor(es): ${r.supervisores.joinToString(", ")}",
            fontSize = 12.sp,
            color = AdminGris
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Dato("Integrantes", r.integrantes.toString())
            Dato("Total ventas", r.totalVentas.toString())
            Dato("Promedio", "%.1f".format(r.promedio))
            Dato("Reportes", r.reportes.toString())
        }
    }
}

@Composable
private fun Dato(etiqueta: String, valor: String) {
    Column {
        Text(valor, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = AdminTexto)
        Text(etiqueta, fontSize = 11.sp, color = AdminGris)
    }
}
