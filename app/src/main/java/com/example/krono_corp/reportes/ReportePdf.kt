package com.example.krono_corp.reportes

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.krono_corp.data.SupervisorEquipo
import com.example.krono_corp.data.VentaIntegrante
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generador del reporte PDF con las APIs nativas de Android (android.graphics.pdf.PdfDocument).
 * Reemplaza a OpenPDF + JFreeChart + java.awt, que no existen en Android.
 * El gráfico de pastel se dibuja directamente con Canvas.
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

    private const val ANCHO = 595          // A4 en puntos
    private const val ALTO = 842
    private const val MARGEN = 40f
    private const val ANCHO_UTIL = ANCHO - 2 * MARGEN
    private const val ALTO_FILA = 22f

    private val COLOR_TITULO = Color.rgb(33, 47, 61)
    private val COLOR_ENCABEZADO = Color.rgb(44, 62, 80)
    private val COLOR_FILA_PAR = Color.rgb(236, 240, 241)
    private val COLORES_EQUIPO = mapOf(
        "Norte" to Color.rgb(52, 152, 219),
        "Centro" to Color.rgb(46, 204, 113),
        "Sur" to Color.rgb(231, 76, 60)
    )

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
        val doc = PdfDocument()
        try {
            val e = Escritor(doc)
            val fecha = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())

            e.titulo("Reporte de Promedios de Ventas")
            e.centrado("Fecha de generación: $fecha")
            e.separar(10f)
            e.parrafo("Resumen de las ventas realizadas por cada equipo y su supervisor a cargo.")
            e.separar(10f)

            e.subtitulo("Resumen por equipo")
            e.separar(6f)
            e.tabla(
                anchos = proporcional(listOf(1.2f, 2f, 1.3f, 1.3f, 1.4f), ANCHO_UTIL),
                encabezados = listOf("Equipo", "Supervisor", "Integrantes", "Total ventas", "Promedio"),
                filas = resumenes.map {
                    listOf(it.equipo, it.supervisor, it.integrantes.size.toString(),
                        it.total.toString(), "%.2f".format(it.promedio))
                },
                zebra = true
            )

            e.separar(18f)
            e.subtitulo("Gráfico de promedios")
            e.separar(6f)
            e.pastel(resumenes)

            resumenes.forEach { r ->
                e.separar(12f)
                e.subtitulo("Equipo ${r.equipo} - Supervisor: ${r.supervisor}")
                e.separar(6f)
                e.tabla(
                    anchos = proporcional(listOf(1f, 1f), ANCHO_UTIL * 0.6f),
                    encabezados = listOf("Integrante", "Ventas realizadas"),
                    filas = r.integrantes.map { listOf(it.integrante, it.ventasRealizadas.toString()) },
                    zebra = false
                )
            }

            e.cerrar()
            FileOutputStream(archivo).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return archivo
    }

    private fun proporcional(pesos: List<Float>, total: Float): List<Float> {
        val suma = pesos.sum()
        return pesos.map { it / suma * total }
    }

    private fun paintTexto(
        tam: Float,
        negrita: Boolean = false,
        colorTexto: Int = Color.BLACK,
        alineacion: Paint.Align = Paint.Align.LEFT
    ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = tam
        typeface = if (negrita) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        color = colorTexto
        textAlign = alineacion
    }

    /** Maneja las páginas y la posición vertical (y) para ir "escribiendo" el documento. */
    private class Escritor(private val doc: PdfDocument) {

        private var numero = 0
        private lateinit var pagina: PdfDocument.Page
        private lateinit var canvas: Canvas
        private var y = MARGEN

        private val pTitulo = paintTexto(20f, true, COLOR_TITULO, Paint.Align.CENTER)
        private val pSub = paintTexto(13f, true, COLOR_TITULO)
        private val pTexto = paintTexto(10f)
        private val pTextoCentro = paintTexto(10f, alineacion = Paint.Align.CENTER)
        private val pHeader = paintTexto(10f, true, Color.WHITE, Paint.Align.CENTER)
        private val pCelda = paintTexto(10f, alineacion = Paint.Align.CENTER)
        private val pRelleno = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val pBorde = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.6f
            color = Color.rgb(189, 195, 199)
        }

        init {
            nuevaPagina()
        }

        private fun nuevaPagina() {
            if (numero > 0) doc.finishPage(pagina)
            numero++
            pagina = doc.startPage(PdfDocument.PageInfo.Builder(ANCHO, ALTO, numero).create())
            canvas = pagina.canvas
            y = MARGEN
        }

        fun cerrar() = doc.finishPage(pagina)

        private fun espacio(alto: Float) {
            if (y + alto > ALTO - MARGEN) nuevaPagina()
        }

        fun separar(alto: Float) {
            y += alto
        }

        fun titulo(texto: String) {
            espacio(40f)
            y += 20f
            canvas.drawText(texto, ANCHO / 2f, y, pTitulo)
            y += 8f
        }

        fun centrado(texto: String) {
            espacio(20f)
            y += 16f
            canvas.drawText(texto, ANCHO / 2f, y, pTextoCentro)
        }

        fun parrafo(texto: String) {
            espacio(20f)
            y += 12f
            canvas.drawText(texto, MARGEN, y, pTexto)
        }

        fun subtitulo(texto: String) {
            espacio(50f)
            y += 14f
            canvas.drawText(texto, MARGEN, y, pSub)
            y += 4f
        }

        private fun ajustar(texto: String, ancho: Float, paint: Paint): String {
            val n = paint.breakText(texto, true, ancho - 8f, null)
            return if (n < texto.length) texto.take((n - 1).coerceAtLeast(0)) + "…" else texto
        }

        fun tabla(
            anchos: List<Float>,
            encabezados: List<String>,
            filas: List<List<String>>,
            zebra: Boolean
        ) {
            fun dibujarFila(celdas: List<String>, fondo: Int?, paint: Paint) {
                var x = MARGEN
                celdas.forEachIndexed { i, texto ->
                    val w = anchos[i]
                    if (fondo != null) {
                        pRelleno.color = fondo
                        canvas.drawRect(x, y, x + w, y + ALTO_FILA, pRelleno)
                    }
                    canvas.drawRect(x, y, x + w, y + ALTO_FILA, pBorde)
                    canvas.drawText(ajustar(texto, w, paint), x + w / 2f, y + ALTO_FILA / 2f + 3.5f, paint)
                    x += w
                }
                y += ALTO_FILA
            }

            espacio(ALTO_FILA * 2)
            dibujarFila(encabezados, COLOR_ENCABEZADO, pHeader)
            filas.forEachIndexed { i, fila ->
                if (y + ALTO_FILA > ALTO - MARGEN) {
                    nuevaPagina()
                    dibujarFila(encabezados, COLOR_ENCABEZADO, pHeader) // repite el encabezado
                }
                dibujarFila(fila, if (zebra && i % 2 == 0) COLOR_FILA_PAR else null, pCelda)
            }
        }

        fun pastel(resumenes: List<ResumenEquipo>) {
            val diametro = 190f
            espacio(diametro + 20f)

            val total = resumenes.sumOf { it.promedio }
            val izquierda = MARGEN + 30f
            val oval = RectF(izquierda, y, izquierda + diametro, y + diametro)

            if (total <= 0.0) {
                pRelleno.color = Color.LTGRAY
                canvas.drawOval(oval, pRelleno)
            } else {
                var inicio = -90f
                resumenes.forEach { r ->
                    val barrido = (r.promedio / total * 360.0).toFloat()
                    pRelleno.color = COLORES_EQUIPO[r.equipo] ?: Color.GRAY
                    canvas.drawArc(oval, inicio, barrido, true, pRelleno)
                    inicio += barrido
                }
            }

            // Leyenda a la derecha del gráfico
            val lx = izquierda + diametro + 30f
            var ly = y + 50f
            resumenes.forEach { r ->
                pRelleno.color = COLORES_EQUIPO[r.equipo] ?: Color.GRAY
                canvas.drawRect(lx, ly - 9f, lx + 12f, ly + 3f, pRelleno)
                val pct = if (total > 0.0) r.promedio / total * 100 else 0.0
                canvas.drawText(
                    "${r.equipo}: ${"%.2f".format(r.promedio)} (${"%.0f".format(pct)}%)",
                    lx + 18f, ly, pTexto
                )
                ly += 20f
            }
            y += diametro + 10f
        }
    }
}