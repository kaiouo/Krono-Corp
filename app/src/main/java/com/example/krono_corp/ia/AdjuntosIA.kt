package com.example.krono_corp.ia

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import com.example.krono_corp.reportes.PdfKrono
import com.example.krono_corp.reportes.VentasExcel

// ==============================================================
// Archivos adjuntos para los asistentes IA (gerente y dueño/supervisor).
//
//  - Lee el archivo que el usuario eligió y lo convierte a TEXTO
//    (la IA de Groq solo entiende texto).
//  - Arma el prompt para que la IA devuelva el archivo COMPLETO editado.
//  - Con ese texto genera un archivo nuevo "<nombre>_editado.<ext>".
//
// Formatos: txt, md, json, csv, tsv, xml, html, log, xlsx, docx y pdf.
// No necesita librerías extra (xlsx y docx se leen/escriben como zip+xml).
// ==============================================================

/** Archivo ya leído y convertido a texto, listo para mandarlo a la IA. */
data class ArchivoAdjunto(
    val nombre: String,
    val extension: String,
    val contenido: String,
    /** true = el archivo era muy largo y solo se leyó el inicio (no se puede editar completo) */
    val truncado: Boolean,
    /** Bytes del .xlsx original: se editan en su lugar para conservar formato, fórmulas y estilos */
    val original: ByteArray? = null,
    /** PDF que NO se generó en Krono: se rehace con el diseño de Krono (solo se conserva el texto) */
    val pdfExterno: Boolean = false
)

/** Respuesta de la IA separada en: texto para el chat + contenido del archivo editado (si hay). */
data class RespuestaConArchivo(
    val texto: String,
    val contenido: String?,
    /** true = la IA empezó a devolver el archivo pero la respuesta se cortó */
    val incompleto: Boolean
)

/** Resultado de guardar el archivo editado. cambios = lista legible de lo que cambió (null = no se puede saber). */
data class ResultadoEdicion(
    val archivo: File,
    val cambios: List<String>?,
    val conFormato: Boolean,
    /** Cambios pedidos por la IA que no se pudieron aplicar al Excel original */
    val avisos: List<String> = emptyList(),
    /** Por qué no se pudo conservar el formato (si pasó) */
    val motivo: String? = null,
    /** Aviso informativo para el usuario (por ejemplo, que un PDF ajeno se rehizo con el diseño de Krono) */
    val nota: String? = null
)

object AdjuntosIA {

    /** Máximo de caracteres que se mandan a la IA (súbelo o bájalo según tu plan de Groq). */
    const val MAX_CARACTERES = 12_000

    private const val MAX_BYTES = 5L * 1024 * 1024
    private const val LIMITE_LECTURA = MAX_CARACTERES * 2
    private const val MARCA_INICIO = "<<<ARCHIVO>>>"
    private const val MARCA_FIN = "<<<FIN>>>"
    private const val MARCA_HOJA = "### HOJA:"
    private const val PROMPT_PDF =
        "El PDF está en un Markdown simple: «# » título, «## » subtítulo, «> » línea secundaria, «**texto**» destacado, " +
                "tablas con «|» (primera fila = encabezado, luego «|---|---|»; una última fila que empieza con TOTAL es la de " +
                "totales), «[pastel] Nombre=valor; Nombre=valor» para el gráfico y «@tema …» en la primera línea. " +
                "Conserva esa sintaxis, el orden y la línea @tema. Si agregas filas, ponlas antes de la fila TOTAL y recalcula los totales."

    private val EXTENSIONES_TEXTO = setOf("txt", "md", "json", "csv", "tsv", "xml", "html", "log")
    private val SOPORTADOS = EXTENSIONES_TEXTO + setOf("xlsx", "docx", "pdf")
    private val ES_NUMERO = Regex("-?(0|[1-9][0-9]{0,14})(\\.[0-9]+)?")

    // -----------------------------------------------------------
    // Leer
    // -----------------------------------------------------------

    suspend fun leer(context: Context, uri: Uri): Result<ArchivoAdjunto> = withContext(Dispatchers.IO) {
        runCatching {
            val nombre = nombreDe(context, uri)
            val ext = nombre.substringAfterLast('.', "").lowercase()

            if (ext !in SOPORTADOS) {
                error(
                    "No puedo leer archivos .${ext.ifBlank { "?" }}. " +
                            "Formatos válidos: xlsx, docx, csv, txt, md, json."
                )
            }

            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("No pude abrir el archivo.")
            if (bytes.size > MAX_BYTES) error("El archivo pesa más de 5 MB. Sube uno más pequeño.")

            var pdfExterno = false
            val texto = try {
                when (ext) {
                    "xlsx" -> xlsxATexto(bytes)
                    "docx" -> docxATexto(bytes)
                    "pdf" -> {
                        val (md, esKrono) = PdfKrono.leerFuente(context, bytes)
                        pdfExterno = !esKrono
                        md
                    }
                    else -> String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
                }
            } catch (e: XmlPullParserException) {
                error("El archivo parece dañado o no es un .$ext válido.")
            } catch (e: Exception) {
                if (ext == "pdf") error("No pude leer el PDF (¿está protegido con contraseña o dañado?).") else throw e
            }
            if (texto.isBlank()) error("El archivo está vacío o no tiene contenido que pueda leer.")

            val truncado = texto.length > MAX_CARACTERES
            ArchivoAdjunto(
                nombre = nombre,
                extension = ext,
                contenido = if (truncado) texto.take(MAX_CARACTERES) else texto,
                truncado = truncado,
                original = if (ext == "xlsx") bytes else null,
                pdfExterno = pdfExterno
            )
        }
    }

