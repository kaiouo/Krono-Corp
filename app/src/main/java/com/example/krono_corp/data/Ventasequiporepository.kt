package com.example.krono_corp.data

import io.appwrite.Query
import io.appwrite.models.Row
import org.json.JSONObject

/**
 * GERENTE: lee las ventas que los supervisores guardaron en la tabla "registro_ventas".
 *
 * Requiere que la tabla "registro_ventas" tenga Read para la etiqueta GERENTE
 * (Appwrite > registro_ventas > Settings > Permissions).
 */
object VentasEquipoRepository {

    const val TABLE_REGISTRO_VENTAS = "6ae91a26000743892eda"

    private const val PAGINA = 100
    private const val MAX_FILAS = 2000

    private val db get() = AppwriteClient.tablesDB
    private val dbId get() = AppwriteClient.DATABASE_ID

    /**
     * Ventas de los usuarios indicados (las más recientes primero).
     * Se filtra en la app para no depender de un índice en la columna usuarioId.
     */
    suspend fun listarDe(usuarioIds: Set<String>): Result<List<VentaRegistrada>> =
        appwriteSafe("registro_ventas") {
            val resultado = mutableListOf<VentaRegistrada>()
            var ultimoId: String? = null
            var leidas = 0

            while (leidas < MAX_FILAS) {
                val queries = mutableListOf(Query.orderDesc("\$createdAt"), Query.limit(PAGINA))
                ultimoId?.let { queries.add(Query.cursorAfter(it)) }

                val filas = db.listRows(
                    databaseId = dbId,
                    tableId = TABLE_REGISTRO_VENTAS,
                    queries = queries
                ).rows
                if (filas.isEmpty()) break

                filas.forEach { fila ->
                    val venta = fila.toVenta()
                    if (venta.usuarioId in usuarioIds) resultado.add(venta)
                }

                leidas += filas.size
                if (filas.size < PAGINA) break
                ultimoId = filas.last().id
            }
            resultado
        }

    // ---------- helpers ----------

    private fun Row<Map<String, Any>>.toVenta(): VentaRegistrada {
        fun texto(columna: String) = data[columna] as? String ?: ""
        fun numero(columna: String) = (data[columna] as? Number)?.toDouble() ?: 0.0

        return VentaRegistrada(
            id = id,
            usuarioId = texto("usuarioId"),
            usuarioNombre = texto("usuarioNombre"),
            fecha = texto("fechaVenta"),
            producto = texto("producto"),
            cantidad = numero("cantidad").toInt(),
            precioUnitario = numero("precioUnitario"),
            descuento = numero("descuento"),
            total = numero("total"),
            sucursal = texto("sucursal"),
            metodoPago = texto("metodoPago"),
            nota = texto("nota"),
            extras = extrasDe(texto("extras"))
        )
    }

    /** "extras" se guarda como texto JSON {"pregunta":"respuesta"}; si no se puede leer, queda vacío. */
    private fun extrasDe(texto: String): Map<String, String> {
        if (texto.isBlank()) return emptyMap()
        return try {
            val json = JSONObject(texto)
            json.keys().asSequence().associateWith { json.optString(it) }
        } catch (e: Exception) {
            emptyMap()
        }
    }
}