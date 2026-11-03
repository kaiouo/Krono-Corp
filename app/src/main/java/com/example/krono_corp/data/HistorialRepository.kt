package com.example.krono_corp.data

import com.example.krono_corp.negocio.GraficoGenerado
import com.example.krono_corp.negocio.ReporteGenerado
import io.appwrite.ID
import io.appwrite.Permission
import io.appwrite.Query
import io.appwrite.Role
import io.appwrite.models.Row

/** Historial de un usuario (DUENO o SUPERVISOR) ya convertido a los modelos de las pantallas. */
class HistorialCargado(
    val reportes: List<ReporteGenerado>,
    val graficos: List<GraficoGenerado>
)

/**
 * Guarda el historial de reportes (texto, PDF, Excel) y de gráficos de cada usuario
 * en la tabla "reportes", usando solo estas columnas:
 *   scope, ownerId, titulo, fecha, contenido  (+ archivos, solo cuando hay PDF/Excel)
 *
 * scope = HISTORIAL_REPORTE o HISTORIAL_GRAFICO, para no mezclarse con otras filas de la tabla.
 */
object HistorialRepository {

    private const val TABLE = ReportesRepository.TABLE_REPORTES
    private const val SCOPE_REPORTE = "HISTORIAL_REPORTE"
    private const val SCOPE_GRAFICO = "HISTORIAL_GRAFICO"
    private const val SEPARADOR = "|"

    private val db get() = AppwriteClient.tablesDB
    private val dbId get() = AppwriteClient.DATABASE_ID

    suspend fun guardarReporte(ownerId: String, reporte: ReporteGenerado): Result<Unit> =
        guardar(
            ownerId = ownerId,
            scope = SCOPE_REPORTE,
            titulo = reporte.titulo,
            fecha = reporte.fecha,
            contenido = reporte.contenido,
            archivos = reporte.archivos
        )

    suspend fun guardarGrafico(ownerId: String, grafico: GraficoGenerado): Result<Unit> =
        guardar(
            ownerId = ownerId,
            scope = SCOPE_GRAFICO,
            titulo = grafico.titulo,
            fecha = grafico.fecha,
            contenido = ReportesRepository.codificarDatos(grafico.datos),
            archivos = emptyList()
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
                    archivos = texto(f, "archivos").split(SEPARADOR).filter { it.isNotBlank() }
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
        archivos: List<String>
    ): Result<Unit> = appwriteSafe("reportes") {
        val datos = mutableMapOf<String, Any>(
            "scope" to scope,
            "ownerId" to ownerId,
            "titulo" to titulo,
            "fecha" to fecha,
            "contenido" to contenido
        )
        // La columna "archivos" solo se envía cuando hay PDF/Excel
        if (archivos.isNotEmpty()) datos["archivos"] = archivos.joinToString(SEPARADOR)

        db.createRow(
            databaseId = dbId,
            tableId = TABLE,
            rowId = ID.unique(),
            data = datos,
            permissions = listOf(Permission.read(Role.user(ownerId)))
        )
        Unit
    }

    private fun texto(fila: Row<Map<String, Any>>, columna: String): String =
        fila.data[columna] as? String ?: ""
}