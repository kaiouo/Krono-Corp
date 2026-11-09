package com.example.krono_corp.reportes

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.util.Base64
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * ÚNICO dibujante de PDFs de Krono. Recibe el documento como un Markdown simple y lo dibuja con el
 * diseño de VentasPdf (tema "ventas") o de ReportePdf (tema "promedios").
 *
 * Además guarda ese mismo Markdown DENTRO del PDF (metadato "KronoFuente"). Así, cuando la IA edita un
 * PDF hecho por la app, recibe el texto original completo, lo modifica y el PDF se vuelve a dibujar
 * con el mismo diseño: nunca queda "sin color".
 *
 * Sintaxis:
 *   @tema ventas | promedios     (primera línea; si falta, ventas)
 *   # Título
 *   ## Subtítulo
 *   Texto normal
 *   > Línea secundaria (gris)
 *   **Texto destacado**
 *   | Col A | Col B |           (primera fila = encabezado)
 *   |---|---|                   (opcional; con ":" se fija la alineación: |:--|--:|:-:|)
 *   | dato  | 12.50 |
 *   | TOTAL | 12.50 |           (si la última fila empieza con TOTAL se dibuja como fila de totales)
 *   [pastel] Norte=12.5; Centro=10; Sur=8
 *   [barras] Producto A=12; Producto B=8     (gráfico de barras, mismo formato que el pastel)
 *   (línea en blanco = espacio)
 */
object PdfKrono {

    private const val CLAVE_FUENTE = "KronoFuente"
    private const val ANCHO = 595
    private const val ALTO = 842

    // ------------------------------------------------------------------ temas

    private class Tema(
        val margen: Float,
        val altoFila: Float,
        val tamCelda: Float,
        val tamTexto: Float,
        val tamMeta: Float,
        val colorTitulo: Int,
        val colorTexto: Int,
        val colorEncabezado: Int,
        val colorZebra: Int,
        val colorTotal: Int,
        val colorAcento: Int,
        val colorMeta: Int,
        val tituloCentrado: Boolean,
        val metaCentrada: Boolean,
        val celdasCentradas: Boolean,
        val zebraSoloPrimera: Boolean
    )

    /** Diseño de VentasPdf.kt */
    private val VENTAS = Tema(
        margen = 36f, altoFila = 18f, tamCelda = 8.5f, tamTexto = 11f, tamMeta = 9f,
        colorTitulo = Color.rgb(32, 38, 51), colorTexto = Color.rgb(32, 38, 51),
        colorEncabezado = Color.rgb(22, 119, 255), colorZebra = Color.rgb(240, 243, 248),
        colorTotal = Color.rgb(222, 232, 250), colorAcento = Color.rgb(22, 119, 255),
        colorMeta = Color.rgb(116, 119, 128),
        tituloCentrado = false, metaCentrada = false, celdasCentradas = false, zebraSoloPrimera = false
    )

    /** Diseño de ReportePdf.kt */
    private val PROMEDIOS = Tema(
        margen = 40f, altoFila = 22f, tamCelda = 10f, tamTexto = 10f, tamMeta = 10f,
        colorTitulo = Color.rgb(33, 47, 61), colorTexto = Color.BLACK,
        colorEncabezado = Color.rgb(44, 62, 80), colorZebra = Color.rgb(236, 240, 241),
        colorTotal = Color.rgb(213, 219, 219), colorAcento = Color.rgb(33, 47, 61),
        colorMeta = Color.BLACK,
        tituloCentrado = true, metaCentrada = true, celdasCentradas = true, zebraSoloPrimera = true
    )

    private val COLORES_FIJOS = mapOf(
        "Norte" to Color.rgb(52, 152, 219),
        "Centro" to Color.rgb(46, 204, 113),
        "Sur" to Color.rgb(231, 76, 60)
    )
    private val PALETA = listOf(
        Color.rgb(22, 119, 255), Color.rgb(46, 204, 113), Color.rgb(231, 76, 60),
        Color.rgb(243, 156, 18), Color.rgb(155, 89, 182), Color.rgb(26, 188, 156)
    )

    private fun colorDe(nombre: String, i: Int): Int = COLORES_FIJOS[nombre] ?: PALETA[i % PALETA.size]

