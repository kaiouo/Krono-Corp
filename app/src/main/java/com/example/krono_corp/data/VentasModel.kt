package com.example.krono_corp.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/**
 * Pregunta que la IA hace al registrar una venta.
 * Las preguntas por defecto se pueden editar y las opcionales desactivar;
 * además el usuario puede agregar las suyas (personalizadas) en MiNegocioScreen.
 */
data class PreguntaVenta(
    val id: String,
    val texto: String,
    val activa: Boolean = true,
    val obligatoria: Boolean = false,
    val personalizada: Boolean = false
) {
    companion object {
        const val PRODUCTO = "PRODUCTO"
        const val CANTIDAD = "CANTIDAD"
        const val PRECIO = "PRECIO"
        const val DESCUENTO = "DESCUENTO"
        const val SUCURSAL = "SUCURSAL"
        const val METODO_PAGO = "METODO_PAGO"
        const val NOTA = "NOTA"

        private val OBLIGATORIAS = setOf(PRODUCTO, CANTIDAD, PRECIO)

        fun porDefecto(): List<PreguntaVenta> = listOf(
            PreguntaVenta(PRODUCTO, "¿Qué producto vendiste?", obligatoria = true),
            PreguntaVenta(CANTIDAD, "¿Cuántas unidades vendiste?", obligatoria = true),
            PreguntaVenta(PRECIO, "¿Cuál es el precio por unidad? (en dólares)", obligatoria = true),
            PreguntaVenta(
                DESCUENTO,
                "¿Aplicaste algún descuento al cliente? Escribe un porcentaje (ej. 10%) o un monto (ej. \$5), o «no»."
            ),
            PreguntaVenta(SUCURSAL, "¿En qué sucursal fue la venta?"),
            PreguntaVenta(METODO_PAGO, "¿Cómo pagó el cliente?"),
            PreguntaVenta(NOTA, "¿Quieres agregar una nota? (escribe «no» para omitir)")
        )

        /** Asegura que estén todas las preguntas base (por si el JSON guardado quedó incompleto). */
        fun normalizar(guardadas: List<PreguntaVenta>): List<PreguntaVenta> {
            val resultado = guardadas.toMutableList()
            porDefecto().forEachIndexed { i, base ->
                if (resultado.none { it.id == base.id }) {
                    resultado.add(i.coerceAtMost(resultado.size), base)
                }
            }
            return resultado.map {
                if (it.id in OBLIGATORIAS) it.copy(activa = true, obligatoria = true) else it
            }
        }

        fun aJson(lista: List<PreguntaVenta>): String = JSONArray().apply {
            lista.forEach {
                put(
                    JSONObject()
                        .put("id", it.id)
                        .put("t", it.texto)
                        .put("a", it.activa)
                        .put("c", it.personalizada)
                )
            }
        }.toString()

        fun desdeJson(texto: String?): List<PreguntaVenta>? {
            if (texto.isNullOrBlank()) return null
            return try {
                val arreglo = JSONArray(texto)
                List(arreglo.length()) { i ->
                    val o = arreglo.getJSONObject(i)
                    val id = o.getString("id")
                    PreguntaVenta(
                        id = id,
                        texto = o.getString("t"),
                        activa = o.optBoolean("a", true),
                        obligatoria = id in OBLIGATORIAS,
                        personalizada = o.optBoolean("c", false)
                    )
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}

/** Datos que se capturan en el chat antes de guardar. */
data class NuevaVenta(
    val producto: String,
    val cantidad: Int,
    val precioUnitario: Double,
    val descuento: Double,        // monto en dólares
    val total: Double,
    val sucursal: String,
    val metodoPago: String,
    val nota: String,
    val extras: Map<String, String>   // respuestas a las preguntas personalizadas
)

/** Venta guardada en Appwrite (tabla "registro_ventas"). */
data class VentaRegistrada(
    val id: String,
    val usuarioId: String,
    val usuarioNombre: String,
    val fecha: String,                // ISO: 2026-11-03T10:15:00
    val producto: String,
    val cantidad: Int,
    val precioUnitario: Double,
    val descuento: Double,
    val total: Double,
    val sucursal: String,
    val metodoPago: String,
    val nota: String,
    val extras: Map<String, String>
)

val VentaRegistrada.fechaHora: LocalDateTime
    get() = try {
        LocalDateTime.parse(fecha)
    } catch (e: Exception) {
        LocalDateTime.of(2000, 1, 1, 0, 0)
    }