    private fun nombreDe(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0) c.getString(i)?.let { return it }
                }
            }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "archivo"
    }

    // -----------------------------------------------------------
    // Prompt y respuesta
    // -----------------------------------------------------------

    fun construirPrompt(adj: ArchivoAdjunto, instruccion: String): String = buildString {
        appendLine("Eres Krono, el asistente de KronoCorp. El usuario adjuntó el archivo «${adj.nombre}».")
        appendLine(
            when (adj.extension) {
                "xlsx" -> "Sus hojas están en formato CSV; cada hoja empieza con una línea «$MARCA_HOJA nombre». Conserva esas líneas. " +
                        "Si hay una fila TOTAL déjala siempre al final (agrega las filas nuevas antes de ella) y recalcula sus valores."
                "pdf" -> PROMPT_PDF
                "csv", "tsv" -> "El contenido está en formato CSV (valores separados por comas)."
                "docx" -> "Cada línea de texto es un párrafo del documento."
                else -> "El contenido es texto plano."
            }
        )
        if (adj.truncado) {
            appendLine(
                "AVISO: el archivo es largo y solo se muestra el inicio. NO devuelvas el archivo modificado; " +
                        "limítate a analizarlo o a responder la pregunta, y avisa que para editarlo debe subirlo más corto."
            )
        }
        appendLine()
        appendLine("--- INICIO DEL ARCHIVO ---")
        appendLine(adj.contenido)
        appendLine("--- FIN DEL ARCHIVO ---")
        appendLine()
        appendLine("Petición del usuario: $instruccion")
        appendLine()
        appendLine("Reglas:")
        appendLine("1. Responde siempre en español.")
        appendLine(
            "2. Si el usuario pide MODIFICAR el archivo (cambiar, corregir, agregar, eliminar, reordenar, " +
                    "traducir, calcular, dar formato...), responde con una o dos frases sobre lo que hiciste y " +
                    "luego el archivo COMPLETO ya modificado entre las marcas $MARCA_INICIO y $MARCA_FIN, " +
                    "en el mismo formato que recibiste, sin omitir filas ni líneas y sin ``` dentro de las marcas."
        )
        appendLine("3. Si solo pregunta o pide un análisis o resumen, responde normal SIN usar las marcas.")
        appendLine("4. No inventes datos que no estén en el archivo.")
        appendLine(
            "5. Interpreta la petición con criterio, aunque sea informal (por ejemplo «ponle IVA», " +
                    "«quita los duplicados», «ordénalo por total»): calcula los valores necesarios y aplica los " +
                    "cambios directamente, sin pedir confirmación ni aclaraciones."
        )
        appendLine("6. Lo que no te pidieron cambiar, devuélvelo tal cual estaba.")
    }.trim()

    fun separarRespuesta(respuesta: String): RespuestaConArchivo {
        val ini = respuesta.indexOf(MARCA_INICIO)
        if (ini < 0) return RespuestaConArchivo(respuesta.trim(), null, false)

        val desde = ini + MARCA_INICIO.length
        val fin = respuesta.indexOf(MARCA_FIN, desde)
        val antes = respuesta.substring(0, ini)

        if (fin < 0) {
            // Se cortó antes de terminar: guardar eso borraría datos del usuario.
            return RespuestaConArchivo(antes.trim(), null, true)
        }
        val despues = respuesta.substring(fin + MARCA_FIN.length)
        val contenido = quitarCercas(respuesta.substring(desde, fin))
        return RespuestaConArchivo("$antes\n$despues".trim(), contenido.ifBlank { null }, false)
    }

    private fun quitarCercas(s: String): String {
        var t = s.trim()
        if (t.startsWith("```")) t = t.substringAfter('\n', "")
        if (t.endsWith("```")) t = t.removeSuffix("```")
        return t.trim('\n', '\r')
    }

    // -----------------------------------------------------------
    // Guardar el archivo editado
    // -----------------------------------------------------------

    private val VERBOS_EDICION = listOf(
        "cambi", "modific", "edit", "corrig", "agreg", "anad", "elimin", "borr", "actualiz",
        "reemplaz", "calcul", "ordena", "traduc", "pon ", "poner", "quita", "inserta", "renombr",
        "sustituy", "completa", "llena", "suma", "multiplic", "aplica", "formatea", "limpia"
    )

    /** ¿La petición suena a «modifica el archivo» (y no solo a una pregunta)? */
    fun pareceEdicion(instruccion: String): Boolean {
        val t = java.text.Normalizer.normalize(instruccion.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "").replace("ñ", "n")
        return VERBOS_EDICION.any { it in t }
    }

    /** Segundo intento cuando la IA dijo que editó pero no devolvió el archivo. */
    fun construirReintento(adj: ArchivoAdjunto, instruccion: String): String =
        construirPrompt(adj, instruccion) + "\n\n" +
                "IMPORTANTE: tu respuesta anterior NO incluyó el archivo. Devuelve AHORA el archivo COMPLETO " +
                "ya modificado entre $MARCA_INICIO y $MARCA_FIN, sin explicaciones adicionales."

    /** Texto para el chat con lo que realmente cambió en el archivo. */
    fun resumenCambios(res: ResultadoEdicion): String {
        if (!res.conFormato) {
            return "\n\n⚠️ No pude editar el Excel original, así que se guardó una copia sin el formato." +
                    (res.motivo?.let { "\nMotivo: $it" } ?: "")
        }
        val c = res.cambios
        return buildString {
            if (c != null) {
                append("\n\n✅ Cambios aplicados en el archivo: ${c.size}")
                c.take(8).forEach { append("\n• ").append(it) }
                if (c.size > 8) append("\n• …y ${c.size - 8} más")
                if (res.avisos.isNotEmpty()) {
                    append("\n\n⚠️ Estos cambios no se pudieron aplicar:")
                    res.avisos.take(5).forEach { append("\n• ").append(it) }
                    if (res.avisos.size > 5) append("\n• …y ${res.avisos.size - 5} más")
                }
            }
            res.nota?.let { append("\n\nℹ️ ").append(it) }
        }
    }

    private fun corto(s: String): String {
        val t = s.replace('\n', ' ')
        return if (t.length > 28) t.take(28) + "…" else t
    }

    /** Diferencias entre dos textos, línea por línea (para PDF). Lista vacía = idénticos. */
    private fun cambiosTexto(antes: String, despues: String): List<String> {
        val a = antes.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val b = despues.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val quitadas = a.toMutableList().also { l -> b.forEach { l.remove(it) } }
        val agregadas = b.toMutableList().also { l -> a.forEach { l.remove(it) } }
        return quitadas.map { "Quitado: «${corto(it)}»" } + agregadas.map { "Agregado: «${corto(it)}»" }
    }

    /** Guarda el archivo editado y calcula qué cambió realmente. */
    fun editar(context: Context, original: ArchivoAdjunto, contenido: String): ResultadoEdicion {
        val dir = File(context.cacheDir, "reportes").apply { mkdirs() }
        val base = original.nombre.substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9_\\- ]"), "_")
            .ifBlank { "archivo" }
        val sello = System.currentTimeMillis() % 100_000
        val archivo = File(dir, "${base}_editado_$sello.${original.extension}")

        var cambios: List<String>? = null
        var conFormato = true
        var motivo: String? = null
        var nota: String? = null
        val avisos = mutableListOf<String>()
        when (original.extension) {
            "xlsx" -> {
                val lista = mutableListOf<String>()
                var bytes: ByteArray? = null
                if (original.original == null) {
                    motivo = "no tengo el archivo original"
                } else {
                    try {
                        bytes = editarXlsxOriginal(original.original, contenido, lista, avisos)
                    } catch (e: Exception) {
                        motivo = "${e.javaClass.simpleName}: ${e.message}"
                    }
                }
                if (bytes != null) {
                    cambios = lista
                    archivo.writeBytes(bytes)
                } else {
                    conFormato = false
                    avisos.clear()
                    // Plan B: sin el original, pero con el diseño de Krono (nunca sin color)
                    archivo.writeBytes(VentasExcel.desdeTabla(hojasDeTexto(contenido)))
                }
            }
            "pdf" -> {
                PdfKrono.generar(context, contenido, archivo)
                cambios = cambiosTexto(original.contenido, contenido)
                if (original.pdfExterno) {
                    nota = "Tu PDF no venía de Krono, así que lo rehice con el diseño de Krono " +
                            "(se conserva el texto y las tablas, no el diseño original)."
                }
            }
            "docx" -> {
                if (contenido.trim() == original.contenido.trim()) cambios = emptyList()
                archivo.writeBytes(textoADocx(contenido))
            }
            else -> {
                if (contenido.trim() == original.contenido.trim()) cambios = emptyList()
                archivo.writeText(contenido, Charsets.UTF_8)
            }
        }
        return ResultadoEdicion(archivo, cambios, conFormato, avisos, motivo, nota)
    }

    /** Compatibilidad: guarda el archivo editado y devuelve solo el File. */
    fun guardarEditado(context: Context, original: ArchivoAdjunto, contenido: String): File =
        editar(context, original, contenido).archivo

    fun etiquetaTipo(archivo: File): String = when (archivo.extension.lowercase()) {
        "pdf" -> "PDF"
        "xlsx", "xls" -> "Excel"
        "docx" -> "Word"
        "csv" -> "CSV"
        else -> "archivo"
    }

    // -----------------------------------------------------------
    // ZIP / XML
    // -----------------------------------------------------------

    private fun leerZip(bytes: ByteArray, quiero: (String) -> Boolean): Map<String, ByteArray> {
        val salida = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var e: ZipEntry? = zip.nextEntry
            while (e != null) {
                if (!e.isDirectory && quiero(e.name)) salida[e.name] = zip.readBytes()
                e = zip.nextEntry
            }
        }
        return salida
    }

    private fun parser(data: ByteArray): XmlPullParser =
        Xml.newPullParser().apply { setInput(ByteArrayInputStream(data), "UTF-8") }

    private fun ZipOutputStream.agregar(nombre: String, contenido: String) {
        putNextEntry(ZipEntry(nombre))
        write(contenido.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun xml(s: String): String =
        s.replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "")
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    // -----------------------------------------------------------
    // XLSX -> texto (CSV por hoja)
    // -----------------------------------------------------------

    private fun xlsxATexto(bytes: ByteArray): String {
        val z = leerZip(bytes) { n ->
            n == "xl/workbook.xml" || n == "xl/_rels/workbook.xml.rels" || n == "xl/sharedStrings.xml" ||
                    (n.startsWith("xl/worksheets/") && n.endsWith(".xml"))
        }
        if (z.isEmpty()) {
            error("No es un .xlsx válido. Si es un .xls antiguo, guárdalo como .xlsx desde Excel y vuelve a subirlo.")
        }
        val compartidas = textosCompartidos(z["xl/sharedStrings.xml"])

        val hojas = hojasDeXlsx(z)

        val sb = StringBuilder()
        for ((nombre, ruta) in hojas) {
            val datos = z[ruta] ?: continue
            sb.append(MARCA_HOJA).append(' ').append(nombre).append('\n')
            sb.append(hojaATexto(datos, compartidas))
            if (sb.length > LIMITE_LECTURA) break
        }
        return sb.toString().trim()
    }

    /** Lista (nombre de hoja, ruta dentro del zip) en el orden del libro. */
    private fun hojasDeXlsx(z: Map<String, ByteArray>): List<Pair<String, String>> {
        val destinos = HashMap<String, String>()
        z["xl/_rels/workbook.xml.rels"]?.let { data ->
            val p = parser(data)
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && p.name == "Relationship") {
                    val id = p.getAttributeValue(null, "Id")
                    val target = p.getAttributeValue(null, "Target")
                    if (id != null && target != null) {
                        destinos[id] = if (target.startsWith("/")) target.trimStart('/') else "xl/$target"
                    }
                }
                ev = p.next()
            }
        }

        val hojas = mutableListOf<Pair<String, String>>() // nombre -> ruta dentro del zip
        z["xl/workbook.xml"]?.let { data ->
            val p = parser(data)
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && p.name == "sheet") {
                    val nombre = p.getAttributeValue(null, "name") ?: "Hoja"
                    val ruta = destinos[p.getAttributeValue(null, "r:id") ?: ""]
                    if (ruta != null) hojas.add(nombre to ruta)
                }
                ev = p.next()
            }
        }
        if (hojas.isEmpty()) {
            z.keys.filter { it.startsWith("xl/worksheets/") }.sorted()
                .forEachIndexed { i, ruta -> hojas.add("Hoja${i + 1}" to ruta) }
        }
        return hojas
    }

    private fun textosCompartidos(data: ByteArray?): List<String> {
        if (data == null) return emptyList()
        val lista = mutableListOf<String>()
        val p = parser(data)
        val sb = StringBuilder()
        var enSi = false
        var enT = false
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "si" -> {
                        enSi = true
                        sb.setLength(0)
                    }
                    "t" -> enT = true
                }
                XmlPullParser.TEXT -> if (enSi && enT) sb.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "t" -> enT = false
                    "si" -> {
                        lista.add(sb.toString())
                        enSi = false
                    }
                }
            }
            ev = p.next()
        }
        return lista
    }

    private fun hojaATexto(data: ByteArray, compartidas: List<String>): String {
        val salida = StringBuilder()
        val p = parser(data)
        var fila = mutableListOf<String>()
        var tipo = ""
        var idx = -1
        var enCelda = false
        var enV = false
        var enT = false
        val valor = StringBuilder()
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT && salida.length < LIMITE_LECTURA) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> fila = mutableListOf()
                    "c" -> {
                        enCelda = true
                        valor.setLength(0)
                        tipo = p.getAttributeValue(null, "t") ?: ""
                        idx = columnaDe(p.getAttributeValue(null, "r"))
                    }
                    "v" -> enV = true
                    "t" -> enT = true
                }
                XmlPullParser.TEXT -> if (enCelda && (enV || enT)) valor.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "v" -> enV = false
                    "t" -> enT = false
                    "c" -> {
                        val txt = when (tipo) {
                            "s" -> compartidas.getOrElse(valor.toString().trim().toIntOrNull() ?: -1) { "" }
                            "b" -> if (valor.toString().trim() == "1") "TRUE" else "FALSE"
                            else -> valor.toString()
                        }
                        if (idx < 0) {
                            fila.add(txt)
                        } else {
                            while (fila.size < idx) fila.add("")
                            if (fila.size == idx) fila.add(txt) else fila[idx] = txt
                        }
                        enCelda = false
                    }
                    "row" -> if (fila.any { it.isNotEmpty() }) {
                        salida.append(fila.joinToString(",") { csvEscapar(it) }).append('\n')
                    }
                }
            }
            ev = p.next()
        }
        return salida.toString()
    }

    /** "B3" -> 1 (índice de columna base 0). -1 si no se puede calcular. */
    private fun columnaDe(ref: String?): Int {
        if (ref == null) return -1
        var n = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') n = n * 26 + (ch - 'A' + 1) else break
        }
        return n - 1
    }

    private fun csvEscapar(v: String): String =
        if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + v.replace("\"", "\"\"") + "\""
        } else v

    // -----------------------------------------------------------
    // XLSX: editar el archivo ORIGINAL (conserva formato, estilos y fórmulas)
    // -----------------------------------------------------------

    /**
     * Toma el .xlsx original y solo cambia las celdas cuyo valor cambió en el texto editado.
     * Colores, anchos, bordes, fórmulas, imágenes y gráficos que no se tocaron se quedan igual.
     * Las filas se emparejan por posición (solo cuentan las filas que tienen datos).
     */
    private fun editarXlsxOriginal(
        original: ByteArray,
        texto: String,
        cambios: MutableList<String>,
        avisos: MutableList<String>
    ): ByteArray {
        val z = leerZip(original) { true }
        val compartidas = textosCompartidos(z["xl/sharedStrings.xml"])
        val hojasOrig = hojasDeXlsx(z)
        val editadas = hojasDeTexto(texto)
        val salida = LinkedHashMap<String, ByteArray>(z)

        hojasOrig.forEachIndexed { i, (nombre, ruta) ->
            val e = editadas.firstOrNull { it.first.trim().equals(nombre.trim(), ignoreCase = true) }
                ?: editadas.getOrNull(i)?.takeIf { editadas.size == hojasOrig.size }
            val datos = z[ruta]
            if (e != null && datos != null) {
                val parchada = parchearHoja(datos, compartidas, e.second, nombre, cambios)
                // Verificación: la hoja guardada debe contener EXACTAMENTE lo que devolvió la IA
                val releida = normalizarFilas(parseCsv(hojaATexto(parchada, compartidas)))
                avisos.addAll(diferencias(nombre, releida, normalizarFilas(e.second)))
                salida[ruta] = parchada
            }
        }

        // Si no se aplicó NADA pero la IA sí pidió diferencias, el original no sirve: se usa el plan B
        if (avisos.isNotEmpty() && cambios.isEmpty()) {
            throw IllegalStateException("no se pudo aplicar ningún cambio sobre el original (${avisos.first()})")
        }

        // Que Excel recalcule las fórmulas al abrir (los resultados guardados pueden haber cambiado)
        z["xl/workbook.xml"]?.let { wb ->
            val doc = parseDom(wb)
            val raiz = doc.documentElement
            var calc = doc.getElementsByTagName("calcPr").item(0) as? Element
            if (calc == null) {
                // Si el libro no tiene calcPr (como el que genera VentasExcel) se crea, en el lugar que exige el esquema
                calc = doc.createElement("calcPr")
                val despues = listOf(
                    "oleSize", "customWorkbookViews", "pivotCaches", "smartTagPr", "smartTagTypes",
                    "webPublishing", "fileRecoveryPr", "webPublishObjects", "extLst"
                )
                val referencia = despues.firstNotNullOfOrNull { hijos(raiz, it).firstOrNull() }
                if (referencia != null) raiz.insertBefore(calc, referencia) else raiz.appendChild(calc)
            }
            calc.setAttribute("fullCalcOnLoad", "1")
            salida["xl/workbook.xml"] = domABytes(doc)
        }

        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zip ->
            for ((nombre, contenido) in salida) {
                zip.putNextEntry(ZipEntry(nombre))
                zip.write(contenido)
                zip.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    private fun normalizarFilas(filas: List<List<String>>): List<List<String>> =
        filas.map { f -> f.dropLastWhile { it.isEmpty() } }.filter { it.isNotEmpty() }

    private fun equivalentes(a: List<List<String>>, b: List<List<String>>): Boolean {
        if (a.size != b.size) return false
        for (i in a.indices) {
            val x = a[i]
            val y = b[i]
            if (x.size != y.size) return false
            for (j in x.indices) if (!iguales(x[j], y[j])) return false
        }
        return true
    }

    /** Diferencias entre lo que la IA pidió (b) y lo que quedó en la hoja guardada (a). */
    private fun diferencias(hoja: String, a: List<List<String>>, b: List<List<String>>): List<String> {
        val dif = mutableListOf<String>()
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { emptyList() }
            val y = b.getOrElse(i) { emptyList() }
            for (j in 0 until maxOf(x.size, y.size)) {
                val vx = x.getOrElse(j) { "" }
                val vy = y.getOrElse(j) { "" }
                if (!iguales(vx, vy)) {
                    dif.add("$hoja, fila ${i + 1}, columna ${letraColumna(j)}: pedido «${corto(vy)}», quedó «${corto(vx)}»")
                }
            }
        }
        return dif
    }

    private fun parseDom(data: ByteArray): Document {
        val f = DocumentBuilderFactory.newInstance()
        f.isNamespaceAware = false
        return f.newDocumentBuilder().parse(ByteArrayInputStream(data))
    }

    private fun domABytes(doc: Document): ByteArray {
        val out = ByteArrayOutputStream()
        val t = TransformerFactory.newInstance().newTransformer()
        t.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        t.transform(DOMSource(doc), StreamResult(out))
        return out.toByteArray()
    }

    private fun hijos(e: Element, nombre: String): List<Element> {
        val r = mutableListOf<Element>()
        var n = e.firstChild
        while (n != null) {
            if (n is Element && n.tagName == nombre) r.add(n)
            n = n.nextSibling
        }
        return r
    }

    private fun textoCelda(c: Element, compartidas: List<String>): String {
        val v = hijos(c, "v").firstOrNull()?.textContent ?: ""
        return when (c.getAttribute("t")) {
            "s" -> compartidas.getOrElse(v.trim().toIntOrNull() ?: -1) { "" }
            "b" -> if (v.trim() == "1") "TRUE" else "FALSE"
            "inlineStr" -> hijos(c, "is").firstOrNull()?.let { esta ->
                val ts = esta.getElementsByTagName("t")
                buildString { for (k in 0 until ts.length) append(ts.item(k).textContent) }
            } ?: ""
            else -> v
        }
    }

    private fun iguales(a: String, b: String): Boolean {
        if (a == b) return true
        val x = a.trim().toDoubleOrNull()
        val y = b.trim().toDoubleOrNull()
        return x != null && y != null && x == y
    }

    private fun sinControl(s: String) =
        s.replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "")

    private fun ponerValor(doc: Document, c: Element, valor: String) {
        for (h in hijos(c, "f") + hijos(c, "v") + hijos(c, "is")) c.removeChild(h)
        c.removeAttribute("t")
        val limpio = sinControl(valor)
        when {
            limpio.isEmpty() -> {}
            ES_NUMERO.matches(limpio.trim()) -> {
                val v = doc.createElement("v")
                v.textContent = limpio.trim()
                c.appendChild(v)
            }
            else -> {
                c.setAttribute("t", "inlineStr")
                val isEl = doc.createElement("is")
                val t = doc.createElement("t")
                t.setAttribute("xml:space", "preserve")
                t.textContent = limpio
                isEl.appendChild(t)
                c.appendChild(isEl)
            }
        }
    }

    private fun parchearFila(
        doc: Document,
        fila: Element,
        valores: List<String>,
        compartidas: List<String>,
        proto: Element?,
        hoja: String,
        cambios: MutableList<String>,
        conservarFormulas: Boolean = false
    ) {
        val celdas = hijos(fila, "c")
        val porCol = HashMap<Int, Element>()
        celdas.forEachIndexed { k, c ->
            val idx = columnaDe(c.getAttribute("r"))
            porCol[if (idx < 0) k else idx] = c
        }
        val numFila = fila.getAttribute("r")
        val maxCol = maxOf(valores.size, (porCol.keys.maxOrNull() ?: -1) + 1)

        for (j in 0 until maxCol) {
            val nuevo = valores.getOrElse(j) { "" }
            val c = porCol[j]
            if (c == null) {
                if (nuevo.isEmpty()) continue
                val celda = doc.createElement("c")
                celda.setAttribute("r", letraColumna(j) + numFila)
                // Estilo: el de la misma columna en la fila de referencia; si no hay, el de la celda de la izquierda
                // (así una columna nueva hereda el encabezado azul, el formato de dinero o la negrita del TOTAL)
                val estilo = proto?.let { p ->
                    hijos(p, "c").firstOrNull { columnaDe(it.getAttribute("r")) == j }?.getAttribute("s")
                }?.takeIf { it.isNotEmpty() }
                    ?: porCol[j - 1]?.getAttribute("s")?.takeIf { it.isNotEmpty() }
                if (estilo != null) celda.setAttribute("s", estilo)
                val siguiente = celdas.firstOrNull { columnaDe(it.getAttribute("r")) > j }
                if (siguiente != null) fila.insertBefore(celda, siguiente) else fila.appendChild(celda)
                porCol[j] = celda
                ponerValor(doc, celda, nuevo)
                fila.removeAttribute("spans")
                cambios.add("$hoja!${letraColumna(j)}$numFila: «${corto(nuevo)}» (nueva)")
            } else {
                val viejo = textoCelda(c, compartidas)
                if (iguales(viejo, nuevo)) continue                                   // sin cambios: no se toca
                if (hijos(c, "f").any { it.getAttribute("t") == "shared" && it.hasAttribute("ref") }) continue // maestra de fórmula compartida
                val formula = hijos(c, "f").firstOrNull()
                if (conservarFormulas && formula != null && ES_NUMERO.matches(nuevo.trim())) {
                    // Fila TOTAL: la fórmula SUM se queda y solo se actualiza el resultado guardado
                    val v = hijos(c, "v").firstOrNull() ?: doc.createElement("v").also { c.appendChild(it) }
                    v.textContent = nuevo.trim()
                } else {
                    ponerValor(doc, c, nuevo)
                }
                val ref = c.getAttribute("r").ifEmpty { letraColumna(j) + numFila }
                cambios.add("$hoja!$ref: «${corto(viejo)}» → «${corto(nuevo)}»")
            }
        }
    }

    private fun primeraCelda(fila: Element, compartidas: List<String>): String {
        val c = hijos(fila, "c").firstOrNull { columnaDe(it.getAttribute("r")) <= 0 } ?: return ""
        return textoCelda(c, compartidas)
    }

    private fun renumerar(fila: Element, nuevo: Int) {
        fila.setAttribute("r", nuevo.toString())
        for (c in hijos(fila, "c")) {
            val letras = c.getAttribute("r").takeWhile { it.isLetter() }
            if (letras.isNotEmpty()) c.setAttribute("r", letras + nuevo)
        }
    }

    /** Si la hoja tiene columnas nuevas, les da ancho (el de la última columna definida). */
    private fun asegurarAnchos(doc: Document, columnas: Int) {
        val cols = doc.getElementsByTagName("cols").item(0) as? Element ?: return
        val existentes = hijos(cols, "col")
        val cubierto = existentes.maxOfOrNull { it.getAttribute("max").toIntOrNull() ?: 0 } ?: 0
        if (columnas > cubierto) {
            val ancho = existentes.lastOrNull()?.getAttribute("width")?.takeIf { it.isNotEmpty() } ?: "14"
            val col = doc.createElement("col")
            col.setAttribute("min", (cubierto + 1).toString())
            col.setAttribute("max", columnas.toString())
            col.setAttribute("width", ancho)
            col.setAttribute("customWidth", "1")
            cols.appendChild(col)
        }
    }

    /**
     * Hoja con fila TOTAL (como las de VentasExcel): las filas nuevas se insertan ANTES del TOTAL,
     * el TOTAL baja conservando su negrita, su formato y sus fórmulas SUM (con el rango ampliado).
     */
    private fun parchearConTotal(
        doc: Document,
        sheetData: Element,
        todas: List<Element>,
        noVacias: List<Element>,
        totalOrig: Element,
        filas: List<List<String>>,
        totalNueva: List<String>,
        compartidas: List<String>,
        hoja: String,
        cambios: MutableList<String>
    ) {
        val datosOrig = noVacias.dropLast(1)
        val datosNuevos = filas.dropLast(1)
        val proto = datosOrig.lastOrNull()
        val r0 = totalOrig.getAttribute("r").toIntOrNull() ?: (todas.indexOf(totalOrig) + 1)
        val extra = maxOf(0, datosNuevos.size - datosOrig.size)

        // 1) Hacer sitio: el TOTAL (y lo que haya debajo) baja tantas filas como filas nuevas
        if (extra > 0) {
            for (fila in todas) {
                val r = fila.getAttribute("r").toIntOrNull() ?: continue
                if (r >= r0) renumerar(fila, r + extra)
            }
            val rango = Regex("([A-Z]+)(\\d+):([A-Z]+)(\\d+)")
            for (c in hijos(totalOrig, "c")) {
                for (f in hijos(c, "f")) {
                    f.textContent = f.textContent.replace(rango) { m ->
                        if (m.groupValues[4].toIntOrNull() == r0 - 1) {
                            "${m.groupValues[1]}${m.groupValues[2]}:${m.groupValues[3]}${r0 - 1 + extra}"
                        } else m.value
                    }
                }
            }
        }

        // 2) Filas de datos: por posición; las que sobran son filas nuevas, con el estilo de la última fila de datos
        datosNuevos.forEachIndexed { i, valores ->
            val existente = datosOrig.getOrNull(i)
            if (existente != null) {
                parchearFila(doc, existente, valores, compartidas, null, hoja, cambios)
            } else {
                val nueva = doc.createElement("row")
                nueva.setAttribute("r", (r0 + (i - datosOrig.size)).toString())
                sheetData.insertBefore(nueva, totalOrig)
                parchearFila(doc, nueva, valores, compartidas, proto, hoja, cambios)
            }
        }
        // Filas que la IA quitó: se vacían (se conserva el formato)
        for (i in datosNuevos.size until datosOrig.size) {
            parchearFila(doc, datosOrig[i], emptyList(), compartidas, null, hoja, cambios)
        }

        // 3) Fila TOTAL: conserva fórmulas y estilo
        parchearFila(doc, totalOrig, totalNueva, compartidas, null, hoja, cambios, conservarFormulas = true)
    }

    private fun parchearHoja(
        data: ByteArray,
        compartidas: List<String>,
        filas: List<List<String>>,
        hoja: String,
        cambios: MutableList<String>
    ): ByteArray {
        val doc = parseDom(data)
        val sheetData = doc.getElementsByTagName("sheetData").item(0) as? Element ?: return data
        val todas = hijos(sheetData, "row")
        val noVacias = todas.filter { fila -> hijos(fila, "c").any { textoCelda(it, compartidas).isNotEmpty() } }

        val totalOrig = noVacias.lastOrNull()
            ?.takeIf { primeraCelda(it, compartidas).trim().equals("TOTAL", ignoreCase = true) }
        val totalNueva = filas.lastOrNull()
            ?.takeIf { it.firstOrNull()?.trim().equals("TOTAL", ignoreCase = true) }

        if (totalOrig != null && totalNueva != null) {
            parchearConTotal(doc, sheetData, todas, noVacias, totalOrig, filas, totalNueva, compartidas, hoja, cambios)
        } else {
            var maxR = maxOf(todas.maxOfOrNull { it.getAttribute("r").toIntOrNull() ?: 0 } ?: 0, todas.size)
            // Fila de referencia para dar estilo a las filas nuevas (la penúltima suele ser una fila de datos)
            val proto = noVacias.getOrNull(noVacias.size - 2) ?: noVacias.lastOrNull()

            filas.forEachIndexed { i, valores ->
                val existente = noVacias.getOrNull(i)
                if (existente != null) {
                    parchearFila(doc, existente, valores, compartidas, null, hoja, cambios)
                } else {
                    maxR++
                    val nueva = doc.createElement("row")
                    nueva.setAttribute("r", maxR.toString())
                    sheetData.appendChild(nueva)
                    parchearFila(doc, nueva, valores, compartidas, proto, hoja, cambios)
                }
            }
            // Filas que la IA quitó: se vacían (se conserva el formato)
            for (i in filas.size until noVacias.size) {
                parchearFila(doc, noVacias[i], emptyList(), compartidas, null, hoja, cambios)
            }
        }

        asegurarAnchos(doc, filas.maxOfOrNull { it.size } ?: 0)
        doc.getElementsByTagName("dimension").item(0)?.let { it.parentNode.removeChild(it) }
        return domABytes(doc)
    }

    // -----------------------------------------------------------
    // texto (CSV por hoja) -> XLSX
    // -----------------------------------------------------------

    private fun hojasDeTexto(texto: String): List<Pair<String, List<List<String>>>> {
        val bloques = mutableListOf<Pair<String, MutableList<String>>>()
        for (linea in texto.lines()) {
            if (linea.trimStart().startsWith(MARCA_HOJA)) {
                bloques.add(linea.trimStart().removePrefix(MARCA_HOJA).trim() to mutableListOf())
            } else {
                if (bloques.isEmpty()) bloques.add("Hoja1" to mutableListOf())
                bloques.last().second.add(linea)
            }
        }
        return bloques.map { (nombre, lineas) -> nombre to parseCsv(lineas.joinToString("\n")) }
    }

    private fun parseCsv(s: String): List<List<String>> {
        val filas = mutableListOf<List<String>>()
        var fila = mutableListOf<String>()
        val celda = StringBuilder()
        var entreComillas = false
        var hayDatos = false
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (entreComillas) {
                if (ch == '"') {
                    if (i + 1 < s.length && s[i + 1] == '"') {
                        celda.append('"')
                        i++
                    } else {
                        entreComillas = false
                    }
                } else {
                    celda.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> {
                        entreComillas = true
                        hayDatos = true
                    }
                    ',' -> {
                        fila.add(celda.toString())
                        celda.setLength(0)
                        hayDatos = true
                    }
                    '\r' -> {}
                    '\n' -> {
                        if (hayDatos || celda.isNotEmpty()) {
                            fila.add(celda.toString())
                            filas.add(fila)
                        }
                        fila = mutableListOf()
                        celda.setLength(0)
                        hayDatos = false
                    }
                    else -> {
                        celda.append(ch)
                        hayDatos = true
                    }
                }
            }
            i++
        }
        if (hayDatos || celda.isNotEmpty()) {
            fila.add(celda.toString())
            filas.add(fila)
        }
        return filas
    }

    private fun letraColumna(indice: Int): String {
        var n = indice + 1
        val sb = StringBuilder()
        while (n > 0) {
            val r = (n - 1) % 26
            sb.insert(0, 'A' + r)
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    // -----------------------------------------------------------
    // DOCX <-> texto (un párrafo por línea)
    // -----------------------------------------------------------

    private fun docxATexto(bytes: ByteArray): String {
        val data = leerZip(bytes) { it == "word/document.xml" }["word/document.xml"]
            ?: error("No es un .docx válido.")
        val p = parser(data)
        val sb = StringBuilder()
        var enT = false
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT && sb.length < LIMITE_LECTURA) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "w:t" -> enT = true
                    "w:tab" -> sb.append('\t')
                    "w:br" -> sb.append('\n')
                }
                XmlPullParser.TEXT -> if (enT) sb.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "w:t" -> enT = false
                    "w:p" -> sb.append('\n')
                }
            }
            ev = p.next()
        }
        return sb.toString().trim()
    }

    private fun textoADocx(texto: String): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            z.agregar(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>"""
            )
            z.agregar(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""
            )
            val parrafos = texto.lines().joinToString("") { linea ->
                if (linea.isEmpty()) "<w:p/>"
                else "<w:p><w:r><w:t xml:space=\"preserve\">${xml(linea)}</w:t></w:r></w:p>"
            }
            z.agregar(
                "word/document.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$parrafos</w:body></w:document>"""
            )
        }
        return bos.toByteArray()
    }
}