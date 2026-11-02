package com.example.krono_corp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.krono_corp.Admin.AdminScreen
import com.example.krono_corp.Login.LoginScreen
import com.example.krono_corp.Login.RegisterScreen
import com.example.krono_corp.data.AppUser
import com.example.krono_corp.data.AppwriteClient
import com.example.krono_corp.data.AuthRepository
import com.example.krono_corp.data.NegocioRepository
import com.example.krono_corp.data.Rol
import com.example.krono_corp.negocio.SucursalItem
import com.example.krono_corp.negocio.AsistenteScreen
import com.example.krono_corp.negocio.ConfiguracionScreen
import com.example.krono_corp.negocio.HistorialGraficosScreen
import com.example.krono_corp.negocio.HistorialReportesScreen
import com.example.krono_corp.negocio.HomeScreen
import com.example.krono_corp.negocio.MiNegocioScreen
import com.example.krono_corp.negocio.NegocioData
import com.example.krono_corp.negocio.NuevaCuentaAdminScreen
import com.example.krono_corp.negocio.RegistroVentasScreen
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
                Rol.GERENTE -> AdminScreen(alCerrarSesion = { cerrarSesion() })

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

@Composable
private fun PantallaError(mensaje: String, alCerrarSesion: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(mensaje, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = alCerrarSesion) {
                Text("Cerrar sesión")
            }
        }
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

    // Un único objeto compartido por todas las pantallas (sus listas son mutables)
    val negocio = remember(usuario.id) {
        NegocioData(propietarioEmail = usuario.email)
    }
    var cargandoNegocio by remember(usuario.id) { mutableStateOf(true) }
    var errorNegocio by remember(usuario.id) { mutableStateOf("") }

    // Carga (o crea, si es la primera vez) el negocio real del usuario en Appwrite,
    // junto con sus sucursales. Antes de esto, negocio.id está vacío.
    LaunchedEffect(usuario.id) {
        NegocioRepository.obtenerOCrearNegocio(usuario.id, usuario.email)
            .onSuccess { remoto ->
                negocio.id = remoto.id
                negocio.ownerId = remoto.ownerId
                negocio.nombreNegocio = remoto.nombreNegocio
                negocio.preguntasIA.clear()
                negocio.preguntasIA.addAll(remoto.preguntasIA)

                NegocioRepository.listarSucursales(remoto.id).onSuccess { sucursales ->
                    negocio.sucursales.clear()
                    negocio.sucursales.addAll(
                        sucursales.map { SucursalItem(it.id, it.nombre, it.activa, it.codigoAcceso) }
                    )
                }
                errorNegocio = ""
            }
            .onFailure { e -> errorNegocio = e.message ?: "No se pudo cargar el negocio." }
        cargandoNegocio = false
    }

    if (cargandoNegocio) {
        PantallaCargando()
        return
    }
    if (errorNegocio.isNotBlank()) {
        PantallaError(mensaje = errorNegocio, alCerrarSesion = alCerrarSesion)
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

        composable(Screen.Ventas.route) {
            RegistroVentasScreen(navController = navController, negocioData = negocio)
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
                        NegocioRepository.actualizarNegocio(negocio.id, nombre, preguntas)
                    }
                }
            )
        }

        composable(Screen.Sucursales.route) {
            SucursalesScreen(
                navController = navController,
                negocioData = negocio,
                onGuardarSucursales = { lista ->
                    negocio.sucursales.clear()
                    negocio.sucursales.addAll(lista)
                    scope.launch {
                        NegocioRepository.reemplazarSucursales(
                            negocioId = negocio.id,
                            ownerId = negocio.ownerId,
                            nuevas = lista.map { Triple(it.id, it.nombre, it.activa to it.codigoAcceso) }
                        )
                    }
                }
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
            AsistenteScreen(navController = navController, negocioData = negocio)
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
