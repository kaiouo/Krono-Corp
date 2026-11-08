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
    val role: Rol,
    val emailVerificado: Boolean = false,
    val sucursal: String = "",   // ID de la sucursal asignada (solo supervisores)
    val gerenteId: String = ""   // gerente al que pertenece (solo supervisores)
)

/** Datos para completar el segundo paso del login (código enviado por correo). */
data class DesafioLogin(
    val userId: String,
    val email: String
)

/**
 * Autenticación -> Appwrite Auth (correo + contraseña, la contraseña se guarda cifrada).
 * Perfil (alias, email, phoneNumber, role) -> tabla "usuarios".
 * El ID de la fila es el MISMO ID de la cuenta, así el login encuentra su perfil directamente.
 *
 * Registro: crea cuenta + perfil, envía el enlace de verificación y deja la sesión cerrada.
 * Login en 2 pasos: (1) correo + contraseña + correo verificado, (2) código de 6 dígitos por correo.
 */
object AuthRepository {

    private const val TAG = "KronoAuth"

    // Página puente (Netlify). "?tipo=verify" le indica que abra kronocorp://verify
    private const val URL_RECUPERACION = "https://kronocorpfp.netlify.app/"
    private const val URL_VERIFICACION = "https://kronocorpfp.netlify.app/?tipo=verify"

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

    // ---------- Registro ----------

    /**
     * Crea la cuenta y el perfil, y envía el correo de verificación.
     * Al terminar NO deja sesión iniciada: la persona debe verificar su correo e iniciar sesión.
     *
     * codigoInvitacion: viene del enlace que genera un gerente. Con un código válido el rol
     * SIEMPRE es Supervisor y la cuenta queda marcada con "invitadoPor" = id del gerente.
     * Sin código no se permite registrarse como Supervisor.
     */
    suspend fun register(
        email: String,
        phone: String,
        password: String,
        rol: Rol,
        codigoInvitacion: String? = null
    ): Result<Unit> = safe {
        val alias = email.substringBefore("@")

        // 0) Si el registro viene de un enlace, se valida ANTES de crear la cuenta
        closeSessionQuietly()
        val invitadoPor: String? = codigoInvitacion
            ?.takeIf { it.isNotBlank() }
            ?.let { InvitacionRepository.gerenteDe(it) }
        if (invitadoPor == null && rol == Rol.SUPERVISOR) {
            throw IllegalStateException(
                "Para registrarte como Supervisor necesitas el enlace de invitación de tu gerente."
            )
        }
        val rolFinal = if (invitadoPor != null) Rol.SUPERVISOR else rol
        val datosPerfil = mutableMapOf<String, Any>(
            "alias" to alias,
            "email" to email,
            "phoneNumber" to phone,
            "role" to rolFinal.codigo
        )
        if (invitadoPor != null) datosPerfil["invitadoPor"] = invitadoPor

        // 1) Cuenta en Appwrite Auth
        val cuenta = account.create(
            userId = ID.unique(),
            email = email,
            password = password,
            name = alias
        )
        Log.i(TAG, "REGISTRO paso 1 OK: cuenta creada ${cuenta.id}")

        // 2) Sesión temporal (necesaria para escribir en la tabla y pedir la verificación)
        closeSessionQuietly()
        val sesion = account.createEmailPasswordSession(email = email, password = password)
        Log.i(TAG, "REGISTRO paso 2 OK: sesión creada ${sesion.id}")

        // 3) Perfil en la tabla "usuarios"
        try {
            tablesDB.createRow(
                databaseId = AppwriteClient.DATABASE_ID,
                tableId = AppwriteClient.TABLE_ID,
                rowId = cuenta.id,
                data = datosPerfil,
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

        // 4) Correo de verificación. Si falla no se cancela el registro:
        //    al intentar iniciar sesión sin verificar, se reenvía automáticamente.
        try {
            account.createEmailVerification(url = URL_VERIFICACION)
            Log.i(TAG, "REGISTRO paso 4 OK: correo de verificación enviado")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "REGISTRO paso 4: no se pudo enviar la verificación: ${e.message}")
        }

        // 5) Se cierra la sesión: hasta verificar el correo y poner el código no hay acceso
        closeSessionQuietly()
    }

    // ---------- Inicio de sesión en 2 pasos ----------

    /**
     * Paso 1: valida correo + contraseña y que el correo esté verificado.
     * Si todo está bien cierra la sesión temporal y envía el código de 6 dígitos al correo.
     */
    suspend fun iniciarLogin(email: String, password: String): Result<DesafioLogin> = safe {
        closeSessionQuietly() // evita el error "ya existe una sesión activa"

        // Si la contraseña es incorrecta, aquí lanza user_invalid_credentials
        account.createEmailPasswordSession(email = email, password = password)
        val cuenta = account.get()
        Log.i(TAG, "LOGIN paso 1 OK: credenciales correctas ${cuenta.id}")

        if (!cuenta.emailVerification) {
            // Reenvía el enlace usando esta sesión temporal y no deja entrar
            try {
                account.createEmailVerification(url = URL_VERIFICACION)
                Log.i(TAG, "LOGIN: correo no verificado, se reenvió el enlace")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "LOGIN: no se pudo reenviar la verificación: ${e.message}")
            }
            closeSessionQuietly()
            throw IllegalStateException(
                "Tu correo aún no está verificado. Te reenviamos el enlace de verificación; " +
                        "ábrelo desde tu correo y luego inicia sesión."
            )
        }

        // Credenciales correctas y correo verificado: se cierra la sesión y se pide el código
        closeSessionQuietly()
        account.createEmailToken(userId = cuenta.id, email = cuenta.email)
        Log.i(TAG, "LOGIN paso 2 OK: código enviado a ${cuenta.email}")

        DesafioLogin(userId = cuenta.id, email = cuenta.email)
    }

