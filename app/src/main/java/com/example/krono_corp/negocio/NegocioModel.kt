package com.example.krono_corp.negocio

import androidx.compose.runtime.mutableStateListOf
import com.example.krono_corp.data.GraficoReporte
import com.example.krono_corp.data.PreguntaVenta

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object MiNegocio : Screen("mi_negocio")
    object Sucursales : Screen("sucursales")
    object Configuracion : Screen("configuracion")
    object Metricas : Screen("metricas")

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
data class ReporteGenerado(
    val id: Int,
    val titulo: String,
    val fecha: String,
    val contenido: String,
    // Rutas locales de los archivos recién generados en esta sesión
    val archivos: List<String> = emptyList(),
    // Archivos ya guardados en el bucket de Appwrite (mismo orden que los nombres)
    val archivoIds: List<String> = emptyList(),
    val archivoNombres: List<String> = emptyList(),
    // Gráfico (barras o pastel) que acompaña al reporte, si lo lleva
    val grafico: GraficoReporte? = null
)

data class GraficoGenerado(
    val id: Int,
    val titulo: String,
    val fecha: String,
    val datos: List<Pair<String, Int>>
)

data class CuentaAdministracion(
    val nombre: String,
    val password: String
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
    ),
    // Modulo de Persona 4
    // Lista observable: la pantalla del historial se actualiza sola cuando cambia
    val historialReportes: MutableList<ReporteGenerado> = mutableStateListOf(),
    val historialGraficos: MutableList<GraficoGenerado> = mutableListOf(),
    val cuentasAdicionales: MutableList<CuentaAdministracion> = mutableListOf(),
    // Preguntas que la IA hace al registrar una venta (se editan en MiNegocioScreen)
    val preguntasVenta: MutableList<PreguntaVenta> = PreguntaVenta.porDefecto().toMutableList()
)