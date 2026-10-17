package com.example.krono_corp.negocio

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object MiNegocio : Screen("mi_negocio")
    object Sucursales : Screen("sucursales")
    object Configuracion : Screen("configuracion")
}

data class SucursalItem(
    val id: String,
    var nombre: String,
    var activa: Boolean = true,
    var codigoAcceso: String = "1234"
)

data class NegocioData(
    var nombreNegocio: String = "Veterinaria Miau",
    val propietarioEmail: String = "gatoGordo@gmail.com",
    val preguntasIA: MutableList<String> = mutableListOf(
        "Sobre Productos: ¿Cuál es el producto más vendido?",
        "Sobre Ventas: ¿Cuáles son los meses con mayores ventas?"
    ),
    val sucursales: MutableList<SucursalItem> = mutableListOf(
        SucursalItem("SUC-1", "Sucursal 1 - Mejicanos", true, "1234"),
        SucursalItem("SUC-2", "Sucursal 2 - Los Próceres", true, "5678")
    )
)