    /** Paso 2: canjea el código de 6 dígitos por la sesión real y carga el perfil. */
    suspend fun confirmarCodigo(userId: String, codigo: String): Result<AppUser> = safe {
        closeSessionQuietly()
        account.createSession(userId = userId, secret = codigo.trim())
        Log.i(TAG, "LOGIN paso 3 OK: código correcto, sesión creada")
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

    /** Envía un código nuevo (el anterior deja de servir). */
    suspend fun reenviarCodigo(userId: String, email: String): Result<Unit> = safe {
        account.createEmailToken(userId = userId, email = email)
        Unit
    }

    suspend fun logout() {
        closeSessionQuietly()
    }

    // ---------- Recuperar contraseña (botón "¿Olvidé mi contraseña?") ----------

    /** Envía el correo de recuperación. El enlace abre la app directo (deep link). */
    suspend fun solicitarRecuperacion(email: String): Result<Unit> = safe {
        account.createRecovery(email = email, url = URL_RECUPERACION)
        Unit
    }

    /** Se llama cuando el usuario abre el enlace del correo y escribe su nueva contraseña. */
    suspend fun confirmarRecuperacion(userId: String, secret: String, nuevaContrasena: String): Result<Unit> = safe {
        account.updateRecovery(userId = userId, secret = secret, password = nuevaContrasena)
        Unit
    }

    // ---------- Verificación de email ----------

    /** Envía el correo de verificación a la cuenta con sesión activa. */
    suspend fun enviarVerificacion(): Result<Unit> = safe {
        account.createEmailVerification(url = URL_VERIFICACION)
        Unit
    }

    /** Se llama cuando el usuario abre el enlace del correo de verificación. */
    suspend fun confirmarVerificacion(userId: String, secret: String): Result<Unit> = safe {
        account.updateEmailVerification(userId = userId, secret = secret)
        Unit
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
            role = Rol.desdeCodigo(d["role"] as? String),
            emailVerificado = cuenta.emailVerification,
            sucursal = d["sucursal"] as? String ?: "",
            gerenteId = d["gerenteId"] as? String ?: ""
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
            "user_invalid_token" -> "El código es incorrecto o ya venció."
            "general_rate_limit_exceeded" -> "Demasiados intentos. Espera un momento."
            "user_unauthorized" ->
                "Sin permisos en la tabla usuarios. Revisa Settings > Permissions en Appwrite."
            else -> message ?: "Error del servidor (código $code)."
        }
        is IOException -> "Sin conexión a internet."
        else -> message ?: "Ocurrió un error inesperado."
    }
}