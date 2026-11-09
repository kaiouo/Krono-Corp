package com.example.krono_corp.reportes

import android.content.Context
import com.example.krono_corp.data.GraficoReporte
import com.example.krono_corp.data.VentaRegistrada
import com.example.krono_corp.data.fechaHora
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Genera un .xlsx REAL sin librerías externas (un .xlsx es un ZIP con archivos XML).
 * Hoja 1 "Ventas": detalle con fila de TOTAL (fórmulas SUM).
 * Hoja 2 "Resumen": unidades y total por producto.
 */
object VentasExcel {

    // Estilos (índices de cellXfs en styles.xml)
    private const val NORMAL = 0
    private const val ENCABEZADO = 1
    private const val DINERO = 2
    private const val DINERO_NEGRITA = 3
    private const val ENTERO = 4
    private const val NEGRITA = 5

    private sealed interface Celda
    private class Txt(val valor: String, val estilo: Int = NORMAL) : Celda
    private class Num(val valor: Double, val estilo: Int = NORMAL) : Celda
    private class Formula(val formula: String, val cache: Double, val estilo: Int) : Celda

    private val vista = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    fun generar(
        context: Context,
        ventas: List<VentaRegistrada>,
        periodo: String,
        grafico: GraficoReporte? = null
    ): File {
        val carpeta = File(context.cacheDir, "reportes").apply { mkdirs() }
        val sello = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
        val archivo = File(carpeta, "Ventas_$sello.xlsx")

        ZipOutputStream(FileOutputStream(archivo)).use { zip ->
            fun entrada(nombre: String, contenido: String) {
                zip.putNextEntry(ZipEntry(nombre))
                zip.write(contenido.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entrada(
                "[Content_Types].xml",
                if (grafico != null) CONTENT_TYPES.replace("</Types>", TIPOS_GRAFICO + "</Types>") else CONTENT_TYPES
            )
            entrada("_rels/.rels", RELS_RAIZ)
            entrada("xl/workbook.xml", WORKBOOK)
            entrada("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            entrada("xl/styles.xml", STYLES)
            entrada("xl/worksheets/sheet1.xml", hojaVentas(ventas))
            entrada("xl/worksheets/sheet2.xml", hojaResumen(ventas, grafico))
            if (grafico != null) {
                entrada("xl/worksheets/_rels/sheet2.xml.rels", RELS_HOJA_RESUMEN)
                entrada("xl/drawings/drawing1.xml", DIBUJO)
                entrada("xl/drawings/_rels/drawing1.xml.rels", RELS_DIBUJO)
                entrada("xl/charts/chart1.xml", xmlGrafico(grafico))
            }
        }
        return archivo
    }

    // ---------------------------------------------------------------- hojas

    private fun hojaVentas(ventas: List<VentaRegistrada>): String {
        // Columnas extra: una por cada pregunta personalizada que exista en las ventas
        val extras = ventas.flatMap { it.extras.keys }.distinct()

        val encabezados = listOf(
            "Fecha", "Producto", "Cantidad", "Precio unitario", "Descuento", "Total",
            "Sucursal", "Método de pago", "Nota", "Supervisor"
        ) + extras

        val filas = mutableListOf<List<Celda?>>()
        filas += encabezados.map { Txt(it, ENCABEZADO) }

        ventas.forEach { v ->
            filas += listOf(
                Txt(v.fechaHora.format(vista)),
                Txt(v.producto),
                Num(v.cantidad.toDouble(), ENTERO),
                Num(v.precioUnitario, DINERO),
                Num(v.descuento, DINERO),
                Num(v.total, DINERO),
                Txt(v.sucursal),
                Txt(v.metodoPago),
                Txt(v.nota),
                Txt(v.usuarioNombre)
            ) + extras.map { Txt(v.extras[it].orEmpty()) }
        }

        val ultima = ventas.size + 1
        filas += listOf(
            Txt("TOTAL", NEGRITA),
            null,
            Formula("SUM(C2:C$ultima)", ventas.sumOf { it.cantidad }.toDouble(), NEGRITA),
            null,
            Formula("SUM(E2:E$ultima)", ventas.sumOf { it.descuento }, DINERO_NEGRITA),
            Formula("SUM(F2:F$ultima)", ventas.sumOf { it.total }, DINERO_NEGRITA)
        )

        val anchos = listOf(18, 24, 10, 15, 12, 12, 26, 16, 30) + extras.map { 28 }
        return hojaXml(filas, anchos)
    }

    private fun hojaResumen(ventas: List<VentaRegistrada>, grafico: GraficoReporte?): String {
        val porProducto = ventas.groupBy { it.producto }
            .map { (nombre, lista) -> Triple(nombre, lista.sumOf { it.cantidad }, lista.sumOf { it.total }) }
            .sortedByDescending { it.third }

        val filas = mutableListOf<List<Celda?>>()
        filas += listOf(Txt("Producto", ENCABEZADO), Txt("Unidades", ENCABEZADO), Txt("Total vendido", ENCABEZADO))
        porProducto.forEach { (nombre, unidades, total) ->
            filas += listOf(Txt(nombre), Num(unidades.toDouble(), ENTERO), Num(total, DINERO))
        }
        val ultima = porProducto.size + 1
        filas += listOf(
            Txt("TOTAL", NEGRITA),
            Formula("SUM(B2:B$ultima)", porProducto.sumOf { it.second }.toDouble(), NEGRITA),
            Formula("SUM(C2:C$ultima)", porProducto.sumOf { it.third }, DINERO_NEGRITA)
        )

        if (grafico != null) {
            // Datos del gráfico en las columnas E:F (el gráfico los lee de ahí y se puede editar en Excel)
            val bloque = mutableListOf<List<Celda?>>()
            bloque += listOf(Txt("Gráfico", ENCABEZADO), Txt("Unidades", ENCABEZADO))
            grafico.datos.forEach { (etiqueta, valor) -> bloque += listOf(Txt(etiqueta), Num(valor.toDouble(), ENTERO)) }
            val total = maxOf(filas.size, bloque.size)
            val unidas = (0 until total).map { i ->
                val izquierda = filas.getOrNull(i) ?: emptyList()
                val relleno: List<Celda?> = List((3 - izquierda.size).coerceAtLeast(0)) { null }
                izquierda + relleno + listOf<Celda?>(null) + (bloque.getOrNull(i) ?: listOf<Celda?>(null, null))
            }
            return hojaXml(unidas, listOf(28, 12, 16, 4, 28, 12), conDibujo = true)
        }
        return hojaXml(filas, listOf(28, 12, 16))
    }

    private fun hojaXml(filas: List<List<Celda?>>, anchos: List<Int>, conDibujo: Boolean = false): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append(
            "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"" +
                    (if (conDibujo) " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"" else "") +
                    ">"
        )
        sb.append(
            "<sheetViews><sheetView workbookViewId=\"0\">" +
                    "<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>" +
                    "</sheetView></sheetViews>"
        )
        sb.append("<cols>")
        anchos.forEachIndexed { i, w ->
            sb.append("<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"$w\" customWidth=\"1\"/>")
        }
        sb.append("</cols><sheetData>")

        filas.forEachIndexed { r, fila ->
            sb.append("<row r=\"${r + 1}\">")
            fila.forEachIndexed { c, celda ->
                val ref = "${columna(c)}${r + 1}"
                when (celda) {
                    null -> Unit
                    is Txt -> sb.append(
                        "<c r=\"$ref\" t=\"inlineStr\" s=\"${celda.estilo}\"><is>" +
                                "<t xml:space=\"preserve\">${escapar(celda.valor)}</t></is></c>"
                    )
                    is Num -> sb.append("<c r=\"$ref\" s=\"${celda.estilo}\"><v>${numero(celda.valor)}</v></c>")
                    is Formula -> sb.append(
                        "<c r=\"$ref\" s=\"${celda.estilo}\"><f>${celda.formula}</f>" +
                                "<v>${numero(celda.cache)}</v></c>"
                    )
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData>")
        if (conDibujo) sb.append("<drawing r:id=\"rId1\"/>")
        sb.append("</worksheet>")
        return sb.toString()
    }

    // ---------------------------------------------------------------- Excel desde texto (lo usa la IA)

    private val NUMERO = Regex("-?(0|[1-9][0-9]{0,14})(\\.[0-9]+)?")
    private val PALABRAS_DINERO = listOf("precio", "total", "descuento", "monto", "costo", "importe", "iva", "ganancia")

    /**
     * Crea un .xlsx con el diseño de Krono (encabezado azul, formato de dinero, filas TOTAL en negrita
     * con fórmulas SUM, panel congelado y anchos) a partir de hojas ya convertidas a texto.
     * La IA lo usa cuando no puede editar el Excel original, para que el resultado NUNCA salga sin color.
     */
    fun desdeTabla(hojas: List<Pair<String, List<List<String>>>>): ByteArray {
        val lista = hojas.ifEmpty { listOf("Hoja1" to emptyList()) }
        val usados = mutableSetOf<String>()
        val nombres = lista.mapIndexed { i, h -> nombreHojaValido(h.first, usados, i + 1) }
        val xmlHojas = lista.map { hojaDesdeTabla(it.second) }

        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zip ->
            fun entrada(nombre: String, contenido: String) {
                zip.putNextEntry(ZipEntry(nombre))
                zip.write(contenido.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entrada("[Content_Types].xml", contentTypesN(lista.size))
            entrada("_rels/.rels", RELS_RAIZ)
            entrada("xl/workbook.xml", workbookN(nombres))
            entrada("xl/_rels/workbook.xml.rels", workbookRelsN(lista.size))
            entrada("xl/styles.xml", STYLES)
            xmlHojas.forEachIndexed { i, x -> entrada("xl/worksheets/sheet${i + 1}.xml", x) }
        }
        return bos.toByteArray()
    }

    private fun tipoColumna(encabezado: String): Int {
        val h = encabezado.lowercase()
        return when {
            "ventas" in h -> NORMAL
            PALABRAS_DINERO.any { it in h } -> DINERO
            "cant" in h || "unidad" in h -> ENTERO
            else -> NORMAL
        }
    }

    private fun hojaDesdeTabla(filas: List<List<String>>): String {
        val datos = filas.filter { f -> f.any { it.isNotBlank() } }
        if (datos.isEmpty()) return hojaXml(emptyList(), emptyList())

        val encabezados = datos.first()
        val columnas = datos.maxOf { it.size }
        val tipos = (0 until columnas).map { tipoColumna(encabezados.getOrElse(it) { "" }) }

        val celdas = mutableListOf<List<Celda?>>()
        celdas += (0 until columnas).map { Txt(encabezados.getOrElse(it) { "" }, ENCABEZADO) }

        datos.drop(1).forEachIndexed { i, fila ->
            val numFila = i + 2
            val esTotal = fila.firstOrNull()?.trim().equals("TOTAL", ignoreCase = true)
            celdas += (0 until columnas).map { c ->
                val v = fila.getOrElse(c) { "" }.trim()
                when {
                    v.isEmpty() -> null
                    NUMERO.matches(v) -> {
                        val estilo = when (tipos[c]) {
                            DINERO -> if (esTotal) DINERO_NEGRITA else DINERO
                            ENTERO -> if (esTotal) NEGRITA else ENTERO
                            else -> if (esTotal) NEGRITA else NORMAL
                        }
                        if (esTotal && tipos[c] != NORMAL && numFila > 2) {
                            Formula("SUM(${columna(c)}2:${columna(c)}${numFila - 1})", v.toDouble(), estilo)
                        } else {
                            Num(v.toDouble(), estilo)
                        }
                    }
                    else -> Txt(v, if (esTotal) NEGRITA else NORMAL)
                }
            }
        }

        val anchos = (0 until columnas).map { c ->
            datos.maxOf { it.getOrElse(c) { "" }.length }.coerceIn(10, 40)
        }
        return hojaXml(celdas, anchos)
    }

    private fun nombreHojaValido(crudo: String, usados: MutableSet<String>, numero: Int): String {
        var nombre = crudo.replace(Regex("""[\\/?*\[\]:]"""), "_").trim().take(31)
        if (nombre.isBlank()) nombre = "Hoja$numero"
        var resultado = nombre
        var n = 2
        while (!usados.add(resultado.lowercase())) {
            val sufijo = " ($n)"
            resultado = nombre.take(31 - sufijo.length) + sufijo
            n++
        }
        return resultado
    }

    private fun contentTypesN(n: Int): String {
        val hojas = (1..n).joinToString("") {
            """<Override PartName="/xl/worksheets/sheet${it}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>${hojas}<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>"""
    }

    private fun workbookN(nombres: List<String>): String {
        val hojas = nombres.mapIndexed { i, n ->
            """<sheet name="${escapar(n)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>"""
        }.joinToString("")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>${hojas}</sheets><calcPr fullCalcOnLoad="1"/></workbook>"""
    }

    private fun workbookRelsN(n: Int): String {
        val hojas = (1..n).joinToString("") {
            """<Relationship Id="rId${it}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it}.xml"/>"""
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">${hojas}<Relationship Id="rId${n + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""
    }

    // ---------------------------------------------------------------- utilidades

    /** 0 -> A, 25 -> Z, 26 -> AA ... */
    private fun columna(indice: Int): String {
        var n = indice
        val sb = StringBuilder()
        do {
            sb.insert(0, ('A' + n % 26))
            n = n / 26 - 1
        } while (n >= 0)
        return sb.toString()
    }

    private fun escapar(t: String) = t
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun numero(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString()
        else String.format(Locale.US, "%.4f", v)

    // ---------------------------------------------------------------- gráfico nativo

    /** Gráfico de barras o de pastel que lee sus datos de Resumen!E:F. */
    private fun xmlGrafico(g: GraficoReporte): String {
        val n = g.datos.size
        val ultima = n + 1
        val categorias = g.datos.mapIndexed { i, d -> "<c:pt idx=\"$i\"><c:v>${escapar(d.first)}</c:v></c:pt>" }.joinToString("")
        val valores = g.datos.mapIndexed { i, d -> "<c:pt idx=\"$i\"><c:v>${d.second}</c:v></c:pt>" }.joinToString("")

        val serie = "<c:ser><c:idx val=\"0\"/><c:order val=\"0\"/>" +
                "<c:tx><c:strRef><c:f>Resumen!\$F\$1</c:f><c:strCache><c:ptCount val=\"1\"/>" +
                "<c:pt idx=\"0\"><c:v>Unidades</c:v></c:pt></c:strCache></c:strRef></c:tx>" +
                (if (g.esPastel) "" else
                    "<c:spPr><a:solidFill><a:srgbClr val=\"1677FF\"/></a:solidFill></c:spPr><c:invertIfNegative val=\"0\"/>") +
                (if (g.esPastel)
                    "<c:dLbls><c:showLegendKey val=\"0\"/><c:showVal val=\"0\"/><c:showCatName val=\"0\"/>" +
                            "<c:showSerName val=\"0\"/><c:showPercent val=\"1\"/><c:showBubbleSize val=\"0\"/>" +
                            "<c:showLeaderLines val=\"1\"/></c:dLbls>"
                else "") +
                "<c:cat><c:strRef><c:f>Resumen!\$E\$2:\$E\$$ultima</c:f><c:strCache><c:ptCount val=\"$n\"/>$categorias</c:strCache></c:strRef></c:cat>" +
                "<c:val><c:numRef><c:f>Resumen!\$F\$2:\$F\$$ultima</c:f><c:numCache><c:formatCode>General</c:formatCode>" +
                "<c:ptCount val=\"$n\"/>$valores</c:numCache></c:numRef></c:val></c:ser>"

        val trazado = if (g.esPastel) {
            "<c:pieChart><c:varyColors val=\"1\"/>$serie<c:firstSliceAng val=\"0\"/></c:pieChart>"
        } else {
            "<c:barChart><c:barDir val=\"col\"/><c:grouping val=\"clustered\"/><c:varyColors val=\"0\"/>$serie" +
                    "<c:gapWidth val=\"80\"/><c:axId val=\"111111111\"/><c:axId val=\"222222222\"/></c:barChart>" +
                    "<c:catAx><c:axId val=\"111111111\"/><c:scaling><c:orientation val=\"minMax\"/></c:scaling>" +
                    "<c:delete val=\"0\"/><c:axPos val=\"b\"/><c:numFmt formatCode=\"General\" sourceLinked=\"0\"/>" +
                    "<c:majorTickMark val=\"out\"/><c:minorTickMark val=\"none\"/><c:tickLblPos val=\"nextTo\"/>" +
                    "<c:crossAx val=\"222222222\"/><c:crosses val=\"autoZero\"/><c:auto val=\"1\"/>" +
                    "<c:lblAlgn val=\"ctr\"/><c:lblOffset val=\"100\"/><c:noMultiLvlLbl val=\"0\"/></c:catAx>" +
                    "<c:valAx><c:axId val=\"222222222\"/><c:scaling><c:orientation val=\"minMax\"/></c:scaling>" +
                    "<c:delete val=\"0\"/><c:axPos val=\"l\"/><c:majorGridlines/>" +
                    "<c:numFmt formatCode=\"General\" sourceLinked=\"0\"/><c:majorTickMark val=\"out\"/>" +
                    "<c:minorTickMark val=\"none\"/><c:tickLblPos val=\"nextTo\"/><c:crossAx val=\"111111111\"/>" +
                    "<c:crosses val=\"autoZero\"/><c:crossBetween val=\"between\"/></c:valAx>"
        }

        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<c:chartSpace xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\" " +
                "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" " +
                "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                "<c:chart><c:title><c:tx><c:rich><a:bodyPr/><a:p><a:r><a:t>Unidades vendidas por producto</a:t></a:r></a:p>" +
                "</c:rich></c:tx><c:overlay val=\"0\"/></c:title><c:autoTitleDeleted val=\"0\"/>" +
                "<c:plotArea><c:layout/>$trazado</c:plotArea>" +
                (if (g.esPastel) "<c:legend><c:legendPos val=\"r\"/><c:overlay val=\"0\"/></c:legend>" else "") +
                "<c:plotVisOnly val=\"1\"/><c:dispBlanksAs val=\"gap\"/></c:chart></c:chartSpace>"
    }

    // ---------------------------------------------------------------- partes fijas del .xlsx

    private const val TIPOS_GRAFICO =
        "<Override PartName=\"/xl/drawings/drawing1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawing+xml\"/>" +
                "<Override PartName=\"/xl/charts/chart1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawingml.chart+xml\"/>"

    private const val RELS_HOJA_RESUMEN =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing\" Target=\"../drawings/drawing1.xml\"/>" +
                "</Relationships>"

    private const val RELS_DIBUJO =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/chart\" Target=\"../charts/chart1.xml\"/>" +
                "</Relationships>"

    // El gráfico se coloca a la derecha de los datos (columnas H a Q, filas 1 a 20)
    private const val DIBUJO =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<xdr:wsDr xmlns:xdr=\"http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing\" " +
                "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\">" +
                "<xdr:twoCellAnchor>" +
                "<xdr:from><xdr:col>7</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>0</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from>" +
                "<xdr:to><xdr:col>16</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>20</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:to>" +
                "<xdr:graphicFrame macro=\"\"><xdr:nvGraphicFramePr><xdr:cNvPr id=\"2\" name=\"Grafico 1\"/><xdr:cNvGraphicFramePr/></xdr:nvGraphicFramePr>" +
                "<xdr:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/></xdr:xfrm>" +
                "<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/chart\">" +
                "<c:chart xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\" " +
                "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" r:id=\"rId1\"/>" +
                "</a:graphicData></a:graphic></xdr:graphicFrame><xdr:clientData/></xdr:twoCellAnchor></xdr:wsDr>"

    private const val CONTENT_TYPES =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                "<Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
                "</Types>"

    private const val RELS_RAIZ =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                "</Relationships>"

    private const val WORKBOOK =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
                "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                "<sheets>" +
                "<sheet name=\"Ventas\" sheetId=\"1\" r:id=\"rId1\"/>" +
                "<sheet name=\"Resumen\" sheetId=\"2\" r:id=\"rId2\"/>" +
                "</sheets></workbook>"

    private const val WORKBOOK_RELS =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
                "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/>" +
                "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
                "</Relationships>"

    private const val STYLES =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
                "<numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"&quot;\$&quot;#,##0.00\"/></numFmts>" +
                "<fonts count=\"3\">" +
                "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
                "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font>" +
                "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
                "</fonts>" +
                "<fills count=\"3\">" +
                "<fill><patternFill patternType=\"none\"/></fill>" +
                "<fill><patternFill patternType=\"gray125\"/></fill>" +
                "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF1677FF\"/><bgColor indexed=\"64\"/></patternFill></fill>" +
                "</fills>" +
                "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
                "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
                "<cellXfs count=\"6\">" +
                "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
                "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\"/></xf>" +
                "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
                "<xf numFmtId=\"164\" fontId=\"2\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\" applyFont=\"1\"/>" +
                "<xf numFmtId=\"1\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
                "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" +
                "</cellXfs>" +
                "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
                "</styleSheet>"
}