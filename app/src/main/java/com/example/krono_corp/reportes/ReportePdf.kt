package com.example.krono_corp.reportes

import android.content.Context
import com.example.krono_corp.data.SupervisorEquipo
import com.example.krono_corp.data.VentaIntegrante
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Reporte de promedios de ventas por equipo (con gráfico de pastel).
 * Solo arma el documento como texto; el dibujo lo hace PdfKrono (tema "promedios"),
 * el mismo que usa la IA al editar un PDF, así el diseño nunca se pierde.
 */
private class ResumenEquipo(
    val equipo: String,
    val supervisor: String,
    val integrantes: List<VentaIntegrante>
) {
    val total get() = integrantes.sumOf { it.ventasRealizadas }
    val promedio get() = if (integrantes.isEmpty()) 0.0 else total.toDouble() / integrantes.size
}

object ReportePdf {

    /** Genera el PDF en la caché de la app y devuelve el archivo. Llamar desde un hilo de fondo. */
    fun generar(
        context: Context,
        ventas: List<VentaIntegrante>,
        supervisores: List<SupervisorEquipo>,
        equipos: List<String>
    ): File {
        val resumenes = equipos.map { eq ->
            ResumenEquipo(
                equipo = eq,
                supervisor = supervisores.firstOrNull { it.equipo.equals(eq, true) }?.nombreSupervisor
                    ?: "Sin asignar",
                integrantes = ventas.filter { it.equipo.equals(eq, true) }
            )
        }

        val carpeta = File(context.cacheDir, "reportes").apply { mkdirs() }
        val archivo = File(carpeta, "Promedios_de_ventas.pdf")
        return PdfKrono.generar(context, markdown(resumenes), archivo)
    }

    private fun markdown(resumenes: List<ResumenEquipo>): String = buildString {
        val fecha = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())

        appendLine("@tema promedios")
        appendLine("# Reporte de Promedios de Ventas")
        appendLine("> Fecha de generación: $fecha")
        appendLine()
        appendLine("Resumen de las ventas realizadas por cada equipo y su supervisor a cargo.")
        appendLine()

        appendLine("## Resumen por equipo")
        appendLine(PdfKrono.fila(listOf("Equipo", "Supervisor", "Integrantes", "Total ventas", "Promedio")))
        appendLine(PdfKrono.separador(5))
        resumenes.forEach {
            appendLine(
                PdfKrono.fila(
                    listOf(
                        it.equipo, it.supervisor, it.integrantes.size.toString(),
                        it.total.toString(), String.format(Locale.US, "%.2f", it.promedio)
                    )
                )
            )
        }
        appendLine()

        appendLine("## Gráfico de promedios")
        appendLine(
            "[pastel] " + resumenes.joinToString("; ") {
                "${PdfKrono.celda(it.equipo).replace(";", ",").replace("=", "-")}=${String.format(Locale.US, "%.2f", it.promedio)}"
            }
        )

        resumenes.forEach { r ->
            appendLine()
            appendLine("## Equipo ${PdfKrono.celda(r.equipo)} - Supervisor: ${PdfKrono.celda(r.supervisor)}")
            appendLine(PdfKrono.fila(listOf("Integrante", "Ventas realizadas")))
            appendLine(PdfKrono.separador(2))
            r.integrantes.forEach { appendLine(PdfKrono.fila(listOf(it.integrante, it.ventasRealizadas.toString()))) }
        }
    }
}