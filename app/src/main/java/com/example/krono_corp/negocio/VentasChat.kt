package com.example.krono_corp.negocio

import android.content.Context
import com.example.krono_corp.data.AppUser
import com.example.krono_corp.data.AuthRepository
import com.example.krono_corp.data.GraficoReporte
import com.example.krono_corp.data.NuevaVenta
import com.example.krono_corp.data.PreguntaVenta
import com.example.krono_corp.data.ReportesRepository
import com.example.krono_corp.data.Rol
import com.example.krono_corp.data.SucursalesRepository
import com.example.krono_corp.data.VentaRegistrada
import com.example.krono_corp.data.VentasRepository
import com.example.krono_corp.data.fechaHora
import com.example.krono_corp.ia.GestorIA
import com.example.krono_corp.ia.GroqService
import com.example.krono_corp.reportes.NotificacionesReporte
import com.example.krono_corp.reportes.VentasExcel
import com.example.krono_corp.reportes.VentasPdf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// ==============================================================
// Cerebro de ventas del chat (SUPERVISOR y DUENO):
//   - registrar una venta con preguntas guiadas
//   - consultar ventas (la app calcula las cifras, Groq las redacta)
//   - exportar a Excel o PDF (y avisar para guardarlo en el historial de reportes)
//   - eliminar una venta
// ==============================================================

class RespuestaChat(
    val texto: String,
    val archivos: List<File> = emptyList(),
    val grafico: GraficoReporte? = null
)

class ReporteVentas(
    val titulo: String,
    val contenido: String,
    val datosGrafico: List<Pair<String, Int>>
)

private val METODOS_PAGO = listOf("Efectivo", "Tarjeta", "Transferencia", "Otro")
private val formatoVista = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
private val LOCALE_ES = Locale.forLanguageTag("es-SV")

private enum class Formato { EXCEL, PDF }

private class FlujoVenta(val pasos: List<PreguntaVenta>) {
    var indice = 0
    var confirmando = false
    var producto = ""
    var cantidad = 0
    var precio = 0.0
    var descuento = 0.0
    var descuentoTexto = ""
    var sucursal = ""
    var sucursalId = ""
    var sucursalFija = false   // el supervisor registra siempre en su sucursal asignada
    var metodoPago = ""
    var nota = ""
    val extras = LinkedHashMap<String, String>()

    val subtotal: Double get() = cantidad * precio
    val total: Double get() = (subtotal - descuento).coerceAtLeast(0.0)
}

private data class Filtros(
    val desde: LocalDateTime?,
    val hasta: LocalDateTime?,
    val periodo: String,
    val producto: String?,
    val sucursal: String?
) {
    fun descripcion(): String = buildString {
        append(periodo)
        if (producto != null) append(" · producto $producto")
        if (sucursal != null) append(" · $sucursal")
    }
}

private sealed interface Estado {
    class Registrando(val flujo: FlujoVenta) : Estado
    class ElegirFormato(val filtros: Filtros, val tipoGrafico: String?) : Estado
    // Falta elegir el gráfico (barras o pastel). formatos vacío = reporte dentro del chat, sin archivos
    class ElegirGrafico(val filtros: Filtros, val formatos: Set<Formato>) : Estado
    class ElegirEliminar(val opciones: List<VentaRegistrada>) : Estado
    class ConfirmarEliminar(val venta: VentaRegistrada) : Estado
}

