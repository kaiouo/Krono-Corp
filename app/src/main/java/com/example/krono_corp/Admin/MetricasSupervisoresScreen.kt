package com.example.krono_corp.Admin

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.krono_corp.data.AppUser
import com.example.krono_corp.data.CuentaSupervisor
import com.example.krono_corp.data.GerenteRepository
import com.example.krono_corp.data.VentaRegistrada
import com.example.krono_corp.data.VentasEquipoRepository
import com.example.krono_corp.data.fechaHora
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private data class MetricaSupervisor(
    val cuenta: CuentaSupervisor,
    val ventas: Int,
    val unidades: Int,
    val total: Double,
    val totalMesAnterior: Double
) {
    val ticketPromedio: Double get() = if (ventas == 0) 0.0 else total / ventas
}

private val LOCALE_ES = Locale.forLanguageTag("es-SV")

private fun dinero(v: Double) = String.format(Locale.US, "\$%,.2f", v)

private fun nombreMes(m: YearMonth): String =
    m.month.getDisplayName(TextStyle.FULL, LOCALE_ES).replaceFirstChar { it.uppercase() } + " ${m.year}"

@Composable
fun MetricasSupervisoresScreen(usuario: AppUser) {
    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var supervisores by remember { mutableStateOf<List<CuentaSupervisor>>(emptyList()) }
    var ventas by remember { mutableStateOf<List<VentaRegistrada>>(emptyList()) }
    var mes by remember { mutableStateOf(YearMonth.now()) }

    LaunchedEffect(usuario.id) {
        cargando = true
        error = null

        var lista = emptyList<CuentaSupervisor>()
        GerenteRepository.supervisoresDe(usuario.id)
            .onSuccess { lista = it }
            .onFailure { error = it.message }
        supervisores = lista

        if (lista.isNotEmpty()) {
            VentasEquipoRepository.listarDe(lista.map { it.id }.toSet())
                .onSuccess { ventas = it }
                .onFailure { error = it.message }
        } else {
            ventas = emptyList()
        }
        cargando = false
    }

    val meses = remember { (0..5).map { YearMonth.now().minusMonths(it.toLong()) } }

    val metricas = remember(supervisores, ventas, mes) {
        val anterior = mes.minusMonths(1)
        supervisores.map { s ->
            val propias = ventas.filter { it.usuarioId == s.id }
            val delMes = propias.filter { YearMonth.from(it.fechaHora) == mes }
            val delAnterior = propias.filter { YearMonth.from(it.fechaHora) == anterior }
            MetricaSupervisor(
                cuenta = s,
                ventas = delMes.size,
                unidades = delMes.sumOf { it.cantidad },
                total = delMes.sumOf { it.total },
                totalMesAnterior = delAnterior.sumOf { it.total }
            )
        }.sortedByDescending { it.total }
    }

    val totalEquipo = metricas.sumOf { it.total }
    val mayor = metricas.maxOfOrNull { it.total } ?: 0.0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text(
                "Métricas de supervisores",
                fontSize = 24.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                color = AdminTexto
            )
            Text("Ventas de cada supervisor por mes", fontSize = 13.sp, color = AdminGris)
            Spacer(Modifier.height(8.dp))

            if (cargando) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }
            error?.let {
                Text(it, color = AdminRojo, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
            }

            // Selector de mes
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                meses.forEach { m ->
                    if (m == mes) {
                        Button(
                            onClick = { mes = m },
                            colors = ButtonDefaults.buttonColors(containerColor = AdminAzul)
                        ) { Text(nombreMes(m), fontSize = 12.sp) }
                    } else {
                        OutlinedButton(onClick = { mes = m }) { Text(nombreMes(m), fontSize = 12.sp) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            TarjetaBlanca {
                Column {
                    Text("Total del equipo · ${nombreMes(mes)}", fontSize = 12.sp, color = AdminGris)
                    Text(
                        dinero(totalEquipo),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = AdminTexto
                    )
                    Text(
                        "${metricas.sumOf { it.ventas }} ventas · ${metricas.sumOf { it.unidades }} unidades",
                        fontSize = 12.sp,
                        color = AdminGris
                    )
                }
            }
        }

        if (!cargando && supervisores.isEmpty()) {
            item { TextoVacio("No tienes supervisores asignados en tu equipo.") }
        }

        items(metricas, key = { it.cuenta.id }) { m ->
            TarjetaBlanca {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            m.cuenta.alias,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = AdminTexto,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            dinero(m.total),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = AdminVerde
                        )
                    }
                    Text(m.cuenta.email, fontSize = 12.sp, color = AdminGris)

                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { if (mayor > 0) (m.total / mayor).toFloat() else 0f },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Ventas", fontSize = 11.sp, color = AdminGris)
                            Text("${m.ventas}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AdminTexto)
                        }
                        Column {
                            Text("Unidades", fontSize = 11.sp, color = AdminGris)
                            Text("${m.unidades}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AdminTexto)
                        }
                        Column {
                            Text("Ticket promedio", fontSize = 11.sp, color = AdminGris)
                            Text(
                                dinero(m.ticketPromedio),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AdminTexto
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("vs mes anterior", fontSize = 11.sp, color = AdminGris)
                            val variacion = when {
                                m.totalMesAnterior > 0 ->
                                    (m.total - m.totalMesAnterior) / m.totalMesAnterior * 100
                                else -> null
                            }
                            Text(
                                text = variacion?.let { String.format(Locale.US, "%+.0f%%", it) } ?: "—",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = when {
                                    variacion == null -> AdminGris
                                    variacion >= 0 -> AdminVerde
                                    else -> AdminRojo
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}