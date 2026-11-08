package com.example.krono_corp.data

import com.example.krono_corp.negocio.GraficoGenerado
import com.example.krono_corp.negocio.ReporteGenerado
import io.appwrite.ID
import io.appwrite.Permission
import io.appwrite.Query
import io.appwrite.Role
import io.appwrite.models.InputFile
import io.appwrite.models.Row
import java.io.File

/** Historial de un usuario (DUENO o SUPERVISOR) ya convertido a los modelos de las pantallas. */
class HistorialCargado(
    val reportes: List<ReporteGenerado>,
    val graficos: List<GraficoGenerado>
)

/**
 * Guarda el historial de reportes (texto, PDF, Excel) y de gráficos de cada usuario
 * en la tabla "reportes".
 *
 * Columnas usadas: scope, ownerId, titulo, fecha, contenido
 *   + archivoId y archivoNombre (las mismas que ya usa ReportesRepository) cuando hay PDF/Excel.
 *
 * Los PDF/Excel se SUBEN al bucket de Appwrite al guardar el reporte, así siguen disponibles
 * aunque el usuario cierre la app, cambie de teléfono o se borre la caché.
 *
 * scope = HISTORIAL_REPORTE o HISTORIAL_GRAFICO, para no mezclarse con otras filas de la tabla.
 */
object HistorialRepository {

    private const val TABLE = ReportesRepository.TABLE_REPORTES
    private const val SCOPE_REPORTE = "HISTORIAL_REPORTE"
    private const val SCOPE_GRAFICO = "HISTORIAL_GRAFICO"
    private const val SEPARADOR = "|"

    private val db get() = AppwriteClient.tablesDB
    private val storage get() = AppwriteClient.storage
    private val dbId get() = AppwriteClient.DATABASE_ID

    /**
     * [reporte].archivos trae las RUTAS LOCALES de los archivos que acaba de generar el chat.
     * Aquí se suben al bucket y en la fila se guardan sus IDs y nombres.
     */
    suspend fun guardarReporte(ownerId: String, reporte: ReporteGenerado): Result<Unit> =
        guardar(
            ownerId = ownerId,
            scope = SCOPE_REPORTE,
            titulo = reporte.titulo,
            fecha = reporte.fecha,
            contenido = reporte.contenido,
            rutasArchivos = reporte.archivos
        )

    suspend fun guardarGrafico(ownerId: String, grafico: GraficoGenerado): Result<Unit> =
        guardar(
            ownerId = ownerId,
            scope = SCOPE_GRAFICO,
            titulo = grafico.titulo,
            fecha = grafico.fecha,
            contenido = ReportesRepository.codificarDatos(grafico.datos),
            rutasArchivos = emptyList()
        )

    /** Trae el historial del usuario (el más reciente primero). */
    suspend fun cargar(ownerId: String): Result<HistorialCargado> = appwriteSafe("reportes") {
        val filas = db.listRows(
            databaseId = dbId,
            tableId = TABLE,
            queries = listOf(
                Query.equal("ownerId", ownerId),
                Query.orderDesc("\$createdAt"),
                Query.limit(200)
            )
        ).rows

        val reportes = filas.filter { texto(it, "scope") == SCOPE_REPORTE }
        val graficos = filas.filter { texto(it, "scope") == SCOPE_GRAFICO }

        HistorialCargado(
            // Los ids de las pantallas son Int: el más reciente tiene el número más alto
            reportes = reportes.mapIndexed { i, f ->
                ReporteGenerado(
                    id = reportes.size - i,
                    titulo = texto(f, "titulo"),
                    fecha = texto(f, "fecha"),
                    contenido = texto(f, "contenido"),
                    // Filas antiguas: rutas locales guardadas en la columna "archivos"
                    archivos = lista(texto(f, "archivos")),
                    // Filas nuevas: archivos guardados en el bucket
                    archivoIds = lista(texto(f, "archivoId")),
                    archivoNombres = lista(texto(f, "archivoNombre"))
                )
            },
            graficos = graficos.mapIndexed { i, f ->
                GraficoGenerado(
                    id = graficos.size - i,
                    titulo = texto(f, "titulo"),
                    fecha = texto(f, "fecha"),
                    datos = ReportesRepository.decodificarDatos(texto(f, "contenido"))
                )
            }
        )
    }

    // ---------- helpers ----------

    private suspend fun guardar(
        ownerId: String,
        scope: String,
        titulo: String,
        fecha: String,
        contenido: String,
        rutasArchivos: List<String>
    ): Result<Unit> = appwriteSafe("reportes") {
        // Solo el propio usuario puede leer su fila y sus archivos
        val permisos = listOf(Permission.read(Role.user(ownerId)))

        // 1) Subir los archivos al bucket (si alguno falla, el reporte igual se guarda como texto)
        val ids = mutableListOf<String>()
        val nombres = mutableListOf<String>()
        var errorSubida: String? = null
        for (ruta in rutasArchivos) {
            val archivo = File(ruta)
            if (!archivo.exists()) continue
            try {
                val subido = storage.createFile(
                    bucketId = ReportesRepository.BUCKET_ARCHIVOS,
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

        // 2) Guardar la fila del historial
        val datos = mutableMapOf<String, Any>(
            "scope" to scope,
            "ownerId" to ownerId,
            "titulo" to titulo,
            "fecha" to fecha,
            "contenido" to contenido
        )
        if (ids.isNotEmpty()) {
            datos["archivoId"] = ids.joinToString(SEPARADOR)
            datos["archivoNombre"] = nombres.joinToString(SEPARADOR)
        }

        db.createRow(
            databaseId = dbId,
            tableId = TABLE,
            rowId = ID.unique(),
            data = datos,
            permissions = permisos
        )

        if (errorSubida != null) {
            throw IllegalStateException(
                "El reporte se guardó, pero no se pudo subir el archivo: $errorSubida"
            )
        }
        Unit
    }

    private fun lista(valor: String): List<String> =
        valor.split(SEPARADOR).filter { it.isNotBlank() }

    private fun texto(fila: Row<Map<String, Any>>, columna: String): String =
        fila.data[columna] as? String ?: ""
}