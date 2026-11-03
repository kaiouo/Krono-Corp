package com.example.krono_corp.data

import io.appwrite.Query
import io.appwrite.models.Row

/** Una cuenta con rol SUPERVISOR (fila de la tabla "usuarios"). */
data class CuentaSupervisor(
    val id: String,          // = ID de la cuenta = ID de la fila
    val alias: String,
    val email: String,
    val phone: String,
    val sucursal: String,    // vacío = todavía sin asignar
    val gerenteId: String    // vacío = todavía sin gerente
)

/**
 * Operaciones del GERENTE sobre las cuentas SUPERVISOR.
 * Requiere que la cuenta del gerente tenga la etiqueta "gerente" en Appwrite
 * (la tabla "usuarios" le da Read y Update a esa etiqueta).
 */
object GerenteRepository {

    private val db get() = AppwriteClient.tablesDB
    private val dbId get() = AppwriteClient.DATABASE_ID

    /** Supervisores que el gerente puede ver: los de su equipo y los que aún no tienen gerente */
    suspend fun supervisoresVisibles(gerenteId: String): Result<List<CuentaSupervisor>> =
        appwriteSafe("usuarios") {
            cuentasSupervisor().filter { it.gerenteId == gerenteId || it.gerenteId.isBlank() }
        }

    /** Supervisores que controla este gerente */
    suspend fun supervisoresDe(gerenteId: String): Result<List<CuentaSupervisor>> =
        appwriteSafe("usuarios") {
            cuentasSupervisor().filter { it.gerenteId == gerenteId }
        }

    /** Cuentas SUPERVISOR que se registraron pero aún no tienen gerente */
    suspend fun sinAsignar(): Result<List<CuentaSupervisor>> =
        appwriteSafe("usuarios") {
            cuentasSupervisor().filter { it.gerenteId.isBlank() }
        }

    /** Agrega al supervisor al equipo del gerente y lo ubica en una sucursal */
    suspend fun asignar(supervisorId: String, gerenteId: String, sucursal: String): Result<Unit> =
        appwriteSafe("usuarios") {
            db.updateRow(
                databaseId = dbId,
                tableId = AppwriteClient.TABLE_ID,
                rowId = supervisorId,
                data = mapOf("gerenteId" to gerenteId, "sucursal" to sucursal)
            )
            Unit
        }

    /** Quita al supervisor del equipo (la cuenta no se borra; queda "sin asignar") */
    suspend fun quitar(supervisorId: String): Result<Unit> =
        appwriteSafe("usuarios") {
            db.updateRow(
                databaseId = dbId,
                tableId = AppwriteClient.TABLE_ID,
                rowId = supervisorId,
                data = mapOf("gerenteId" to "", "sucursal" to "")
            )
            Unit
        }

    // ---------- helpers ----------

    private suspend fun cuentasSupervisor(): List<CuentaSupervisor> =
        db.listRows(
            databaseId = dbId,
            tableId = AppwriteClient.TABLE_ID,
            queries = listOf(Query.limit(100))
        ).rows
            .filter { it.data["role"] == Rol.SUPERVISOR.codigo }
            .map { it.toCuenta() }
            .sortedWith(compareBy({ it.sucursal }, { it.alias.lowercase() }))

    private fun Row<Map<String, Any>>.toCuenta() = CuentaSupervisor(
        id = id,
        alias = data["alias"] as? String ?: "",
        email = data["email"] as? String ?: "",
        phone = data["phoneNumber"] as? String ?: "",
        sucursal = data["sucursal"] as? String ?: "",
        gerenteId = data["gerenteId"] as? String ?: ""
    )
}