    // ------------------------------------------------------------------ ayudas para armar el Markdown

    /** Texto seguro para una celda de tabla. */
    fun celda(t: String): String = t.replace("|", "/").replace(Regex("\\s+"), " ").trim()

    /** Texto seguro para una línea suelta (que no se confunda con la sintaxis). */
    fun linea(t: String): String = celda(t).trimStart('#', '>', '|', '*', '@', '[', ' ')

    fun fila(celdas: List<String>): String = "| " + celdas.joinToString(" | ") { celda(it) } + " |"

    fun separador(columnas: Int): String = "|" + "---|".repeat(columnas)

    // ------------------------------------------------------------------ bloques

    private enum class Estilo { TITULO, SUBTITULO, TEXTO, META, DESTACADO }

    private sealed interface Bloque
    private class BTexto(val estilo: Estilo, val texto: String) : Bloque
    private class BEspacio : Bloque
    private class BTabla(
        val encabezados: List<String>,
        val filas: List<List<String>>,
        val total: List<String>?,
        val alineacion: List<Paint.Align?>
    ) : Bloque
    private class BPastel(val datos: List<Pair<String, Double>>) : Bloque
    private class BBarras(val datos: List<Pair<String, Double>>) : Bloque

    private val SEPARADOR = Regex("^\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?$")
    private val NUMERICO = Regex("^[-+]?[\$€]?\\s?-?\\d[\\d.,]*%?$")

    private fun celdasDe(linea: String): List<String> =
        linea.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

    private fun parsear(md: String): Pair<Tema, List<Bloque>> {
        var tema = VENTAS
        val bloques = mutableListOf<Bloque>()
        val lineas = md.replace("\r", "").lines()
        var i = 0
        while (i < lineas.size) {
            val l = lineas[i].trim()
            when {
                l.startsWith("@tema", ignoreCase = true) -> {
                    tema = if (l.contains("promedios", ignoreCase = true)) PROMEDIOS else VENTAS
                    i++
                }
                l.isEmpty() -> {
                    if (bloques.isNotEmpty() && bloques.last() !is BEspacio) bloques.add(BEspacio())
                    i++
                }
                l.startsWith("|") -> {
                    val grupo = mutableListOf<String>()
                    while (i < lineas.size && lineas[i].trim().startsWith("|")) {
                        grupo.add(lineas[i].trim())
                        i++
                    }
                    bloques.add(parsearTabla(grupo))
                }
                l.startsWith("[pastel]", ignoreCase = true) -> {
                    bloques.add(parsearPastel(l.substring("[pastel]".length)))
                    i++
                }
                l.startsWith("[barras]", ignoreCase = true) -> {
                    bloques.add(BBarras(parsearPastel(l.substring("[barras]".length)).datos))
                    i++
                }
                l.startsWith("## ") -> {
                    bloques.add(BTexto(Estilo.SUBTITULO, l.removePrefix("## ").trim()))
                    i++
                }
                l.startsWith("# ") -> {
                    bloques.add(BTexto(Estilo.TITULO, l.removePrefix("# ").trim()))
                    i++
                }
                l.startsWith(">") -> {
                    bloques.add(BTexto(Estilo.META, l.removePrefix(">").trim()))
                    i++
                }
                l.length > 4 && l.startsWith("**") && l.endsWith("**") -> {
                    bloques.add(BTexto(Estilo.DESTACADO, l.removeSurrounding("**").trim()))
                    i++
                }
                else -> {
                    bloques.add(BTexto(Estilo.TEXTO, l))
                    i++
                }
            }
        }
        return tema to bloques
    }

    private fun parsearTabla(lineas: List<String>): BTabla {
        val encabezados = celdasDe(lineas[0])
        var resto = lineas.drop(1)
        var alineacion: List<Paint.Align?> = encabezados.map { null }
        if (resto.isNotEmpty() && SEPARADOR.matches(resto[0])) {
            val sep = celdasDe(resto[0])
            alineacion = encabezados.indices.map { c ->
                val s = sep.getOrElse(c) { "" }
                when {
                    s.startsWith(":") && s.endsWith(":") -> Paint.Align.CENTER
                    s.endsWith(":") -> Paint.Align.RIGHT
                    s.startsWith(":") -> Paint.Align.LEFT
                    else -> null
                }
            }
            resto = resto.drop(1)
        }
        val n = encabezados.size
        var filas = resto.map { r ->
            val c = celdasDe(r)
            List(n) { k -> c.getOrElse(k) { "" } }
        }
        var total: List<String>? = null
        if (filas.isNotEmpty() && filas.last().firstOrNull().equals("TOTAL", ignoreCase = true)) {
            total = filas.last()
            filas = filas.dropLast(1)
        }
        return BTabla(encabezados, filas, total, alineacion)
    }

