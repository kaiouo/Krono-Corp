package com.example.krono_corp.ia

import com.example.krono_corp.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Un turno de la conversación: lo que preguntó el usuario y lo que respondió Krono. */
data class TurnoChat(val pregunta: String, val respuesta: String)

/**
 * Versión Android de GroqService.
 * Cambios respecto a la de consola:
 *  - java.net.http.HttpClient no existe en Android -> HttpURLConnection
 *  - System.getenv("GROQ_API_KEY") no existe en Android -> BuildConfig.GROQ_API_KEY (local.properties)
 *  - kotlinx.serialization -> org.json (ya incluido en Android, sin dependencias nuevas)
 *  - la llamada es suspend y corre en Dispatchers.IO (no bloquea la pantalla)
 */
class GroqService(private val apiKey: String = BuildConfig.GROQ_API_KEY) {

    suspend fun enviar(historial: List<TurnoChat>, preguntaNueva: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(llamar(historial, preguntaNueva))
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Result.failure(IllegalStateException("Sin conexión a internet."))
            } catch (e: Exception) {
                Result.failure(IllegalStateException(e.message ?: "Error al comunicarse con Groq."))
            }
        }

    private fun llamar(historial: List<TurnoChat>, pregunta: String): String {
        check(apiKey.isNotBlank()) { "Falta GROQ_API_KEY en local.properties." }

        val mensajes = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", PROMPT_SISTEMA))
            historial.forEach {
                put(JSONObject().put("role", "user").put("content", it.pregunta))
                put(JSONObject().put("role", "assistant").put("content", it.respuesta))
            }
            put(JSONObject().put("role", "user").put("content", pregunta))
        }
        val cuerpo = JSONObject().put("model", MODELO).put("messages", mensajes).toString()

        val conexion = (URL(URL_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }

        try {
            conexion.outputStream.use { it.write(cuerpo.toByteArray(Charsets.UTF_8)) }

            val codigo = conexion.responseCode
            val flujo = if (codigo in 200..299) conexion.inputStream else conexion.errorStream
            val texto = flujo?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (codigo !in 200..299) {
                val detalle = runCatching {
                    JSONObject(texto).getJSONObject("error").getString("message")
                }.getOrNull()
                val causa = when (codigo) {
                    401 -> "la API key de Groq no es válida"
                    429 -> "se alcanzó el límite de uso de Groq, intenta en un momento"
                    else -> detalle ?: "error del servidor"
                }
                throw IllegalStateException("Error de Groq (HTTP $codigo): $causa.")
            }

            val choices = JSONObject(texto).optJSONArray("choices")
            val contenido = choices
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content", "")
                .orEmpty()

            if (contenido.isBlank()) throw IllegalStateException("Krono no recibió una respuesta válida.")
            return contenido
        } finally {
            conexion.disconnect()
        }
    }

    private companion object {
        const val URL_API = "https://api.groq.com/openai/v1/chat/completions"
        const val MODELO = "openai/gpt-oss-120b"
        const val PROMPT_SISTEMA =
            "Eres Krono, el asistente de inteligencia artificial de KronoCorp. " +
                    "Responde de forma clara, profesional y útil."
    }
}