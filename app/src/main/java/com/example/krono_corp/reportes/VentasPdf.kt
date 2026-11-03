package com.example.krono_corp.reportes

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.krono_corp.data.VentaRegistrada
import com.example.krono_corp.data.fechaHora
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Reporte PDF de ventas (resumen por producto + detalle) con las APIs nativas de Android. */
object VentasPdf {

    private const val ANCHO = 595
    private const val ALTO = 842
    private const val MARGEN = 36f
    private const val ALTO_FILA = 18f

    private val AZUL = Color.rgb(22, 119, 255)
    private val OSCURO = Color.rgb(32, 38, 51)
    private val GRIS = Color.rgb(116, 119, 128)
    private val ZEBRA = Color.rgb(240, 243, 248)
    private val TOTAL = Color.rgb(222, 232, 250)

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

        val doc = PdfDocument()
        try {
            val h = Hoja(doc)
            h.texto("Reporte de ventas", 20f, negrita = true, color = OSCURO)
            h.texto("$negocio  ·  $periodo", 11f, color = OSCURO)
            h.texto("Generado: ${LocalDateTime.now().format(vistaLarga)}  ·  Por: $usuario", 9f, color = GRIS)
            h.espacio(10f)

            val totalVentas = ventas.sumOf { it.total }
            val unidades = ventas.sumOf { it.cantidad }
            val descuentos = ventas.sumOf { it.descuento }
            h.texto(
                "${ventas.size} venta(s)  ·  $unidades unidades  ·  Total: ${dinero(totalVentas)}" +
                        if (descuentos > 0) "  ·  Descuentos: ${dinero(descuentos)}" else "",
                11f, negrita = true, color = AZUL
            )
            h.espacio(12f)

            // ---------- Resumen por producto ----------
            h.texto("Resumen por producto", 13f, negrita = true, color = OSCURO)
            h.espacio(4f)
            val porProducto = ventas.groupBy { it.producto }
                .map { (n, l) -> Triple(n, l.sumOf { it.cantidad }, l.sumOf { it.total }) }
                .sortedByDescending { it.third }
            h.tabla(
                anchos = listOf(303f, 100f, 120f),
                alineacion = listOf(Paint.Align.LEFT, Paint.Align.RIGHT, Paint.Align.RIGHT),
                encabezados = listOf("Producto", "Unidades", "Total vendido"),
                filas = porProducto.map { listOf(it.first, it.second.toString(), dinero(it.third)) },
                filaTotal = listOf("TOTAL", unidades.toString(), dinero(totalVentas))
            )
            h.espacio(16f)

            // ---------- Detalle ----------
            h.texto("Detalle de ventas", 13f, negrita = true, color = OSCURO)
            h.espacio(4f)
            h.tabla(
                anchos = listOf(78f, 100f, 34f, 48f, 44f, 52f, 90f, 77f),
                alineacion = listOf(
                    Paint.Align.LEFT, Paint.Align.LEFT, Paint.Align.RIGHT, Paint.Align.RIGHT,
                    Paint.Align.RIGHT, Paint.Align.RIGHT, Paint.Align.LEFT, Paint.Align.LEFT
                ),
                encabezados = listOf("Fecha", "Producto", "Cant.", "Precio", "Desc.", "Total", "Sucursal", "Pago"),
                filas = ventas.map {
                    listOf(
                        it.fechaHora.format(vista), it.producto, it.cantidad.toString(),
                        dinero(it.precioUnitario), dinero(it.descuento), dinero(it.total),
                        it.sucursal.ifBlank { "—" }, it.metodoPago.ifBlank { "—" }
                    )
                },
                filaTotal = listOf("TOTAL", "", unidades.toString(), "", dinero(descuentos), dinero(totalVentas), "", "")
            )

            h.cerrar()
            FileOutputStream(archivo).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return archivo
    }

    private class Hoja(private val doc: PdfDocument) {
        private var numero = 0
        private lateinit var pagina: PdfDocument.Page
        private lateinit var canvas: Canvas
        private var y = MARGEN

        private val pCelda = texto(8.5f, false, OSCURO)
        private val pNegrita = texto(8.5f, true, OSCURO)
        private val pEncabezado = texto(8.5f, true, Color.WHITE)
        private val pRelleno = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val pBorde = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.5f
            color = Color.rgb(200, 205, 215)
        }

        init {
            nueva()
        }

        private fun texto(tam: Float, negrita: Boolean, colorTexto: Int) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = tam
                typeface = if (negrita) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                color = colorTexto
            }

        private fun nueva() {
            if (numero > 0) doc.finishPage(pagina)
            numero++
            pagina = doc.startPage(PdfDocument.PageInfo.Builder(ANCHO, ALTO, numero).create())
            canvas = pagina.canvas
            y = MARGEN
        }

        fun cerrar() = doc.finishPage(pagina)

        fun espacio(alto: Float) {
            y += alto
        }

        fun texto(t: String, tam: Float, negrita: Boolean = false, color: Int = OSCURO) {
            if (y + tam + 6f > ALTO - MARGEN) nueva()
            y += tam + 2f
            canvas.drawText(t, MARGEN, y, texto(tam, negrita, color))
            y += 2f
        }

        private fun ajustar(t: String, ancho: Float, paint: Paint): String {
            val n = paint.breakText(t, true, ancho - 8f, null)
            return if (n < t.length) t.take((n - 1).coerceAtLeast(0)) + "…" else t
        }

        fun tabla(
            anchos: List<Float>,
            alineacion: List<Paint.Align>,
            encabezados: List<String>,
            filas: List<List<String>>,
            filaTotal: List<String>
        ) {
            fun dibujar(celdas: List<String>, fondo: Int?, paint: Paint) {
                var x = MARGEN
                celdas.forEachIndexed { i, t ->
                    val w = anchos[i]
                    if (fondo != null) {
                        pRelleno.color = fondo
                        canvas.drawRect(x, y, x + w, y + ALTO_FILA, pRelleno)
                    }
                    canvas.drawRect(x, y, x + w, y + ALTO_FILA, pBorde)
                    paint.textAlign = alineacion[i]
                    val tx = when (alineacion[i]) {
                        Paint.Align.LEFT -> x + 4f
                        Paint.Align.RIGHT -> x + w - 4f
                        else -> x + w / 2f
                    }
                    canvas.drawText(ajustar(t, w, paint), tx, y + ALTO_FILA / 2f + 3f, paint)
                    x += w
                }
                y += ALTO_FILA
            }

            if (y + ALTO_FILA * 3 > ALTO - MARGEN) nueva()
            dibujar(encabezados, AZUL, pEncabezado)
            filas.forEachIndexed { i, fila ->
                if (y + ALTO_FILA > ALTO - MARGEN) {
                    nueva()
                    dibujar(encabezados, AZUL, pEncabezado) // repite el encabezado
                }
                dibujar(fila, if (i % 2 == 0) ZEBRA else null, pCelda)
            }
            if (y + ALTO_FILA > ALTO - MARGEN) nueva()
            dibujar(filaTotal, TOTAL, pNegrita)
        }
    }
}