package com.example.krono_corp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.krono_corp.Admin.AdminScreen
import com.example.krono_corp.Login.LoginScreen
import com.example.krono_corp.Login.RegisterScreen
import com.example.krono_corp.data.AppUser
import com.example.krono_corp.data.AppwriteClient
import com.example.krono_corp.data.AuthRepository
import com.example.krono_corp.data.ConfigVentasRepository
import com.example.krono_corp.data.HistorialRepository
import com.example.krono_corp.data.NegocioRepository
import com.example.krono_corp.data.ReportesRepository
import com.example.krono_corp.data.Rol
import com.example.krono_corp.negocio.AsistenteScreen
import com.example.krono_corp.negocio.ConfiguracionScreen
import com.example.krono_corp.negocio.GraficoGenerado
import com.example.krono_corp.negocio.HistorialGraficosScreen
import com.example.krono_corp.negocio.HistorialReportesScreen
import com.example.krono_corp.negocio.HomeScreen
import com.example.krono_corp.negocio.MiNegocioScreen
import com.example.krono_corp.negocio.NegocioData
import com.example.krono_corp.negocio.NuevaCuentaAdminScreen
import com.example.krono_corp.negocio.ReporteGenerado
import com.example.krono_corp.negocio.Screen
import com.example.krono_corp.negocio.SucursalesScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Appwrite se inicializa antes de dibujar cualquier pantalla
        AppwriteClient.init(applicationContext)

        setContent {
            MaterialTheme {
                KronoApp()
            }
        }
    }
}

private enum class Pantalla { CARGANDO, LOGIN, REGISTRO, SESION }

@Composable
fun KronoApp() {
    var pantalla by rememberSaveable { mutableStateOf(Pantalla.CARGANDO) }
    var usuario by remember { mutableStateOf<AppUser?>(null) }
    val scope = rememberCoroutineScope()

    // Al abrir la app (o rotar la pantalla) recupera la sesión guardada en Appwrite
    LaunchedEffect(pantalla) {
        val necesitaUsuario = pantalla == Pantalla.CARGANDO ||
                (pantalla == Pantalla.SESION && usuario == null)
        if (necesitaUsuario) {
            val u = AuthRepository.currentUser()
            usuario = u
            pantalla = if (u != null) Pantalla.SESION else Pantalla.LOGIN
        }
    }

    fun entrar(u: AppUser) {
        usuario = u
        pantalla = Pantalla.SESION
    }

    fun cerrarSesion() {
        scope.launch {
            AuthRepository.logout()
            usuario = null
            pantalla = Pantalla.LOGIN
        }
    }

    when (pantalla) {
        Pantalla.CARGANDO -> PantallaCargando()

        Pantalla.LOGIN -> LoginScreen(
            onLoginSuccess = { entrar(it) },
            onGoToRegister = { pantalla = Pantalla.REGISTRO }
        )

        Pantalla.REGISTRO -> RegisterScreen(
            onRegisterSuccess = { entrar(it) },
            onGoToLogin = { pantalla = Pantalla.LOGIN }
        )

        Pantalla.SESION -> usuario?.let { u ->
            when (u.role) {
                // GERENTE -> panel de administración
                Rol.GERENTE -> AdminScreen(
                    usuario = u,
                    alCerrarSesion = { cerrarSesion() }
                )

                // DUENO y SUPERVISOR -> flujo del negocio (empieza en MiNegocioScreen)
                Rol.DUENO, Rol.SUPERVISOR -> NegocioNavHost(
                    usuario = u,
                    alCerrarSesion = { cerrarSesion() }
                )
            }
        } ?: PantallaCargando()
    }
}

@Composable
private fun PantallaCargando() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * Flujo de DUENO y SUPERVISOR: todas las pantallas del paquete "negocio"
 * comparten un NavController y un mismo NegocioData.
 */
