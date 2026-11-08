package com.example.krono_corp.data

import io.appwrite.ID
import io.appwrite.Permission
import io.appwrite.Query
import io.appwrite.Role
import io.appwrite.models.InputFile
import io.appwrite.models.Row
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Un reporte o gráfico que generó un SUPERVISOR (fila de la tabla "reportes"). */
data class ReporteRemoto(
    val id: String,
    val supervisorId: String,
    val supervisorNombre: String,
    val tipo: String,        // REPORTE o GRAFICO
    val titulo: String,
    val fecha: String,
    val contenido: String,   // texto del reporte, o JSON con los datos del gráfico
    val estado: String,      // Pendiente o Revisado
    val archivoIds: List<String> = emptyList(),      // IDs de los archivos en el bucket de Storage
    val archivoNombres: List<String> = emptyList()   // nombre de cada archivo (mismo orden que los IDs)
)

object ReportesRepository {

    const val TABLE_REPORTES = "6ae54d25001bd5664634"
    const val BUCKET_ARCHIVOS = "6aef0cfa000d2b278694"
    const val TIPO_REPORTE = "REPORTE"
    const val TIPO_GRAFICO = "GRAFICO"
    const val ESTADO_PENDIENTE = "Pendiente"
    const val ESTADO_REVISADO = "Revisado"

    // Separador para guardar varios archivos en una sola columna de texto
    private const val SEPARADOR = "|"

    private val db get() = AppwriteClient.tablesDB
    private val storage get() = AppwriteClient.storage
    private val dbId get() = AppwriteClient.DATABASE_ID

    /**
     * SUPERVISOR: guarda en Appwrite lo que generó el asistente.
     * Si hay archivos (Excel/PDF) los sube al bucket; los lee el supervisor y, por el permiso
     * de la etiqueta «gerente» del bucket, también su gerente.
     */
    suspend fun guardar(
        supervisor: AppUser,
        tipo: String,
        titulo: String,
        fecha: String,
        contenido: String,
        archivos: List<File> = emptyList()
    ): Result<Unit> = appwriteSafe(TABLE_REPORTES) {
        // Solo el propio supervisor puede leer su reporte/archivo. El gerente los lee por el
        // permiso de la etiqueta «gerente» en la tabla "reportes" y en el bucket de archivos
        // (Appwrite no deja que un usuario otorgue permisos a roles que él no tiene).
        val permisos = listOf(Permission.read(Role.user(supervisor.id)))

        // 1) Subir los archivos (si alguno falla, el reporte igual se guarda como texto)
        val ids = mutableListOf<String>()
        val nombres = mutableListOf<String>()
        var errorSubida: String? = null
        for (archivo in archivos) {
            if (!archivo.exists()) continue
            try {
                val subido = storage.createFile(
                    bucketId = BUCKET_ARCHIVOS,
                    fileId = ID.unique(),
                    file = InputFile.fromPath(archivo.absolutePath),
                    permissions = permisos
                )
                ids.add(subido.id)
                nombres.add(archivo.name)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                errorSubida = e.message ?: "error desconocido"
            }
        }

        // 2) Guardar la fila del reporte
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
                "estado" to ESTADO_PENDIENTE,
                "archivoId" to ids.joinToString(SEPARADOR),
                "archivoNombre" to nombres.joinToString(SEPARADOR)
            ),
            // El supervisor solo puede LEER su reporte; el estado lo cambia el gerente
            permissions = permisos
        )

        if (errorSubida != null) {
            throw IllegalStateException(
                "El reporte se guardó, pero no se pudo subir el archivo: $errorSubida"
            )
        }
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

    /**
     * SUPERVISOR: sus propios reportes y gráficos, del más reciente al más antiguo.
     * Appwrite solo le entrega las filas que tiene permiso de leer (las suyas); además se
     * filtra por supervisorId como segunda capa.
     */
    suspend fun listarDe(supervisorId: String): Result<List<ReporteRemoto>> =
        appwriteSafe(TABLE_REPORTES) {
            val todos = mutableListOf<ReporteRemoto>()
            var cursor: String? = null
            var paginas = 0
            while (paginas < 20) {
                val consultas = mutableListOf(Query.orderDesc("\$createdAt"), Query.limit(100))
                cursor?.let { consultas.add(Query.cursorAfter(it)) }
                val pagina = db.listRows(
                    databaseId = dbId,
                    tableId = TABLE_REPORTES,
                    queries = consultas
                ).rows
                todos += pagina.map { it.toReporte() }
                if (pagina.size < 100) break
                cursor = pagina.last().id
                paginas++
            }
            todos.filter { it.supervisorId == supervisorId }
        }

    /** Descarga el contenido de un archivo guardado en el bucket. */
    suspend fun descargar(archivoId: String): Result<ByteArray> =
        appwriteSafe("archivos de reportes") {
            storage.getFileDownload(bucketId = BUCKET_ARCHIVOS, fileId = archivoId)
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

    private fun lista(valor: Any?): List<String> =
        (valor as? String).orEmpty().split(SEPARADOR).filter { it.isNotBlank() }

    private fun Row<Map<String, Any>>.toReporte() = ReporteRemoto(
        id = id,
        supervisorId = data["supervisorId"] as? String ?: "",
        supervisorNombre = data["supervisorNombre"] as? String ?: "",
        tipo = data["tipo"] as? String ?: TIPO_REPORTE,
        titulo = data["titulo"] as? String ?: "",
        fecha = data["fecha"] as? String ?: "",
        contenido = data["contenido"] as? String ?: "",
        estado = data["estado"] as? String ?: ESTADO_PENDIENTE,
        archivoIds = lista(data["archivoId"]),
        archivoNombres = lista(data["archivoNombre"])
    )
}