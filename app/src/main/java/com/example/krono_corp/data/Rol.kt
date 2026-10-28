package com.example.krono_corp.data

/**
 * Roles de la app.
 * - codigo: lo que se guarda en la columna "role" de Appwrite
 * - etiqueta: lo que ve el usuario en el menú desplegable
 */
enum class Rol(val codigo: String, val etiqueta: String) {
    GERENTE("GERENTE", "Gerente General"),
    SUPERVISOR("SUPERVISOR", "Supervisor de Ventas"),
    DUENO("DUENO", "Dueño de Emprendimiento");

    companion object {
        fun desdeCodigo(codigo: String?): Rol =
            entries.firstOrNull { it.codigo == codigo } ?: SUPERVISOR
    }
}