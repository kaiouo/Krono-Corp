package com.example.krono_corp.data

import io.appwrite.exceptions.AppwriteException
import kotlinx.coroutines.CancellationException
import java.io.IOException

/** Ejecuta una llamada a Appwrite y devuelve un Result con un mensaje de error en español. */
internal suspend fun <T> appwriteSafe(recurso: String, bloque: suspend () -> T): Result<T> = try {
    Result.success(bloque())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(IllegalStateException(e.mensajeAppwrite(recurso)))
}

private fun Exception.mensajeAppwrite(recurso: String): String = when (this) {
    is AppwriteException -> when (code) {
        401, 403 ->
            "Sin permisos en «$recurso». Revisa Settings > Permissions de la tabla en Appwrite " +
                    "(y que la cuenta de gerente tenga la etiqueta «gerente»)."
        404 -> "No se encontró «$recurso». Revisa que la tabla exista con ese ID."
        400 -> "Appwrite rechazó la petición en «$recurso»: ${message ?: "datos no válidos"}"
        else -> message ?: "Error del servidor (código $code)."
    }
    is IOException -> "Sin conexión a internet."
    else -> message ?: "Ocurrió un error inesperado."
}