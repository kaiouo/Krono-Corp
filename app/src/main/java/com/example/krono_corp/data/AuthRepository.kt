package com.example.krono_corp.data

import android.util.Log
import io.appwrite.ID
import io.appwrite.Permission
import io.appwrite.Role
import io.appwrite.exceptions.AppwriteException
import io.appwrite.models.User
import kotlinx.coroutines.CancellationException
import java.io.IOException

data class AppUser(
    val id: String,
    val alias: String,
    val email: String,
    val phone: String,
    val role: Rol
)

/**
 * Autenticación -> Appwrite Auth (correo + contraseña, la contraseña se guarda cifrada).
 * Perfil (alias, email, phoneNumber, role) -> tabla "usuarios".
 * El ID de la fila es el MISMO ID de la cuenta, así el login encuentra su perfil directamente.
 */
object AuthRepository {

    private const val TAG = "KronoAuth"

    private val account get() = AppwriteClient.account
    private val tablesDB get() = AppwriteClient.tablesDB

    /** Usuario con sesión activa en el dispositivo (o null si no hay). */
    suspend fun currentUser(): AppUser? = try {
        perfilDe(account.get())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    suspend fun register(
        email: String,
        phone: String,
        password: String,
        rol: Rol
    ): Result<AppUser> = safe {
        val alias = email.substringBefore("@")

        // 1) Cuenta en Appwrite Auth
        val cuenta = account.create(
            userId = ID.unique(),
            email = email,
            password = password,
            name = alias
        )
        Log.i(TAG, "REGISTRO paso 1 OK: cuenta creada ${cuenta.id}")

        // 2) Sesión (necesaria para tener permiso de escribir en la tabla)
        closeSessionQuietly()
        val sesion = account.createEmailPasswordSession(email = email, password = password)
        Log.i(TAG, "REGISTRO paso 2 OK: sesión creada ${sesion.id}")

        // 3) Perfil en la tabla "usuarios"
        try {
            tablesDB.createRow(
                databaseId = AppwriteClient.DATABASE_ID,
                tableId = AppwriteClient.TABLE_ID,
                rowId = cuenta.id,
                data = mapOf(
                    "alias" to alias,
                    "email" to email,
                    "phoneNumber" to phone,
                    "role" to rol.codigo
                ),
                // Solo el dueño de la fila puede leerla y editarla
                permissions = listOf(
                    Permission.read(Role.user(cuenta.id)),
                    Permission.update(Role.user(cuenta.id))
                )
            )
            Log.i(TAG, "REGISTRO paso 3 OK: fila creada en usuarios")
        } catch (e: Exception) {
            closeSessionQuietly()
            throw e
        }

        AppUser(id = cuenta.id, alias = alias, email = email, phone = phone, role = rol)
    }

    suspend fun login(email: String, password: String): Result<AppUser> = safe {
        closeSessionQuietly() // evita el error "ya existe una sesión activa"
        val sesion = account.createEmailPasswordSession(email = email, password = password)
        Log.i(TAG, "LOGIN paso 1 OK: sesión creada ${sesion.id}")
        try {
            perfilDe(account.get())
        } catch (e: AppwriteException) {
            closeSessionQuietly()
            if (e.code == 404) {
                throw IllegalStateException(
                    "Tu cuenta existe pero no tiene perfil en la tabla usuarios. " +
                            "Elimínala en Auth > Users y regístrate de nuevo."
                )
            }
            throw e
        }
    }

    suspend fun logout() {
        closeSessionQuietly()
    }

    // ---------- helpers ----------

    private suspend fun perfilDe(cuenta: User<Map<String, Any>>): AppUser {
        val fila = tablesDB.getRow(
            databaseId = AppwriteClient.DATABASE_ID,
            tableId = AppwriteClient.TABLE_ID,
            rowId = cuenta.id
        )
        val d = fila.data
        return AppUser(
            id = cuenta.id,
            alias = d["alias"] as? String ?: cuenta.email.substringBefore("@"),
            email = d["email"] as? String ?: cuenta.email,
            phone = d["phoneNumber"] as? String ?: "",
            role = Rol.desdeCodigo(d["role"] as? String)
        )
    }

    /** Cierra la sesión solo si realmente hay una (evita llamar como invitado). */
    private suspend fun closeSessionQuietly() {
        try {
            account.get()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return // no hay sesión activa: nada que cerrar
        }
        try {
            account.deleteSession(sessionId = "current")
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // se ignora
        }
    }

    private suspend fun <T> safe(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val aw = e as? AppwriteException
        Log.e(TAG, "FALLO ${e::class.simpleName} code=${aw?.code} type=${aw?.type} msg=${e.message}", e)
        Result.failure(IllegalStateException(e.toMensaje()))
    }

    private fun Exception.toMensaje(): String = when (this) {
        is AppwriteException -> when (type) {
            "user_already_exists", "user_email_already_exists" ->
                "Este correo ya está registrado. Inicia sesión."
            "user_invalid_credentials" -> "Correo o contraseña incorrectos."
            "general_rate_limit_exceeded" -> "Demasiados intentos. Espera un momento."
            "user_unauthorized" ->
                "Sin permisos en la tabla usuarios. Revisa Settings > Permissions en Appwrite."
            else -> message ?: "Error del servidor (código $code)."
        }
        is IOException -> "Sin conexión a internet."
        else -> message ?: "Ocurrió un error inesperado."
    }
}