    private fun parsearPastel(s: String): BPastel = BPastel(
        s.split(";").mapNotNull { p ->
            val k = p.lastIndexOf('=')
            if (k <= 0) null
            else p.substring(0, k).trim() to (p.substring(k + 1).trim().replace(',', '.').toDoubleOrNull() ?: 0.0)
        }
    )

    private fun esNumerica(valores: List<String>): Boolean {
        val reales = valores.filter { it.isNotBlank() && it != "—" && it != "-" }
        return reales.isNotEmpty() && reales.all { NUMERICO.matches(it) }
    }

    // ------------------------------------------------------------------ generar / leer

    /** Dibuja [markdown] en [archivo] y le incrusta el texto fuente para poder editarlo después. */
    fun generar(context: Context, markdown: String, archivo: File): File {
        val (tema, bloques) = parsear(markdown)
        archivo.parentFile?.mkdirs()
        val doc = PdfDocument()
        try {
            val h = Hoja(doc, tema)
            bloques.forEach { h.dibujar(it) }
            h.cerrar()
            FileOutputStream(archivo).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        incrustarFuente(context, archivo, markdown)
        return archivo
    }

    /** Guarda el Markdown dentro del PDF. Si falla, el PDF sigue siendo válido (solo que sin fuente). */
    private fun incrustarFuente(context: Context, archivo: File, markdown: String) {
        try {
            PDFBoxResourceLoader.init(context.applicationContext)
            PDDocument.load(archivo.readBytes()).use { d ->
                val b64 = Base64.encodeToString(markdown.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                d.documentInformation.setCustomMetadataValue(CLAVE_FUENTE, b64)
                d.save(archivo)
            }
        } catch (e: Exception) {
            // sin fuente incrustada: la IA solo podrá leer el texto del PDF
        }
    }

    /**
     * Devuelve (markdown, vinoDeKrono).
     * - PDF hecho por Krono: el Markdown original, exacto.
     * - Cualquier otro PDF: su texto extraído (se rehará con el diseño de Krono).
     */
    fun leerFuente(context: Context, bytes: ByteArray): Pair<String, Boolean> {
        PDFBoxResourceLoader.init(context.applicationContext)
        return PDDocument.load(bytes).use { d ->
            val b64 = d.documentInformation.getCustomMetadataValue(CLAVE_FUENTE)
            val guardado = if (b64.isNullOrBlank()) null else runCatching {
                String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
            }.getOrNull()
            if (!guardado.isNullOrBlank()) {
                guardado to true
            } else {
                val texto = PDFTextStripper().apply { sortByPosition = true }.getText(d)
                textoAMarkdown(texto) to false
            }
        }
    }

    private fun textoAMarkdown(texto: String): String =
        texto.replace("\r", "").lines().map { it.trim() }.filter { it.isNotEmpty() }
            .mapIndexed { i, l -> if (i == 0) "# ${linea(l)}" else linea(l) }
            .joinToString("\n")

    // ------------------------------------------------------------------ dibujo

    private class Hoja(private val doc: PdfDocument, private val t: Tema) {

        private var numero = 0
        private lateinit var pagina: PdfDocument.Page
        private lateinit var canvas: Canvas
        private var y = t.margen
        private var tablas = 0
        private val anchoUtil = ANCHO - 2 * t.margen

        private val pRelleno = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val pBorde = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.6f
            color = Color.rgb(189, 195, 199)
        }

        init {
            nueva()
        }

        private fun paint(
            tam: Float,
            negrita: Boolean,
            colorTexto: Int,
            alineacion: Paint.Align = Paint.Align.LEFT
        ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = tam
            typeface = if (negrita) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            color = colorTexto
            textAlign = alineacion
        }

        private fun nueva() {
            if (numero > 0) doc.finishPage(pagina)
            numero++
            pagina = doc.startPage(PdfDocument.PageInfo.Builder(ANCHO, ALTO, numero).create())
            canvas = pagina.canvas
            y = t.margen
        }

        fun cerrar() = doc.finishPage(pagina)

        fun dibujar(b: Bloque) {
            when (b) {
                is BEspacio -> y += 8f
                is BTexto -> texto(b.estilo, b.texto)
                is BTabla -> tabla(b)
                is BPastel -> pastel(b)
                is BBarras -> barras(b)
            }
        }

        // ---------- texto (con salto de línea automático)

        private fun partir(texto: String, p: Paint, ancho: Float): List<String> {
            val salida = mutableListOf<String>()
            var actual = ""
            for (palabra in texto.split(" ")) {
                val prueba = if (actual.isEmpty()) palabra else "$actual $palabra"
                if (actual.isEmpty() || p.measureText(prueba) <= ancho) {
                    actual = prueba
                } else {
                    salida.add(actual)
                    actual = palabra
                }
            }
            if (actual.isNotEmpty() || salida.isEmpty()) salida.add(actual)
            return salida
        }

        private fun texto(e: Estilo, texto: String) {
            val (tam, negrita, color) = when (e) {
                Estilo.TITULO -> Triple(20f, true, t.colorTitulo)
                Estilo.SUBTITULO -> Triple(13f, true, t.colorTitulo)
                Estilo.TEXTO -> Triple(t.tamTexto, false, t.colorTexto)
                Estilo.META -> Triple(t.tamMeta, false, t.colorMeta)
                Estilo.DESTACADO -> Triple(t.tamTexto, true, t.colorAcento)
            }
            val centrado = (e == Estilo.TITULO && t.tituloCentrado) || (e == Estilo.META && t.metaCentrada)
            val p = paint(tam, negrita, color, if (centrado) Paint.Align.CENTER else Paint.Align.LEFT)

            // un subtítulo nunca se queda solo al final de una página
            if (e == Estilo.SUBTITULO && y + 50f > ALTO - t.margen) nueva()

            partir(texto, p, anchoUtil).forEach { l ->
                if (y + tam + 6f > ALTO - t.margen) nueva()
                y += tam + 2f
                canvas.drawText(l, if (centrado) ANCHO / 2f else t.margen, y, p)
                y += 2f
            }
            if (e == Estilo.SUBTITULO) y += 4f
            if (e == Estilo.TITULO) y += 6f
        }

        // ---------- tabla

        private fun ajustar(texto: String, ancho: Float, p: Paint): String {
            val n = p.breakText(texto, true, ancho - 8f, null)
            return if (n < texto.length) texto.take((n - 1).coerceAtLeast(0)) + "…" else texto
        }

        private fun tabla(b: BTabla) {
            val n = b.encabezados.size
            if (n == 0) return
            tablas++
            val zebra = !t.zebraSoloPrimera || tablas == 1

            val alin = (0 until n).map { c ->
                b.alineacion.getOrNull(c) ?: when {
                    t.celdasCentradas -> Paint.Align.CENTER
                    esNumerica(b.filas.map { it[c] }) -> Paint.Align.RIGHT
                    else -> Paint.Align.LEFT
                }
            }

            // anchos de columna según el largo del contenido
            val pesos = (0 until n).map { c ->
                val largos = b.filas.map { it[c].length } + b.encabezados[c].length + (b.total?.get(c)?.length ?: 0)
                (largos.maxOrNull() ?: 5).coerceIn(5, 36).toFloat()
            }
            val anchoTabla = if (n <= 2) anchoUtil * 0.6f else anchoUtil
            val suma = pesos.sum()
            val anchos = pesos.map { it / suma * anchoTabla }

            val pEnc = paint(t.tamCelda, true, Color.WHITE)
            val pCelda = paint(t.tamCelda, false, t.colorTexto)
            val pNegrita = paint(t.tamCelda, true, t.colorTexto)

            fun dibujarFila(celdas: List<String>, fondo: Int?, p: Paint) {
                var x = t.margen
                celdas.forEachIndexed { i, texto ->
                    val w = anchos[i]
                    if (fondo != null) {
                        pRelleno.color = fondo
                        canvas.drawRect(x, y, x + w, y + t.altoFila, pRelleno)
                    }
                    canvas.drawRect(x, y, x + w, y + t.altoFila, pBorde)
                    p.textAlign = alin[i]
                    val tx = when (alin[i]) {
                        Paint.Align.LEFT -> x + 4f
                        Paint.Align.RIGHT -> x + w - 4f
                        else -> x + w / 2f
                    }
                    canvas.drawText(ajustar(texto, w, p), tx, y + t.altoFila / 2f + t.tamCelda / 3f, p)
                    x += w
                }
                y += t.altoFila
            }

            if (y + t.altoFila * 3 > ALTO - t.margen) nueva()
            dibujarFila(b.encabezados, t.colorEncabezado, pEnc)
            b.filas.forEachIndexed { i, f ->
                if (y + t.altoFila > ALTO - t.margen) {
                    nueva()
                    dibujarFila(b.encabezados, t.colorEncabezado, pEnc) // repite el encabezado
                }
                dibujarFila(f, if (zebra && i % 2 == 0) t.colorZebra else null, pCelda)
            }
            b.total?.let { fila ->
                if (y + t.altoFila > ALTO - t.margen) nueva()
                dibujarFila(fila, t.colorTotal, pNegrita)
            }
        }

        // ---------- gráfico de barras

        private fun barras(b: BBarras) {
            val datos = b.datos.map { it.first to maxOf(0.0, it.second) }
            if (datos.isEmpty()) return
            val altoMax = 120f
            if (y + altoMax + 40f > ALTO - t.margen) nueva()

            val maximo = datos.maxOf { it.second }.coerceAtLeast(1.0)
            val base = y + 14f + altoMax
            val paso = anchoUtil / datos.size
            val grosor = (paso * 0.55f).coerceAtMost(60f)
            val pValor = paint(8.5f, false, t.colorTexto, Paint.Align.CENTER)

            datos.forEachIndexed { i, d ->
                val cx = t.margen + paso * i + paso / 2f
                val alturaBarra = (altoMax * d.second / maximo).toFloat()
                pRelleno.color = colorDe(d.first, i)
                canvas.drawRect(cx - grosor / 2f, base - alturaBarra, cx + grosor / 2f, base, pRelleno)
                val valor = if (d.second == d.second.toLong().toDouble()) d.second.toLong().toString()
                else String.format(Locale.US, "%.2f", d.second)
                canvas.drawText(valor, cx, base - alturaBarra - 3f, pValor)
                canvas.drawText(ajustar(d.first, paso, pValor), cx, base + 12f, pValor)
            }
            canvas.drawLine(t.margen, base, t.margen + anchoUtil, base, pBorde)
            y = base + 22f
        }

        // ---------- gráfico de pastel

        private fun pastel(b: BPastel) {
            val diametro = 190f
            if (y + diametro + 20f > ALTO - t.margen) nueva()

            val datos = b.datos.map { it.first to maxOf(0.0, it.second) }
            val total = datos.sumOf { it.second }
            val izquierda = t.margen + 30f
            val oval = RectF(izquierda, y, izquierda + diametro, y + diametro)

            if (total <= 0.0) {
                pRelleno.color = Color.LTGRAY
                canvas.drawOval(oval, pRelleno)
            } else {
                var inicio = -90f
                datos.forEachIndexed { i, d ->
                    val barrido = (d.second / total * 360.0).toFloat()
                    pRelleno.color = colorDe(d.first, i)
                    canvas.drawArc(oval, inicio, barrido, true, pRelleno)
                    inicio += barrido
                }
            }

            val pTexto = paint(10f, false, t.colorTexto)
            val lx = izquierda + diametro + 30f
            var ly = y + 50f
            datos.forEachIndexed { i, d ->
                pRelleno.color = colorDe(d.first, i)
                canvas.drawRect(lx, ly - 9f, lx + 12f, ly + 3f, pRelleno)
                val pct = if (total > 0.0) d.second / total * 100 else 0.0
                canvas.drawText(
                    "${d.first}: ${String.format(Locale.US, "%.2f", d.second)} (${String.format(Locale.US, "%.0f", pct)}%)",
                    lx + 18f, ly, pTexto
                )
                ly += 20f
            }
            y += diametro + 10f
        }
    }
}