class VentasChat(
    private val usuario: AppUser,
    private val negocio: NegocioData,
    context: Context,
    /** Se llama cada vez que se genera un reporte (con o sin PDF/Excel) para guardarlo en el historial. */
    private val alExportar: (titulo: String, contenido: String, archivos: List<File>, grafico: GraficoReporte?) -> Unit =
        { _, _, _, _ -> },
    // Item 3: permite agregar una columna/pregunta nueva al registro de ventas (y por lo tanto
    // al Excel exportado) escribiéndolo en el chat, sin entrar a "Mi Negocio".
    private val alGuardarPreguntas: (List<PreguntaVenta>) -> Unit = {}
) {
    private val appContext = context.applicationContext
    private val groq = GroqService()      // redacta las respuestas de consulta (sin memoria)
    private val gestor = GestorIA()       // conversación libre con memoria
    private var estado: Estado? = null

    // ------------------------------------------------------------------
    // Punto de entrada. Devuelve null si el mensaje no tiene que ver con ventas.
    // ------------------------------------------------------------------
    suspend fun procesar(entrada: String): RespuestaChat? {
        val texto = entrada.trim()
        val p = normalizar(texto)

        // 1) Si hay un proceso en curso, el mensaje es la respuesta a ese proceso
        estado?.let { actual ->
            if (esCancelar(p)) {
                estado = null
                return RespuestaChat("Listo, lo cancelé. ¿En qué más te ayudo?")
            }
            return when (actual) {
                is Estado.Registrando -> responderPaso(actual.flujo, texto)
                is Estado.ElegirFormato -> elegirFormato(actual, p)
                is Estado.ElegirGrafico -> elegirGrafico(actual, p)
                is Estado.ElegirEliminar -> elegirEliminar(actual, p)
                is Estado.ConfirmarEliminar -> confirmarEliminar(actual, p)
            }
        }

        // 2) Detectar la intención
        return when {
            esEliminar(p) -> iniciarEliminar(p)
            esExportar(p) -> exportar(p)
            esReporte(p) -> iniciarReporte(p)
            esRegistrar(p) -> iniciarRegistro()
            esAgregarColumna(p) -> agregarColumna(texto, p)
            esConsulta(p) -> consultar(texto, p)
            else -> null
        }
    }

    /** Conversación libre con Groq (para lo que no es de ventas). */
    suspend fun preguntarIA(pregunta: String): String =
        gestor.procesarPregunta(pregunta).getOrElse {
            "Todavía no puedo responder eso (${it.message}). Prueba con una de las opciones de abajo."
        }

    /** Reporte del mes actual con las ventas reales (para el botón «Generar reporte» y «Generar gráfico»). */
    suspend fun reporteDelMes(): Result<ReporteVentas?> {
        val todas = VentasRepository.listar(usuario.id).getOrElse { return Result.failure(it) }
        val hoy = LocalDate.now()
        val inicio = hoy.withDayOfMonth(1).atStartOfDay()
        val ventas = todas.filter { it.fechaHora >= inicio }
        if (ventas.isEmpty()) return Result.success(null)

        val mes = hoy.month.getDisplayName(TextStyle.FULL, LOCALE_ES)
        val porProducto = ventas.groupBy { it.producto }
            .map { (n, l) -> Triple(n, l.sumOf { it.cantidad }, l.sumOf { it.total }) }
            .sortedByDescending { it.second }

        val contenido = buildString {
            appendLine("Reporte de ventas de $mes ${hoy.year}:")
            porProducto.forEach { appendLine("• ${it.first}: ${it.second} unidades — ${dinero(it.third)}") }
            append("Total vendido: ${dinero(ventas.sumOf { it.total })} en ${ventas.size} venta(s)")
        }
        return Result.success(
            ReporteVentas(
                titulo = "Reporte de ventas - $mes ${hoy.year}",
                contenido = contenido,
                datosGrafico = porProducto.take(6).map { it.first to it.second }
            )
        )
    }

    // ------------------------------------------------------------------
    // Detección de intenciones
    // ------------------------------------------------------------------
    private fun esCancelar(p: String) =
        p in setOf("cancelar", "cancela", "cancelo", "salir", "olvidalo", "olvida", "no quiero") ||
                p.startsWith("cancel")

    private fun esSi(p: String) =
        p in setOf("si", "s", "sii", "claro", "ok", "okay", "confirmo", "confirmar", "guardar", "dale", "correcto", "yes") ||
                p.startsWith("si ")

    private fun esNo(p: String) =
        p in setOf("no", "n", "nop", "ninguno", "ninguna", "omitir", "omito", "sin descuento", "-", "nada")

    private fun esRegistrar(p: String): Boolean {
        val verbo = listOf(
            "registrar", "registra", "almacenar", "almacena", "guardar", "guarda", "anotar", "anota",
            "agregar", "agrega", "ingresar", "ingresa", "nueva venta", "crear venta", "anadir"
        ).any { it in p }
        return ("venta" in p && verbo) || p.startsWith("vendi ") || "quiero vender" in p
    }

    private fun esEliminar(p: String) =
        "venta" in p && listOf("elimin", "borr", "anul", "quitar", "quita ").any { it in p }

    // Item 3: "agrega una columna/pregunta sobre el nombre del cliente", "quiero una pregunta
    // nueva para...", etc. Crea una pregunta personalizada: aparece en el registro de ventas
    // (chat) y como columna extra en el Excel exportado.
    private fun esAgregarColumna(p: String): Boolean {
        val objeto = "columna" in p || "pregunta" in p || "campo" in p
        val verbo = listOf("agreg", "anadir", "anade", "crea", "nueva", "quiero una").any { it in p }
        return objeto && verbo && "quitar" !in p && "elimin" !in p && "borr" !in p
    }

    private fun agregarColumna(textoOriginal: String, p: String): RespuestaChat {
        if (usuario.role == Rol.SUPERVISOR) {
            return RespuestaChat("Solo tu gerente puede agregar o cambiar las preguntas del registro de ventas.")
        }
        val separador = Regex("\\b(sobre|para|de|que diga|llamada)\\b")
        val despues = separador.find(p)?.range?.last?.plus(1)
        val crudo = if (despues != null) textoOriginal.substring(despues).trim() else ""
        if (crudo.length < 3) {
            return RespuestaChat(
                "¿Sobre qué quieres la nueva pregunta? Dime, por ejemplo: " +
                        "«agrega una columna sobre el nombre del cliente»."
            )
        }
        val etiqueta = crudo.replaceFirstChar { it.uppercase() }.trimEnd('.', '?', '!')
        val yaExiste = negocio.preguntasVenta.any { it.personalizada && normalizar(it.texto).contains(normalizar(etiqueta)) }
        if (yaExiste) return RespuestaChat("Ya tienes una pregunta parecida a «$etiqueta». No agregué nada nuevo.")

        val nueva = PreguntaVenta(
            id = "EXTRA_${System.currentTimeMillis()}",
            texto = "¿Cuál es $etiqueta?".let { if (etiqueta.startsWith("¿")) etiqueta else it },
            activa = true,
            personalizada = true
        )
        negocio.preguntasVenta.add(nueva)
        alGuardarPreguntas(negocio.preguntasVenta.toList())
        return RespuestaChat(
            "Listo ✅ Agregué la pregunta «${nueva.texto}». A partir de ahora te la voy a preguntar " +
                    "cuando registres una venta, y va a salir como columna aparte en el Excel que exportes. " +
                    "También puedes editarla en «Mi Negocio»."
        )
    }

    private fun esExportar(p: String): Boolean {
        val formato = "excel" in p || "xlsx" in p || "pdf" in p || "hoja de calculo" in p
        val verbo = "export" in p || "descarg" in p || "archivo" in p
        return (formato && ("venta" in p || "reporte" in p || verbo)) || (verbo && "venta" in p)
    }

    // «genera un reporte», «dame un reporte completo»... (sin pedir Excel/PDF: se muestra en el chat)
    private fun esReporte(p: String): Boolean =
        "reporte" in p && listOf("genera", "crea", "dame", "haz", "completo", "quiero", "necesito").any { it in p }

    /** barras o pastel si el usuario ya lo dijo en su mensaje; null si hay que preguntárselo. */
    private fun tipoGraficoDe(p: String): String? = when {
        "pastel" in p || "circular" in p || "torta" in p -> ReportesRepository.GRAFICO_PASTEL
        "barra" in p -> ReportesRepository.GRAFICO_BARRAS
        else -> null
    }

    private fun esConsulta(p: String): Boolean {
        if ("stock" in p || "inventario" in p) return false
        return "venta" in p || "vendi" in p || "vendid" in p || "ingreso" in p || "recaud" in p
    }

    // ------------------------------------------------------------------
    // Registrar venta (conversación guiada)
    // ------------------------------------------------------------------
    private suspend fun iniciarRegistro(): RespuestaChat {
        // Perfil fresco: el gerente pudo asignar al supervisor después de iniciar sesión
        val actual = AuthRepository.currentUser() ?: usuario
        val esSupervisor = actual.role == Rol.SUPERVISOR && actual.gerenteId.isNotBlank()

        // Las sucursales viven en Appwrite: las del gerente (supervisor) o las propias (dueño)
        SucursalesRepository.listar(if (esSupervisor) actual.gerenteId else actual.id)
            .onSuccess { lista ->
                negocio.sucursales.clear()
                negocio.sucursales.addAll(lista)
            }

        val pasos = PreguntaVenta.normalizar(negocio.preguntasVenta).filter { it.activa || it.obligatoria }
        val flujo = FlujoVenta(pasos)
        if (esSupervisor) {
            negocio.sucursales.firstOrNull {
                it.id == actual.sucursal || it.nombre.equals(actual.sucursal, ignoreCase = true)
            }?.let {
                flujo.sucursal = it.nombre
                flujo.sucursalId = it.id
                flujo.sucursalFija = true
            }
        }
        estado = Estado.Registrando(flujo)
        val encabezado = "Perfecto, vamos a registrar una venta. (Escribe «cancelar» en cualquier momento para salir.)\n\n"
        val primera = avanzar(flujo)
        return RespuestaChat(encabezado + primera.texto)
    }

    private fun opcionesSucursal(): List<String> =
        negocio.sucursales.filter { it.activa }.map { it.nombre }

    private fun preguntaActual(f: FlujoVenta): String {
        val paso = f.pasos[f.indice]
        val sb = StringBuilder()
        sb.append("Pregunta ${f.indice + 1} de ${f.pasos.size}\n").append(paso.texto)
        when (paso.id) {
            PreguntaVenta.SUCURSAL -> {
                sb.append("\n")
                opcionesSucursal().forEachIndexed { i, s -> sb.append("\n${i + 1}. $s") }
                sb.append("\n\nResponde con el número o el nombre.")
            }
            PreguntaVenta.METODO_PAGO -> {
                sb.append("\n")
                METODOS_PAGO.forEachIndexed { i, s -> sb.append("\n${i + 1}. $s") }
                sb.append("\n\nResponde con el número o escribe otro método.")
            }
            else -> if (paso.personalizada) sb.append("\n(Escribe «no» para omitir.)")
        }
        return sb.toString()
    }

    private fun avanzar(f: FlujoVenta): RespuestaChat {
        // Si no hay sucursales activas, esa pregunta se omite
        while (f.indice < f.pasos.size &&
            f.pasos[f.indice].id == PreguntaVenta.SUCURSAL &&
            (f.sucursalFija || opcionesSucursal().isEmpty())
        ) f.indice++

        if (f.indice < f.pasos.size) return RespuestaChat(preguntaActual(f))
        f.confirmando = true
        return RespuestaChat(resumenVenta(f))
    }

    private suspend fun responderPaso(f: FlujoVenta, entrada: String): RespuestaChat {
        if (f.confirmando) return confirmarRegistro(f, normalizar(entrada))

        val paso = f.pasos[f.indice]
        val e = entrada.trim()
        val omitir = esNo(normalizar(e))

        when (paso.id) {
            PreguntaVenta.PRODUCTO -> {
                if (e.length < 2) return RespuestaChat("Escribe el nombre del producto, por ejemplo «Camisa».")
                f.producto = e.replaceFirstChar { it.uppercase() }
            }
            PreguntaVenta.CANTIDAD -> {
                val n = Regex("\\d+").find(e)?.value?.toIntOrNull()
                if (n == null || n <= 0 || n > 100000) {
                    return RespuestaChat("Indica una cantidad válida (un número entero mayor que 0).")
                }
                f.cantidad = n
            }
            PreguntaVenta.PRECIO -> {
                val v = parseNumero(e)
                if (v == null || v <= 0.0) {
                    return RespuestaChat("Indica un precio válido en dólares, por ejemplo 15 o 15.50.")
                }
                f.precio = redondear(v)
            }
            PreguntaVenta.DESCUENTO -> {
                if (omitir) {
                    f.descuento = 0.0
                    f.descuentoTexto = ""
                } else {
                    val d = parseDescuento(e, f.subtotal)
                        ?: return RespuestaChat(
                            "No entendí el descuento. Escribe un porcentaje (ej. 10%) o un monto en dólares " +
                                    "(ej. 5), o «no». No puede superar el subtotal de ${dinero(f.subtotal)}."
                        )
                    f.descuento = d.first
                    f.descuentoTexto = d.second
                }
            }
            PreguntaVenta.SUCURSAL -> {
                f.sucursal = if (omitir) "" else resolverOpcion(e, opcionesSucursal())
                    ?: return RespuestaChat("No reconocí la sucursal.\n\n" + preguntaActual(f))
                f.sucursalId = negocio.sucursales.firstOrNull { it.nombre == f.sucursal }?.id ?: ""
            }
            PreguntaVenta.METODO_PAGO -> {
                f.metodoPago = if (omitir) "" else resolverOpcion(e, METODOS_PAGO)
                    ?: e.replaceFirstChar { it.uppercase() }
            }
            PreguntaVenta.NOTA -> f.nota = if (omitir) "" else e
            else -> f.extras[paso.texto] = if (omitir) "" else e
        }

        f.indice++
        return avanzar(f)
    }

    private fun resumenVenta(f: FlujoVenta): String = buildString {
        appendLine("Este es el resumen de la venta:\n")
        appendLine("• Producto: ${f.producto}")
        appendLine("• Cantidad: ${f.cantidad}")
        appendLine("• Precio unitario: ${dinero(f.precio)}")
        if (f.descuento > 0) {
            val extra = if (f.descuentoTexto.isNotBlank()) " (${f.descuentoTexto})" else ""
            appendLine("• Descuento: ${dinero(f.descuento)}$extra")
        }
        appendLine("• Total: ${dinero(f.total)}")
        if (f.sucursal.isNotBlank()) appendLine("• Sucursal: ${f.sucursal}")
        if (f.metodoPago.isNotBlank()) appendLine("• Pago: ${f.metodoPago}")
        if (f.nota.isNotBlank()) appendLine("• Nota: ${f.nota}")
        f.extras.forEach { (pregunta, respuesta) ->
            if (respuesta.isNotBlank()) appendLine("• ${pregunta.trim('¿', '?', ' ')}: $respuesta")
        }
        append("\n¿Confirmas? Responde «sí» para guardarla o «no» para descartarla.")
    }

    private suspend fun confirmarRegistro(f: FlujoVenta, p: String): RespuestaChat {
        if (esNo(p)) {
            estado = null
            return RespuestaChat("Venta descartada. No se guardó nada.")
        }
        if (!esSi(p)) return RespuestaChat("Responde «sí» para guardar la venta o «no» para descartarla.")

        val nueva = NuevaVenta(
            producto = f.producto,
            cantidad = f.cantidad,
            precioUnitario = f.precio,
            descuento = f.descuento,
            total = redondear(f.total),
            sucursal = f.sucursal,
            metodoPago = f.metodoPago,
            nota = f.nota,
            extras = f.extras.toMap()
        )
        return VentasRepository.guardar(usuario, nueva, f.sucursalId).fold(
            onSuccess = {
                estado = null
                RespuestaChat(
                    "✅ Venta registrada: ${it.cantidad} × ${it.producto} — total ${dinero(it.total)}.\n\n" +
                            "Puedes pedirme «ventas de hoy», «exporta mis ventas del mes en Excel» o «registrar otra venta»."
                )
            },
            onFailure = {
                RespuestaChat(
                    "No pude guardar la venta: ${it.message}\n\n" +
                            "Responde «sí» para reintentar o «no» para descartarla."
                )
            }
        )
    }

    // ------------------------------------------------------------------
    // Consultar
    // ------------------------------------------------------------------
    private suspend fun consultar(pregunta: String, p: String): RespuestaChat {
        val todas = VentasRepository.listar(usuario.id)
            .getOrElse { return RespuestaChat(it.message ?: "No pude leer tus ventas.") }
        if (todas.isEmpty()) {
            return RespuestaChat("Aún no tienes ventas registradas. Escribe «registrar venta» para empezar.")
        }

        val filtros = filtrosDe(p, todas)
        val ventas = aplicar(filtros, todas)
        if (ventas.isEmpty()) {
            return RespuestaChat(
                "No encontré ventas ${filtros.descripcion()}. " +
                        "Prueba con «de este mes», «de la semana» o «todas»."
            )
        }

        val datos = construirDatos(ventas, filtros, p)
        val redaccion = redactar(pregunta, datos)
        val pista = "\n\n💡 Puedes pedirme: «exporta las ventas ${filtros.periodo} en Excel o PDF»."
        return RespuestaChat(
            if (redaccion != null) "$redaccion\n\n— Detalle —\n$datos$pista" else "$datos$pista"
        )
    }

    /** Groq solo redacta; las cifras las calcula la app y se le entregan ya hechas. */
    private suspend fun redactar(pregunta: String, datos: String): String? {
        val prompt = "Datos calculados por la app (no inventes ni cambies cifras):\n$datos\n\n" +
                "Pregunta del usuario: $pregunta\n\n" +
                "Responde en español, claro y breve (máximo 4 frases), usando solo esos datos."
        return groq.enviar(emptyList(), prompt).getOrNull()
            ?.replace("**", "")?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun construirDatos(ventas: List<VentaRegistrada>, f: Filtros, p: String): String = buildString {
        val total = ventas.sumOf { it.total }
        val descuentos = ventas.sumOf { it.descuento }
        appendLine("Periodo: ${f.descripcion()}")
        appendLine("Ventas registradas: ${ventas.size}")
        appendLine("Unidades vendidas: ${ventas.sumOf { it.cantidad }}")
        appendLine("Total vendido: ${dinero(total)}")
        if (descuentos > 0) appendLine("Descuentos aplicados: ${dinero(descuentos)}")
        appendLine("Venta promedio: ${dinero(total / ventas.size)}")

        val porProducto = ventas.groupBy { it.producto }
            .map { (n, l) -> Triple(n, l.sumOf { it.cantidad }, l.sumOf { it.total }) }
            .sortedByDescending { it.second }
        appendLine("Productos más vendidos (unidades · total):")
        porProducto.take(5).forEach { appendLine("- ${it.first}: ${it.second} · ${dinero(it.third)}") }

        val porSucursal = ventas.filter { it.sucursal.isNotBlank() }.groupBy { it.sucursal }
        if (porSucursal.size > 1) {
            appendLine("Por sucursal:")
            porSucursal.forEach { (s, l) -> appendLine("- $s: ${dinero(l.sumOf { it.total })}") }
        }

        val porPago = ventas.filter { it.metodoPago.isNotBlank() }.groupBy { it.metodoPago }
        if (porPago.size > 1) {
            appendLine("Por método de pago:")
            porPago.forEach { (m, l) -> appendLine("- $m: ${dinero(l.sumOf { it.total })}") }
        }

        if (Regex("\\bmeses\\b").containsMatchIn(p)) {
            appendLine("Por mes (de mayor a menor):")
            ventas.groupBy { it.fechaHora.year to it.fechaHora.monthValue }
                .map { (ym, l) ->
                    val nombre = java.time.Month.of(ym.second).getDisplayName(TextStyle.FULL, LOCALE_ES)
                    Triple("$nombre ${ym.first}", l.sumOf { it.total }, l.size)
                }
                .sortedByDescending { it.second }
                .forEach { appendLine("- ${it.first}: ${dinero(it.second)} (${it.third} ventas)") }
        }

        if ("ultima" in p || "reciente" in p) {
            appendLine("Últimas ventas:")
            ventas.take(5).forEach { appendLine("- ${resumenLinea(it)}") }
        }
    }.trim()

    // ------------------------------------------------------------------
    // Exportar
    // ------------------------------------------------------------------
    private suspend fun exportar(p: String): RespuestaChat {
        val todas = VentasRepository.listar(usuario.id)
            .getOrElse { return RespuestaChat(it.message ?: "No pude leer tus ventas.") }
        if (todas.isEmpty()) {
            return RespuestaChat("Aún no tienes ventas registradas para exportar. Escribe «registrar venta» para empezar.")
        }
        val filtros = filtrosDe(p, todas)
        val formatos = formatosDe(p)
        if (formatos.isEmpty()) {
            estado = Estado.ElegirFormato(filtros, tipoGraficoDe(p))
            return RespuestaChat(
                "¿En qué formato quieres las ventas ${filtros.descripcion()}? " +
                        "Responde «Excel», «PDF» o «ambos»."
            )
        }
        return conGrafico(tipoGraficoDe(p), filtros, formatos, todas)
    }

    private suspend fun elegirFormato(e: Estado.ElegirFormato, p: String): RespuestaChat {
        val formatos = formatosDe(p)
        if (formatos.isEmpty()) return RespuestaChat("Responde «Excel», «PDF» o «ambos» (o «cancelar»).")
        estado = null
        val todas = VentasRepository.listar(usuario.id)
            .getOrElse { return RespuestaChat(it.message ?: "No pude leer tus ventas.") }
        return conGrafico(e.tipoGrafico ?: tipoGraficoDe(p), e.filtros, formatos, todas)
    }

    // ------------------------------------------------------------------
    // Reporte completo: texto + gráfico de barras o de pastel
    // ------------------------------------------------------------------
    private suspend fun iniciarReporte(p: String): RespuestaChat {
        val todas = VentasRepository.listar(usuario.id)
            .getOrElse { return RespuestaChat(it.message ?: "No pude leer tus ventas.") }
        if (todas.isEmpty()) {
            return RespuestaChat("Aún no tienes ventas registradas para el reporte. Escribe «registrar venta» para empezar.")
        }
        return conGrafico(tipoGraficoDe(p), filtrosDe(p, todas), emptySet(), todas)
    }

    /** Si ya se sabe el tipo de gráfico genera; si no, lo pregunta (barras o pastel). */
    private suspend fun conGrafico(
        tipo: String?,
        filtros: Filtros,
        formatos: Set<Formato>,
        todas: List<VentaRegistrada>
    ): RespuestaChat {
        val ventas = aplicar(filtros, todas)
        if (ventas.isEmpty()) return RespuestaChat("No encontré ventas ${filtros.descripcion()} para el reporte.")
        if (tipo == null) {
            estado = Estado.ElegirGrafico(filtros, formatos)
            return RespuestaChat("¿Qué gráfico quieres en el reporte: de «barras» o de «pastel»?")
        }
        return if (formatos.isEmpty()) reporteEnChat(filtros, tipo, ventas)
        else generarArchivos(filtros, formatos, todas, tipo)
    }

    private suspend fun elegirGrafico(e: Estado.ElegirGrafico, p: String): RespuestaChat {
        val tipo = tipoGraficoDe(p) ?: return RespuestaChat("Responde «barras» o «pastel» (o «cancelar»).")
        estado = null
        val todas = VentasRepository.listar(usuario.id)
            .getOrElse { return RespuestaChat(it.message ?: "No pude leer tus ventas.") }
        return conGrafico(tipo, e.filtros, e.formatos, todas)
    }

    private fun graficoDe(tipo: String, ventas: List<VentaRegistrada>) = GraficoReporte(
        tipo = tipo,
        datos = ventas.groupBy { it.producto }
            .map { (nombre, lista) -> nombre to lista.sumOf { it.cantidad } }
            .sortedByDescending { it.second }
            .take(6)
    )

    private fun reporteEnChat(f: Filtros, tipo: String, ventas: List<VentaRegistrada>): RespuestaChat {
        val porProducto = ventas.groupBy { it.producto }
            .map { (n, l) -> Triple(n, l.sumOf { it.cantidad }, l.sumOf { it.total }) }
            .sortedByDescending { it.second }
        val contenido = buildString {
            appendLine("Reporte de ventas ${f.descripcion()}:")
            porProducto.forEach { appendLine("• ${it.first}: ${it.second} unidades — ${dinero(it.third)}") }
            append("Total vendido: ${dinero(ventas.sumOf { it.total })} en ${ventas.size} venta(s)")
        }
        val grafico = graficoDe(tipo, ventas)
        alExportar("Reporte de ventas ${f.descripcion()}", contenido, emptyList(), grafico)
        NotificacionesReporte.avisar(
            appContext,
            "Reporte generado",
            "Tu reporte de ventas ${f.descripcion()} con gráfico está en el historial de reportes."
        )
        val nombre = if (grafico.esPastel) "pastel" else "barras"
        return RespuestaChat(
            "Listo ✅ Generé el reporte con gráfico de $nombre y lo guardé en el historial de reportes.\n\n$contenido",
            grafico = grafico
        )
    }

    private fun formatosDe(p: String): Set<Formato> = buildSet {
        if ("excel" in p || "xlsx" in p || "hoja de calculo" in p) add(Formato.EXCEL)
        if ("pdf" in p) add(Formato.PDF)
        if ("ambos" in p || "los dos" in p) {
            add(Formato.EXCEL)
            add(Formato.PDF)
        }
    }

    private suspend fun generarArchivos(
        f: Filtros,
        formatos: Set<Formato>,
        todas: List<VentaRegistrada>,
        tipoGrafico: String
    ): RespuestaChat {
        val ventas = aplicar(f, todas)
        if (ventas.isEmpty()) return RespuestaChat("No encontré ventas ${f.descripcion()} para exportar.")
        val grafico = graficoDe(tipoGrafico, ventas)
        return try {
            val archivos = withContext(Dispatchers.IO) {
                buildList {
                    if (Formato.EXCEL in formatos) add(VentasExcel.generar(appContext, ventas, f.descripcion(), grafico))
                    if (Formato.PDF in formatos) {
                        add(VentasPdf.generar(appContext, ventas, f.descripcion(), usuario.alias, negocio.nombreNegocio, grafico))
                    }
                }
            }
            val resumen = "Exporté ${ventas.size} venta(s) ${f.descripcion()}. " +
                    "Total: ${dinero(ventas.sumOf { it.total })}."

            // Avisa para que el PDF/Excel quede en el historial de reportes
            alExportar(
                "Ventas ${f.descripcion()} (${archivos.joinToString(" y ") { it.extension.uppercase() }})",
                resumen,
                archivos,
                grafico
            )

            // Notificación: toca para abrir el archivo
            val tipos = archivos.joinToString(" y ") { if (it.extension.equals("pdf", true)) "PDF" else "Excel" }
            NotificacionesReporte.avisar(
                appContext,
                "Reporte listo",
                "Tu reporte en $tipos (${f.descripcion()}) está listo. Toca para abrirlo.",
                archivos.firstOrNull()
            )

            RespuestaChat("Listo ✅ $resumen Lo guardé en el historial de reportes.", archivos, grafico)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RespuestaChat("No pude generar el archivo: ${e.message}")
        }
    }

    // ------------------------------------------------------------------
    // Eliminar
    // ------------------------------------------------------------------
    private suspend fun iniciarEliminar(p: String): RespuestaChat {
        val todas = VentasRepository.listar(usuario.id)
            .getOrElse { return RespuestaChat(it.message ?: "No pude leer tus ventas.") }
        if (todas.isEmpty()) return RespuestaChat("No tienes ventas registradas.")

        val recientes = todas.take(5) // ya vienen de la más reciente a la más antigua
        if ("ultima" in p) {
            estado = Estado.ConfirmarEliminar(recientes.first())
            return RespuestaChat(
                "¿Seguro que quieres eliminar la última venta?\n${resumenLinea(recientes.first())}\n\n" +
                        "Responde «sí» o «no»."
            )
        }
        estado = Estado.ElegirEliminar(recientes)
        return RespuestaChat(
            "Estas son tus últimas ventas. Responde con el número de la que quieres eliminar:\n\n" +
                    recientes.mapIndexed { i, v -> "${i + 1}. ${resumenLinea(v)}" }.joinToString("\n")
        )
    }

    private fun elegirEliminar(e: Estado.ElegirEliminar, p: String): RespuestaChat {
        val n = Regex("\\d+").find(p)?.value?.toIntOrNull()
        val venta = n?.let { e.opciones.getOrNull(it - 1) }
            ?: return RespuestaChat("Responde con un número de la lista (1 a ${e.opciones.size}) o «cancelar».")
        estado = Estado.ConfirmarEliminar(venta)
        return RespuestaChat("¿Eliminar esta venta?\n${resumenLinea(venta)}\n\nResponde «sí» o «no».")
    }

    private suspend fun confirmarEliminar(e: Estado.ConfirmarEliminar, p: String): RespuestaChat {
        if (esNo(p)) {
            estado = null
            return RespuestaChat("Listo, no eliminé nada.")
        }
        if (!esSi(p)) return RespuestaChat("Responde «sí» para eliminarla o «no» para conservarla.")
        return VentasRepository.eliminar(e.venta.id).fold(
            onSuccess = {
                estado = null
                RespuestaChat("Venta eliminada.")
            },
            onFailure = { RespuestaChat("No pude eliminarla: ${it.message}") }
        )
    }

    // ------------------------------------------------------------------
    // Filtros (periodo, producto, sucursal) tomados del texto del usuario
    // ------------------------------------------------------------------
    private fun palabra(p: String, w: String) = Regex("\\b$w\\b").containsMatchIn(p)

    private fun filtrosDe(p: String, ventas: List<VentaRegistrada>): Filtros {
        val hoy = LocalDate.now()
        val periodo: Triple<LocalDateTime?, LocalDateTime?, String> = when {
            palabra(p, "ayer") ->
                Triple(hoy.minusDays(1).atStartOfDay(), hoy.atStartOfDay(), "de ayer")
            palabra(p, "hoy") ->
                Triple(hoy.atStartOfDay(), hoy.plusDays(1).atStartOfDay(), "de hoy")
            "semana" in p ->
                Triple(hoy.with(DayOfWeek.MONDAY).atStartOfDay(), null, "de esta semana")
            "mes pasado" in p -> {
                val inicioActual = hoy.withDayOfMonth(1)
                Triple(inicioActual.minusMonths(1).atStartOfDay(), inicioActual.atStartOfDay(), "del mes pasado")
            }
            palabra(p, "todas") || palabra(p, "todo") || "historial" in p || "siempre" in p || palabra(p, "meses") ->
                Triple(null, null, "de todo el historial")
            palabra(p, "ano") ->
                Triple(hoy.withDayOfYear(1).atStartOfDay(), null, "de este año")
            else ->
                Triple(hoy.withDayOfMonth(1).atStartOfDay(), null, "de este mes")
        }

        val producto = ventas.map { it.producto }.distinct()
            .filter { it.isNotBlank() }
            .sortedByDescending { it.length }
            .firstOrNull { p.contains(normalizar(it)) }

        val sucursales = (negocio.sucursales.map { it.nombre } + ventas.map { it.sucursal })
            .distinct().filter { it.isNotBlank() }
        val sucursal = sucursales.firstOrNull { coincideNombre(p, it) }

        return Filtros(periodo.first, periodo.second, periodo.third, producto, sucursal)
    }

    private fun aplicar(f: Filtros, ventas: List<VentaRegistrada>): List<VentaRegistrada> =
        ventas.filter { v ->
            val fecha = v.fechaHora
            (f.desde == null || fecha >= f.desde) &&
                    (f.hasta == null || fecha < f.hasta) &&
                    (f.producto == null || v.producto.equals(f.producto, ignoreCase = true)) &&
                    (f.sucursal == null || v.sucursal.equals(f.sucursal, ignoreCase = true))
        }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------
    private fun normalizar(t: String): String =
        Normalizer.normalize(t, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase().trim()

    /** "Sucursal 1 - Mejicanos" coincide si el texto contiene el nombre completo o la parte tras el guion. */
    private fun coincideNombre(p: String, nombre: String): Boolean {
        val completo = normalizar(nombre)
        val corto = normalizar(nombre.substringAfter("-"))
        return p.contains(completo) || (corto.length >= 3 && p.contains(corto))
    }

    private fun resolverOpcion(entrada: String, opciones: List<String>): String? {
        val p = normalizar(entrada)
        p.toIntOrNull()?.let { n -> return opciones.getOrNull(n - 1) }
        return opciones.firstOrNull { normalizar(it) == p }
            ?: opciones.firstOrNull { p.length >= 3 && coincideNombre(p, it) }
            ?: opciones.firstOrNull { p.length >= 3 && normalizar(it).contains(p) }
    }

    private fun parseNumero(texto: String): Double? {
        var t = texto.trim().replace("$", "").replace("usd", "", ignoreCase = true).replace(" ", "")
        if (t.contains('.') && t.contains(',')) t = t.replace(",", "")
        else if (t.contains(',')) t = if (Regex(",\\d{1,2}$").containsMatchIn(t)) t.replace(',', '.') else t.replace(",", "")
        return t.toDoubleOrNull()
    }

    /** Devuelve (monto del descuento, texto del porcentaje) o null si no es válido. */
    private fun parseDescuento(texto: String, subtotal: Double): Pair<Double, String>? {
        val t = texto.trim()
        if (t.endsWith("%")) {
            val pct = parseNumero(t.dropLast(1)) ?: return null
            if (pct < 0 || pct > 100) return null
            return redondear(subtotal * pct / 100) to "${numeroCorto(pct)}%"
        }
        val monto = parseNumero(t) ?: return null
        if (monto < 0 || monto > subtotal) return null
        return redondear(monto) to ""
    }

    private fun redondear(v: Double) = Math.round(v * 100) / 100.0

    private fun numeroCorto(v: Double) =
        if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(Locale.US, "%.1f", v)

    private fun dinero(v: Double) = "$" + String.format(Locale.US, "%,.2f", v)

    private fun resumenLinea(v: VentaRegistrada) =
        "${v.fechaHora.format(formatoVista)}: ${v.cantidad} × ${v.producto} = ${dinero(v.total)}" +
                if (v.sucursal.isNotBlank()) " (${v.sucursal})" else ""
}