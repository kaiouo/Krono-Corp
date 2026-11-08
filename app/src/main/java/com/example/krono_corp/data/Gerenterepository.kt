package com.example.krono_corp.data

import io.appwrite.Query
import io.appwrite.models.Row

/** Una cuenta con rol SUPERVISOR (fila de la tabla "usuarios"). */
data class CuentaSupervisor(
    val id: String,          // = ID de la cuenta = ID de la fila
    val alias: String,
    val email: String,
    val phone: String,
    val sucursal: String,    // ID de la sucursal (tabla sucursales); vacío = todavía sin asignar
    val gerenteId: String,   // vacío = todavía sin gerente
    val invitadoPor: String = ""  // id del gerente cuyo enlace de invitación usó al registrarse
)

/**
 * Operaciones del GERENTE sobre las cuentas SUPERVISOR.
 * Requiere que la cuenta del gerente tenga la etiqueta "gerente" en Appwrite
 * (la tabla "usuarios" le da Read y Update a esa etiqueta).
 */
object GerenteRepository {

    private val db get() = AppwriteClient.tablesDB
    private val dbId get() = AppwriteClient.DATABASE_ID

    /** Supervisores que el gerente puede ver: los de su equipo y los que invitó y aún no asigna */
    suspend fun supervisoresVisibles(gerenteId: String): Result<List<CuentaSupervisor>> =
        appwriteSafe("usuarios") {
            cuentasSupervisor().filter {
                it.gerenteId == gerenteId || (it.gerenteId.isBlank() && it.invitadoPor == gerenteId)
            }
        }

    /** Supervisores que controla este gerente */
    suspend fun supervisoresDe(gerenteId: String): Result<List<CuentaSupervisor>> =
        appwriteSafe("usuarios") {
            cuentasSupervisor().filter { it.gerenteId == gerenteId }
        }

    /** Cuentas SUPERVISOR que se registraron pero aún no tienen gerente (de cualquier gerente) */
    suspend fun sinAsignar(): Result<List<CuentaSupervisor>> =
        appwriteSafe("usuarios") {
            cuentasSupervisor().filter { it.gerenteId.isBlank() }
        }

    /**
     * Cuentas SUPERVISOR que se registraron con el enlace de ESTE gerente
     * y todavía no tienen sucursal asignada.
     */
    suspend fun sinAsignarDe(gerenteId: String): Result<List<CuentaSupervisor>> =
        appwriteSafe("usuarios") {
            cuentasSupervisor().filter { it.gerenteId.isBlank() && it.invitadoPor == gerenteId }
        }

    /**
     * Agrega al supervisor al equipo del gerente y lo ubica en una sucursal.
     * [sucursalId] es el ID de la fila de la tabla sucursales.
     */
    suspend fun asignar(supervisorId: String, gerenteId: String, sucursalId: String): Result<Unit> =
        appwriteSafe("usuarios") {
            db.updateRow(
                databaseId = dbId,
                tableId = AppwriteClient.TABLE_ID,
                rowId = supervisorId,
                data = mapOf("gerenteId" to gerenteId, "sucursal" to sucursalId)
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
        gerenteId = data["gerenteId"] as? String ?: "",
        invitadoPor = data["invitadoPor"] as? String ?: ""
    )
}