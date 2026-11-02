package com.example.krono_corp.negocio

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object MiNegocio : Screen("mi_negocio")
    object Sucursales : Screen("sucursales")
    object Configuracion : Screen("configuracion")

    object Ventas : Screen("ventas")

    // Modulo de Persona 4
    object Asistente : Screen("asistente")
    object HistorialReportes : Screen("historial_reportes")
    object HistorialGraficos : Screen("historial_graficos")
    object NuevaCuentaAdmin : Screen("nueva_cuenta_admin")
}

data class SucursalItem(
    val id: String,
    var nombre: String,
    var activa: Boolean = true,
    var codigoAcceso: String = "1234"
)

// Modulo de Persona 4: lo que genera el asistente y las cuentas adicionales
// para administrar el mismo negocio sin pedir otro correo.
// El id es el id real del documento en Appwrite (vacio solo mientras se esta guardando).
data class ReporteGenerado(
    val id: String,
    val titulo: String,
    val fecha: String,
    val contenido: String
)

data class GraficoGenerado(
    val id: String,
    val titulo: String,
    val fecha: String,
    val datos: List<Pair<String, Int>>
)

data class CuentaAdministracion(
    val id: String = "",
    val nombre: String,
    val password: String
)

data class NegocioData(
    // Id del documento "negocios" en Appwrite; vacio hasta que se carga o se crea
    var id: String = "",
    var ownerId: String = "",
    var nombreNegocio: String = "Veterinaria Miau",
    val propietarioEmail: String = "gatoGordo@gmail.com",
    val preguntasIA: MutableList<String> = mutableListOf(
        "Sobre Productos: ¿Cuál es el producto más vendido?",
        "Sobre Ventas: ¿Cuáles son los meses con mayores ventas?"
    ),
    val sucursales: MutableList<SucursalItem> = mutableListOf(
        SucursalItem("SUC-1", "Sucursal 1 - Mejicanos", true, "1234"),
        SucursalItem("SUC-2", "Sucursal 2 - Los Próceres", true, "5678")
    ),
    // Modulo de Persona 4
    val historialReportes: MutableList<ReporteGenerado> = mutableListOf(),
    val historialGraficos: MutableList<GraficoGenerado> = mutableListOf(),
    val cuentasAdicionales: MutableList<CuentaAdministracion> = mutableListOf()
)