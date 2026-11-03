package com.example.krono_corp.ia

/**
 * Mantiene la memoria de la conversación actual con Krono
 * (igual que historialChat en la versión de consola).
 */
class GestorIA(private val groq: GroqService = GroqService()) {

    private val historial = mutableListOf<TurnoChat>()

    suspend fun procesarPregunta(pregunta: String): Result<String> =
        groq.enviar(historial.takeLast(MAX_TURNOS_MEMORIA), pregunta).onSuccess { respuesta ->
            // Solo se recuerdan las respuestas exitosas (los errores no entran a la memoria)
            historial.add(TurnoChat(pregunta, respuesta.take(MAX_CARACTERES_RESPUESTA)))
        }

    fun reiniciar() = historial.clear()

    private companion object {
        const val MAX_TURNOS_MEMORIA = 10
        const val MAX_CARACTERES_RESPUESTA = 800
    }
}