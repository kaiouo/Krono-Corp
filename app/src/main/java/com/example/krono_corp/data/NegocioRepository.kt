package com.example.krono_corp.data

import io.appwrite.exceptions.AppwriteException
import org.json.JSONArray

/** Lo que se guarda en Appwrite para el negocio de cada usuario. */
data class NegocioGuardado(
    val nombreNegocio: String,
    /** null = nunca se han guardado preguntas (se usan las de por defecto). */
    val preguntasIA: List<String>?,
    /** Preguntas del registro de ventas configuradas por el gerente (null = sin configurar). */
    val preguntasVenta: List<PreguntaVenta>? = null
)

/**
 * Guarda el nombre del negocio y las preguntas en la tabla "negocios".
 * Usa el id del usuario (ownerId) como id de la fila: una fila por usuario.
 */
object NegocioRepository {

    const val TABLE_NEGOCIOS = "6ae90fde00129ce5d8f6"

    private val db get() = AppwriteClient.tablesDB
    private val dbId get() = AppwriteClient.DATABASE_ID

    /** Devuelve null si el usuario todavía no ha guardado su negocio. */
    suspend fun cargar(ownerId: String): Result<NegocioGuardado?> = appwriteSafe("negocios") {
        try {
            val fila = db.getRow(
                databaseId = dbId,
                tableId = TABLE_NEGOCIOS,
                rowId = ownerId
            )
            NegocioGuardado(
                nombreNegocio = fila.data["nombreNegocio"] as? String ?: "",
                preguntasIA = preguntasDesdeJson(fila.data["preguntasIA"] as? String),
                preguntasVenta = PreguntaVenta.desdeJson(fila.data["preguntasVenta"] as? String)
                    ?.let { PreguntaVenta.normalizar(it) }
            )
        } catch (e: AppwriteException) {
            if (e.code == 404) null else throw e
        }
    }

    /**
     * Crea la fila si no existe y la actualiza si ya existe.
     * preguntasVenta solo se envía si no es null (así el Dueño no necesita la columna).
     */
    suspend fun guardar(
        ownerId: String,
        nombreNegocio: String,
        preguntasIA: List<String>,
        preguntasVenta: List<PreguntaVenta>? = null
    ): Result<Unit> = appwriteSafe("negocios") {
        val datos = mutableMapOf<String, Any>(
            "ownerId" to ownerId,
            "nombreNegocio" to nombreNegocio.trim().ifEmpty { "Mi negocio" },
            "preguntasIA" to preguntasAJson(preguntasIA)
        )
        if (preguntasVenta != null) {
            datos["preguntasVenta"] = PreguntaVenta.aJson(preguntasVenta)
        }
        try {
            db.updateRow(
                databaseId = dbId,
                tableId = TABLE_NEGOCIOS,
                rowId = ownerId,
                data = datos
            )
        } catch (e: AppwriteException) {
            if (e.code == 404) {
                db.createRow(
                    databaseId = dbId,
                    tableId = TABLE_NEGOCIOS,
                    rowId = ownerId,
                    data = datos
                )
            } else {
                throw e
            }
        }
        Unit
    }

    // ---------- helpers ----------

    private fun preguntasAJson(lista: List<String>): String =
        JSONArray().apply { lista.forEach { put(it) } }.toString()

    private fun preguntasDesdeJson(texto: String?): List<String>? {
        if (texto.isNullOrBlank()) return null
        return try {
            val arreglo = JSONArray(texto)
            List(arreglo.length()) { i -> arreglo.getString(i) }
        } catch (e: Exception) {
            null
        }
    }
}