package com.example.krono_corp.data

import io.appwrite.Query
import io.appwrite.exceptions.AppwriteException
import io.appwrite.models.Row
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Un supervisor que se registró usando el enlace de invitación de un gerente. */
data class RegistroInvitado(
    val id: String,
    val alias: String,
    val email: String,
    val telefono: String,
    val registradoEn: Long,   // milisegundos desde 1970 (0 si no se pudo leer)
    val asignado: Boolean     // true si el gerente ya lo ubicó en una sucursal
)

/**
 * Lee de la tabla "usuarios" a las personas que se registraron con el enlace de un gerente.
 * AuthRepository.register guarda "invitadoPor" = id del gerente en cada una de esas cuentas,
 * y Appwrite guarda la fecha y hora de creación de la fila ($createdAt).
 */
object RegistrosInvitacionRepository {

    private val db get() = AppwriteClient.tablesDB
    private val dbId get() = AppwriteClient.DATABASE_ID

    /** Registros del gerente, del más reciente al más antiguo. */
    suspend fun listar(gerenteId: String): Result<List<RegistroInvitado>> = try {
        val filas = db.listRows(
            databaseId = dbId,
            tableId = AppwriteClient.TABLE_ID,
            queries = listOf(Query.equal("invitadoPor", gerenteId), Query.limit(100))
        ).rows
        Result.success(filas.map { it.toRegistro() }.sortedByDescending { it.registradoEn })
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(IllegalStateException(e.toMensaje()))
    }

    // ---------- helpers ----------

    private fun Row<Map<String, Any>>.toRegistro(): RegistroInvitado {
        val email = data["email"] as? String ?: ""
        return RegistroInvitado(
            id = id,
            alias = data["alias"] as? String ?: email.substringBefore("@"),
            email = email,
            telefono = data["phoneNumber"] as? String ?: "",
            registradoEn = parsearFecha(createdAt),
            asignado = (data["gerenteId"] as? String ?: "").isNotBlank()
        )
    }

    /** Appwrite entrega la fecha en UTC, ej. 2026-11-08T23:23:46.921+00:00 */
    private fun parsearFecha(iso: String): Long = try {
        val formato = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
        formato.parse(iso.take(19))?.time ?: 0L
    } catch (_: Exception) {
        0L
    }

    private fun Exception.toMensaje(): String = when (this) {
        is AppwriteException -> when {
            code == 401 || code == 403 ->
                "Sin permisos para leer los supervisores registrados. " +
                        "Revisa Settings > Permissions de la tabla usuarios en Appwrite."
            message?.contains("index", ignoreCase = true) == true ->
                "Falta un índice sobre la columna invitadoPor en la tabla usuarios " +
                        "(Appwrite > usuarios > Indexes > Create index, tipo Key)."
            else -> message ?: "Error del servidor (código $code)."
        }
        is IOException -> "Sin conexión a internet."
        else -> message ?: "Ocurrió un error inesperado."
    }
}