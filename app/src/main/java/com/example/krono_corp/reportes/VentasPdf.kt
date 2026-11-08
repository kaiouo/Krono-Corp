package com.example.krono_corp.reportes

import android.content.Context
import com.example.krono_corp.data.VentaRegistrada
import com.example.krono_corp.data.fechaHora
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Reporte PDF de ventas (resumen por producto + detalle).
 * Solo arma el documento como texto; el dibujo (colores, tablas, paginación) lo hace PdfKrono,
 * que es el mismo que usa la IA al editar un PDF, así el diseño nunca se pierde.
 */
object VentasPdf {

    private val vista = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm")
    private val vistaLarga = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    private fun dinero(v: Double) = "$" + String.format(Locale.US, "%,.2f", v)

    fun generar(
        context: Context,
        ventas: List<VentaRegistrada>,
        periodo: String,
        usuario: String,
        negocio: String
    ): File {
        val carpeta = File(context.cacheDir, "reportes").apply { mkdirs() }
        val sello = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
        val archivo = File(carpeta, "Ventas_$sello.pdf")
        return PdfKrono.generar(context, markdown(ventas, periodo, usuario, negocio), archivo)
    }

    private fun markdown(
        ventas: List<VentaRegistrada>,
        periodo: String,
        usuario: String,
        negocio: String
    ): String = buildString {
        val totalVentas = ventas.sumOf { it.total }
        val unidades = ventas.sumOf { it.cantidad }
        val descuentos = ventas.sumOf { it.descuento }

        appendLine("@tema ventas")
        appendLine("# Reporte de ventas")
        appendLine(PdfKrono.linea("$negocio  ·  $periodo"))
        appendLine("> Generado: ${LocalDateTime.now().format(vistaLarga)}  ·  Por: ${PdfKrono.celda(usuario)}")
        appendLine()
        appendLine(
            "**${ventas.size} venta(s)  ·  $unidades unidades  ·  Total: ${dinero(totalVentas)}" +
                    (if (descuentos > 0) "  ·  Descuentos: ${dinero(descuentos)}" else "") + "**"
        )
        appendLine()

        // ---------- Resumen por producto ----------
        appendLine("## Resumen por producto")
        val porProducto = ventas.groupBy { it.producto }
            .map { (n, l) -> Triple(n, l.sumOf { it.cantidad }, l.sumOf { it.total }) }
            .sortedByDescending { it.third }
        appendLine(PdfKrono.fila(listOf("Producto", "Unidades", "Total vendido")))
        appendLine(PdfKrono.separador(3))
        porProducto.forEach { appendLine(PdfKrono.fila(listOf(it.first, it.second.toString(), dinero(it.third)))) }
        appendLine(PdfKrono.fila(listOf("TOTAL", unidades.toString(), dinero(totalVentas))))
        appendLine()

        // ---------- Detalle ----------
        appendLine("## Detalle de ventas")
        appendLine(PdfKrono.fila(listOf("Fecha", "Producto", "Cant.", "Precio", "Desc.", "Total", "Sucursal", "Pago")))
        appendLine(PdfKrono.separador(8))
        ventas.forEach {
            appendLine(
                PdfKrono.fila(
                    listOf(
                        it.fechaHora.format(vista), it.producto, it.cantidad.toString(),
                        dinero(it.precioUnitario), dinero(it.descuento), dinero(it.total),
                        it.sucursal.ifBlank { "—" }, it.metodoPago.ifBlank { "—" }
                    )
                )
            )
        }
        appendLine(PdfKrono.fila(listOf("TOTAL", "", unidades.toString(), "", dinero(descuentos), dinero(totalVentas), "", "")))
    }
}