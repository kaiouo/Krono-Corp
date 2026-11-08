package com.example.krono_corp.data

import io.appwrite.Permission
import io.appwrite.Query
import io.appwrite.Role
import io.appwrite.exceptions.AppwriteException
import io.appwrite.models.Row
import kotlinx.coroutines.CancellationException
import java.security.SecureRandom

/** Enlace de invitación vigente de un gerente. */
data class Invitacion(
    val codigo: String,
    val enlace: String,
    val expiraEn: Long          // milisegundos desde 1970
)

/**
 * Invitaciones para registrar supervisores (tabla "invitaciones").
 *
 * - El ID de la fila ES el código del enlace (aleatorio, 20 caracteres).
 * - Columnas: gerenteId (text), expiraEn (integer), activo (boolean).
 * - Cada fila es legible por cualquiera (así se puede validar ANTES de crear la cuenta)
 *   y solo el gerente que la creó puede modificarla o borrarla.
 *
 * Enlace que se comparte: https://kronocorpfp.netlify.app/registro.html?codigo=XXXX
 * La página puente abre la app con kronocorp://registro?codigo=XXXX
 */
object InvitacionRepository {

    private const val TABLA = "invitaciones"
    private const val TABLE_ID = "6aee56ba00034d713842"          // ID de la tabla en Appwrite
    private const val URL_BASE = "https://kronocorpfp.netlify.app/registro.html"
    private const val VIGENCIA_MS = 7L * 24 * 60 * 60 * 1000   // 7 días
    private const val LARGO_CODIGO = 20
    private const val ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"

    private const val MSG_INVALIDA =
        "Este enlace de invitación no es válido, ya venció o fue desactivado. " +
                "Pídele a tu gerente uno nuevo."

    private val db get() = AppwriteClient.tablesDB
    private val dbId get() = AppwriteClient.DATABASE_ID

    /** Crea un enlace nuevo de 7 días. Los enlaces anteriores de este gerente se desactivan. */
    suspend fun crear(gerenteId: String): Result<Invitacion> =
        appwriteSafe(TABLA) {
            desactivarVigentes(gerenteId)

            val codigo = generarCodigo()
            val expira = System.currentTimeMillis() + VIGENCIA_MS
            db.createRow(
                databaseId = dbId,
                tableId = TABLE_ID,
                rowId = codigo,
                data = mapOf(
                    "gerenteId" to gerenteId,
                    "expiraEn" to expira,
                    "activo" to true
                ),
                permissions = listOf(
                    Permission.read(Role.any()),
                    Permission.update(Role.user(gerenteId)),
                    Permission.delete(Role.user(gerenteId))
                )
            )
            Invitacion(codigo = codigo, enlace = enlaceDe(codigo), expiraEn = expira)
        }

    /** Enlace vigente (activo y sin vencer) del gerente, o null si no tiene ninguno. */
    suspend fun vigente(gerenteId: String): Result<Invitacion?> =
        appwriteSafe(TABLA) {
            filasVigentes(gerenteId)
                .mapNotNull { it.toInvitacion() }
                .maxByOrNull { it.expiraEn }
        }

    /** Revisa el código sin crear nada. Devuelve el id del gerente dueño del enlace. */
    suspend fun validar(codigo: String): Result<String> =
        appwriteSafe(TABLA) { gerenteDe(codigo) }

    /**
     * Devuelve el id del gerente dueño del código, o lanza IllegalStateException con un
     * mensaje para el usuario si el código no existe, venció o fue desactivado.
     * (AuthRepository.register lo llama dentro de su propio manejo de errores.)
     */
    suspend fun gerenteDe(codigo: String): String {
        val limpio = codigo.trim()
        if (!Regex("[A-Za-z0-9_]{10,36}").matches(limpio)) {
            throw IllegalStateException(MSG_INVALIDA)
        }

        val fila = try {
            db.getRow(databaseId = dbId, tableId = TABLE_ID, rowId = limpio)
        } catch (e: AppwriteException) {
            if (e.code == 404) throw IllegalStateException(MSG_INVALIDA)
            throw e
        }

        val activo = fila.data["activo"] as? Boolean ?: true
        val expira = (fila.data["expiraEn"] as? Number)?.toLong() ?: 0L
        val gerenteId = fila.data["gerenteId"] as? String ?: ""
        if (!activo || expira <= System.currentTimeMillis() || gerenteId.isBlank()) {
            throw IllegalStateException(MSG_INVALIDA)
        }
        return gerenteId
    }

    // ---------- helpers ----------

    private fun enlaceDe(codigo: String) = "$URL_BASE?codigo=$codigo"

    private fun generarCodigo(): String {
        val azar = SecureRandom()
        return buildString(LARGO_CODIGO) {
            repeat(LARGO_CODIGO) { append(ALFABETO[azar.nextInt(ALFABETO.length)]) }
        }
    }

    /** Filas del gerente que siguen activas y sin vencer (necesita el índice sobre gerenteId). */
    private suspend fun filasVigentes(gerenteId: String): List<Row<Map<String, Any>>> =
        db.listRows(
            databaseId = dbId,
            tableId = TABLE_ID,
            queries = listOf(Query.equal("gerenteId", gerenteId), Query.limit(50))
        ).rows.filter {
            val activo = it.data["activo"] as? Boolean ?: true
            val expira = (it.data["expiraEn"] as? Number)?.toLong() ?: 0L
            activo && expira > System.currentTimeMillis()
        }

    /** Desactiva los enlaces anteriores. Si algo falla no impide crear el nuevo. */
    private suspend fun desactivarVigentes(gerenteId: String) {
        try {
            filasVigentes(gerenteId).forEach { fila ->
                db.updateRow(
                    databaseId = dbId,
                    tableId = TABLE_ID,
                    rowId = fila.id,
                    data = mapOf("activo" to false)
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // se ignora: el enlace nuevo igual se crea
        }
    }

    private fun Row<Map<String, Any>>.toInvitacion(): Invitacion? {
        val expira = (data["expiraEn"] as? Number)?.toLong() ?: return null
        return Invitacion(codigo = id, enlace = enlaceDe(id), expiraEn = expira)
    }
}