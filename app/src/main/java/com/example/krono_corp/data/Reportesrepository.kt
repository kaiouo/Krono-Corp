package com.example.krono_corp.data

import io.appwrite.ID
import io.appwrite.Permission
import io.appwrite.Query
import io.appwrite.Role
import io.appwrite.models.Row
import org.json.JSONArray
import org.json.JSONObject

/** Un reporte o gráfico que generó un SUPERVISOR (fila de la tabla "reportes"). */
data class ReporteRemoto(
    val id: String,
    val supervisorId: String,
    val supervisorNombre: String,
    val tipo: String,        // REPORTE o GRAFICO
    val titulo: String,
    val fecha: String,
    val contenido: String,   // texto del reporte, o JSON con los datos del gráfico
    val estado: String       // Pendiente o Revisado
)

object ReportesRepository {

    const val TABLE_REPORTES = "6ae54d25001bd5664634"
    const val TIPO_REPORTE = "REPORTE"
    const val TIPO_GRAFICO = "GRAFICO"
    const val ESTADO_PENDIENTE = "Pendiente"
    const val ESTADO_REVISADO = "Revisado"

    private val db get() = AppwriteClient.tablesDB
    private val dbId get() = AppwriteClient.DATABASE_ID

    /** SUPERVISOR: guarda en Appwrite lo que generó el asistente. */
    suspend fun guardar(
        supervisor: AppUser,
        tipo: String,
        titulo: String,
        fecha: String,
        contenido: String
    ): Result<Unit> = appwriteSafe(TABLE_REPORTES) {
        db.createRow(
            databaseId = dbId,
            tableId = TABLE_REPORTES,
            rowId = ID.unique(),
            data = mapOf(
                "supervisorId" to supervisor.id,
                "supervisorNombre" to supervisor.alias,
                "tipo" to tipo,
                "titulo" to titulo,
                "fecha" to fecha,
                "contenido" to contenido,
                "estado" to ESTADO_PENDIENTE
            ),
            // El supervisor solo puede LEER su reporte; el estado lo cambia el gerente
            permissions = listOf(Permission.read(Role.user(supervisor.id)))
        )
        Unit
    }

    /** GERENTE: trae los reportes (más recientes primero). Se filtran por supervisor en la pantalla. */
    suspend fun listar(): Result<List<ReporteRemoto>> = appwriteSafe(TABLE_REPORTES) {
        db.listRows(
            databaseId = dbId,
            tableId = TABLE_REPORTES,
            queries = listOf(Query.orderDesc("\$createdAt"), Query.limit(100))
        ).rows.map { it.toReporte() }
    }

    /** GERENTE: marca un reporte como Revisado o Pendiente. */
    suspend fun cambiarEstado(id: String, estado: String): Result<Unit> =
        appwriteSafe(TABLE_REPORTES) {
            db.updateRow(
                databaseId = dbId,
                tableId = TABLE_REPORTES,
                rowId = id,
                data = mapOf("estado" to estado)
            )
            Unit
        }

    // ---------- datos de los gráficos (se guardan como JSON de texto) ----------

    fun codificarDatos(datos: List<Pair<String, Int>>): String =
        JSONArray().apply {
            datos.forEach { put(JSONObject().put("e", it.first).put("v", it.second)) }
        }.toString()

    fun decodificarDatos(texto: String): List<Pair<String, Int>> = try {
        val arreglo = JSONArray(texto)
        List(arreglo.length()) { i ->
            val o = arreglo.getJSONObject(i)
            o.getString("e") to o.getInt("v")
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun Row<Map<String, Any>>.toReporte() = ReporteRemoto(
        id = id,
        supervisorId = data["supervisorId"] as? String ?: "",
        supervisorNombre = data["supervisorNombre"] as? String ?: "",
        tipo = data["tipo"] as? String ?: TIPO_REPORTE,
        titulo = data["titulo"] as? String ?: "",
        fecha = data["fecha"] as? String ?: "",
        contenido = data["contenido"] as? String ?: "",
        estado = data["estado"] as? String ?: ESTADO_PENDIENTE
    )
}