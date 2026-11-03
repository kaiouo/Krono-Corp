package com.example.krono_corp.data

import io.appwrite.ID
import io.appwrite.Query
import io.appwrite.exceptions.AppwriteException
import io.appwrite.models.Row
import kotlinx.coroutines.CancellationException
import java.io.IOException

/** Reemplaza a supervisores.json. El id ahora es el ID de la fila en Appwrite. */
data class SupervisorEquipo(
    val id: String,
    val nombreSupervisor: String,
    val gmail: String,
    val phoneNumber: String,
    val equipo: String
)

/** Reemplaza a datosNorte.json, datosCentro.json y datosSur.json (una sola tabla con la columna equipo). */
data class VentaIntegrante(
    val equipo: String,
    val integrante: String,
    val ventasRealizadas: Int
)

/** Equipos que existen en la empresa */
val EQUIPOS = listOf("Norte", "Centro", "Sur")

object SupervisoresRepository {

    // Table ID de cada tabla (créalas con estos IDs en Appwrite o cambia los valores aquí)
    const val TABLE_SUPERVISORES = "6ae55abc000e8405dde2"
    const val TABLE_VENTAS = "6ae55ad20032f98be2a2"

    private val db get() = AppwriteClient.tablesDB
    private val dbId get() = AppwriteClient.DATABASE_ID

    // ---------- Supervisores ----------

    suspend fun listarSupervisores(): Result<List<SupervisorEquipo>> = safe(TABLE_SUPERVISORES) {
        db.listRows(
            databaseId = dbId,
            tableId = TABLE_SUPERVISORES,
            queries = listOf(Query.limit(100))
        ).rows
            .map { it.toSupervisor() }
            .sortedWith(compareBy({ EQUIPOS.indexOf(it.equipo) }, { it.nombreSupervisor }))
    }

    suspend fun agregarSupervisor(
        nombre: String,
        gmail: String,
        telefono: String,
        equipo: String
    ): Result<SupervisorEquipo> = safe(TABLE_SUPERVISORES) {
        db.createRow(
            databaseId = dbId,
            tableId = TABLE_SUPERVISORES,
            rowId = ID.unique(),
            data = mapOf(
                "nombreSupervisor" to nombre,
                "gmail" to gmail,
                "phoneNumber" to telefono,
                "equipo" to equipo
            )
        ).toSupervisor()
    }

    suspend fun eliminarSupervisor(id: String): Result<Unit> = safe(TABLE_SUPERVISORES) {
        db.deleteRow(databaseId = dbId, tableId = TABLE_SUPERVISORES, rowId = id)
        Unit
    }

    // ---------- Ventas por integrante ----------

    suspend fun listarVentas(): Result<List<VentaIntegrante>> = safe(TABLE_VENTAS) {
        db.listRows(
            databaseId = dbId,
            tableId = TABLE_VENTAS,
            queries = listOf(Query.limit(100))
        ).rows.map { it.toVenta() }
    }

    /** Carga datos de prueba en la tabla ventas (para probar el reporte sin escribir filas a mano). */
    suspend fun cargarVentasEjemplo(): Result<Int> = safe(TABLE_VENTAS) {
        val ejemplo = listOf(
            VentaIntegrante("Norte", "Carlos Mejía", 14),
            VentaIntegrante("Norte", "Lucía Pérez", 18),
            VentaIntegrante("Norte", "Mario Rivas", 11),
            VentaIntegrante("Centro", "Sofía Castro", 20),
            VentaIntegrante("Centro", "Daniel Flores", 15),
            VentaIntegrante("Centro", "Elena Ramos", 17),
            VentaIntegrante("Sur", "Jorge Alvarado", 9),
            VentaIntegrante("Sur", "Camila Torres", 13),
            VentaIntegrante("Sur", "Pedro Navarro", 10)
        )
        ejemplo.forEach { v ->
            db.createRow(
                databaseId = dbId,
                tableId = TABLE_VENTAS,
                rowId = ID.unique(),
                data = mapOf(
                    "equipo" to v.equipo,
                    "integrante" to v.integrante,
                    "ventasRealizadas" to v.ventasRealizadas
                )
            )
        }
        ejemplo.size
    }

    // ---------- helpers ----------

    private fun Row<Map<String, Any>>.toSupervisor() = SupervisorEquipo(
        id = id,
        nombreSupervisor = data["nombreSupervisor"] as? String ?: "",
        gmail = data["gmail"] as? String ?: "",
        phoneNumber = data["phoneNumber"] as? String ?: "",
        equipo = data["equipo"] as? String ?: ""
    )

    private fun Row<Map<String, Any>>.toVenta() = VentaIntegrante(
        equipo = data["equipo"] as? String ?: "",
        integrante = data["integrante"] as? String ?: "",
        // Los números pueden llegar como Int o Double según el parser
        ventasRealizadas = (data["ventasRealizadas"] as? Number)?.toInt() ?: 0
    )

    private suspend fun <T> safe(tabla: String, block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(IllegalStateException(e.toMensaje(tabla)))
    }

    private fun Exception.toMensaje(tabla: String): String = when (this) {
        is AppwriteException -> when (code) {
            401, 403 -> "Sin permisos en la tabla «$tabla». Revisa Settings > Permissions en Appwrite."
            404 -> "No se encontró la base de datos o la tabla «$tabla». Revisa DATABASE_ID y el ID de la tabla."
            else -> message ?: "Error del servidor (código $code)."
        }
        is IOException -> "Sin conexión a internet."
        else -> message ?: "Ocurrió un error inesperado."
    }
}