@Composable
private fun NegocioNavHost(
    usuario: AppUser,
    alCerrarSesion: () -> Unit
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun avisarError(mensaje: String?, porDefecto: String) {
        Toast.makeText(context, mensaje ?: porDefecto, Toast.LENGTH_LONG).show()
    }

    // Solo el SUPERVISOR envía sus reportes y gráficos a Appwrite (para que su gerente los vea)
    fun enviarAlGerente(tipo: String, titulo: String, fecha: String, contenido: String) {
        if (usuario.role != Rol.SUPERVISOR) return
        scope.launch {
            ReportesRepository.guardar(usuario, tipo, titulo, fecha, contenido)
                .onFailure {
                    avisarError(it.message, "No se pudo enviar el reporte a tu gerente.")
                }
        }
    }

    // DUENO y SUPERVISOR guardan su propio historial de reportes (texto, PDF, Excel) y gráficos
    fun guardarReporteEnHistorial(r: ReporteGenerado) {
        scope.launch {
            HistorialRepository.guardarReporte(usuario.id, r)
                .onFailure { avisarError(it.message, "No se pudo guardar el reporte en el historial.") }
        }
    }

    fun guardarGraficoEnHistorial(g: GraficoGenerado) {
        scope.launch {
            HistorialRepository.guardarGrafico(usuario.id, g)
                .onFailure { avisarError(it.message, "No se pudo guardar el gráfico en el historial.") }
        }
    }

    // Un único objeto compartido por todas las pantallas (sus listas son mutables)
    val negocio = remember(usuario.id) {
        NegocioData(propietarioEmail = usuario.email)
    }

    // Carga lo guardado en Appwrite antes de mostrar las pantallas
    var configLista by remember(usuario.id) { mutableStateOf(false) }
    LaunchedEffect(usuario.id) {
        // Preguntas de venta (tabla "usuarios")
        ConfigVentasRepository.cargarPreguntas(usuario.id).onSuccess { guardadas ->
            if (guardadas != null) {
                negocio.preguntasVenta.clear()
                negocio.preguntasVenta.addAll(guardadas)
            }
        }

        // Nombre del negocio y preguntas para la IA (tabla "negocios")
        NegocioRepository.cargar(usuario.id).onSuccess { guardado ->
            if (guardado != null) {
                if (guardado.nombreNegocio.isNotBlank()) {
                    negocio.nombreNegocio = guardado.nombreNegocio
                }
                guardado.preguntasIA?.let {
                    negocio.preguntasIA.clear()
                    negocio.preguntasIA.addAll(it)
                }
            }
        }

        // Historial de reportes (texto, PDF, Excel) y gráficos (tabla "reportes")
        HistorialRepository.cargar(usuario.id).onSuccess { historial ->
            negocio.historialReportes.clear()
            negocio.historialReportes.addAll(historial.reportes)
            negocio.historialGraficos.clear()
            negocio.historialGraficos.addAll(historial.graficos)
        }
        .onFailure {
            avisarError(it.message, "No se pudo cargar tu historial de reportes y gráficos.")
        }
        configLista = true
    }
    if (!configLista) {
        PantallaCargando()
        return
    }

    NavHost(
        navController = navController,
        startDestination = Screen.MiNegocio.route // cámbialo a Screen.Home.route si prefieres iniciar en el Home
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                navController = navController,
                negocioData = negocio,
                onCerrarSesion = alCerrarSesion
            )
        }

        composable(Screen.MiNegocio.route) {
            MiNegocioScreen(
                navController = navController,
                negocioData = negocio,
                onGuardarCambios = { nombre, preguntas ->
                    negocio.nombreNegocio = nombre
                    negocio.preguntasIA.clear()
                    negocio.preguntasIA.addAll(preguntas)
                    scope.launch {
                        NegocioRepository.guardar(usuario.id, nombre, preguntas).onFailure {
                            avisarError(it.message, "No se pudo guardar el negocio.")
                        }
                    }
                },
                onGuardarPreguntasVenta = { lista ->
                    negocio.preguntasVenta.clear()
                    negocio.preguntasVenta.addAll(lista)
                    scope.launch {
                        ConfigVentasRepository.guardarPreguntas(usuario.id, lista).onFailure {
                            avisarError(it.message, "No se pudieron guardar las preguntas de venta.")
                        }
                    }
                }
            )
        }

        composable(Screen.Sucursales.route) {
            SucursalesScreen(
                navController = navController,
                negocioData = negocio,
                negocioId = usuario.id
            )
        }

        composable(Screen.Configuracion.route) {
            ConfiguracionScreen(
                navController = navController,
                negocioData = negocio,
                onCerrarSesion = alCerrarSesion
            )
        }

        composable(Screen.Asistente.route) {
            AsistenteScreen(
                navController = navController,
                negocioData = negocio,
                usuario = usuario,
                onReporteGenerado = { r ->
                    guardarReporteEnHistorial(r)
                    enviarAlGerente(ReportesRepository.TIPO_REPORTE, r.titulo, r.fecha, r.contenido)
                },
                onGraficoGenerado = { g ->
                    guardarGraficoEnHistorial(g)
                    enviarAlGerente(
                        ReportesRepository.TIPO_GRAFICO, g.titulo, g.fecha,
                        ReportesRepository.codificarDatos(g.datos)
                    )
                }
            )
        }

        composable(Screen.HistorialReportes.route) {
            HistorialReportesScreen(navController = navController, negocioData = negocio)
        }

        composable(Screen.HistorialGraficos.route) {
            HistorialGraficosScreen(navController = navController, negocioData = negocio)
        }

        composable(Screen.NuevaCuentaAdmin.route) {
            NuevaCuentaAdminScreen(navController = navController, negocioData = negocio)
        }
    }
}