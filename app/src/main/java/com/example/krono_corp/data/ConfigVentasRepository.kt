package com.example.krono_corp.data

/**
 * Guarda las preguntas de venta personalizadas en la fila del propio usuario
 * (tabla "usuarios", columna de texto "preguntasVenta").
 */
object ConfigVentasRepository {

    private val db get() = AppwriteClient.tablesDB
    private val dbId get() = AppwriteClient.DATABASE_ID

    /** null = el usuario todavía no ha personalizado nada (se usan las de por defecto). */
    suspend fun cargarPreguntas(userId: String): Result<List<PreguntaVenta>?> =
        appwriteSafe("usuarios") {
            val fila = db.getRow(
                databaseId = dbId,
                tableId = AppwriteClient.TABLE_ID,
                rowId = userId
            )
            PreguntaVenta.desdeJson(fila.data["preguntasVenta"] as? String)
                ?.let { PreguntaVenta.normalizar(it) }
        }

    suspend fun guardarPreguntas(userId: String, preguntas: List<PreguntaVenta>): Result<Unit> =
        appwriteSafe("usuarios") {
            db.updateRow(
                databaseId = dbId,
                tableId = AppwriteClient.TABLE_ID,
                rowId = userId,
                data = mapOf("preguntasVenta" to PreguntaVenta.aJson(preguntas))
            )
            Unit
        }
}