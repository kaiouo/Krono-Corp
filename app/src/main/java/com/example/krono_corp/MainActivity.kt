package com.example.krono_corp

import android.content.Intent
import android.net.Uri
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
import com.example.krono_corp.Supervisor.SupervisorScreen
import com.example.krono_corp.Login.ForgotPasswordScreen
import com.example.krono_corp.Login.LoginScreen
import com.example.krono_corp.Login.RegisterScreen
import com.example.krono_corp.Login.ResetPasswordScreen
import com.example.krono_corp.Login.VerificarCodigoScreen
import com.example.krono_corp.Login.VerificarCorreoScreen
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
import com.example.krono_corp.negocio.MetricasScreen
import com.example.krono_corp.negocio.MiNegocioScreen
import com.example.krono_corp.negocio.NegocioData
//import com.example.krono_corp.negocio.NuevaCuentaAdminScreen
import com.example.krono_corp.negocio.ReporteGenerado
import com.example.krono_corp.negocio.Screen
import com.example.krono_corp.negocio.SucursalesScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Enlace de Appwrite (recuperar contraseña / verificar correo) que abrió la app.
    // Es var de la Activity (no solo de la Composable) para que onNewIntent lo pueda actualizar
    // aunque la app ya esté abierta cuando el usuario toca el enlace del correo.
    private var deepLink by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Appwrite se inicializa antes de dibujar cualquier pantalla
        AppwriteClient.init(applicationContext)
        deepLink = intent?.data

        setContent {
            MaterialTheme {
                KronoApp(deepLink = deepLink, alConsumirDeepLink = { deepLink = null })
            }
        }
    }

    // Se llama cuando el usuario toca el enlace del correo con la app ya abierta
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deepLink = intent.data
    }
}

private enum class Pantalla {
    CARGANDO,
    LOGIN,
    REGISTRO,
    REVISA_CORREO,          // después de registrarse: "revisa tu correo para verificar"
    VERIFICAR_CODIGO,       // segundo paso del login: código de 6 dígitos
    OLVIDE_CONTRASENA,
    RESTABLECER_CONTRASENA,
    SESION
}

@Composable
fun KronoApp(deepLink: Uri? = null, alConsumirDeepLink: () -> Unit = {}) {
    var pantalla by rememberSaveable { mutableStateOf(Pantalla.CARGANDO) }
    var usuario by remember { mutableStateOf<AppUser?>(null) }
    var recuperacion by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) }
    // Cuenta que está en el segundo paso del login (userId, correo)
    var desafio by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) }
    // Correo recién registrado, para la pantalla "Revisa tu correo"
    var correoPendiente by rememberSaveable { mutableStateOf("") }
    // Código del enlace de invitación (kronocorp://registro?codigo=..) para registrarse como Supervisor
    var codigoInvitacion by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
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

    // Enlace del correo de Appwrite: kronocorp://recovery?userId=..&secret=.. o kronocorp://verify?...
    LaunchedEffect(deepLink) {
        val uri = deepLink ?: return@LaunchedEffect

        // Invitación de un gerente: abre el registro con el rol Supervisor fijo
        if (uri.host == "registro") {
            val codigo = uri.getQueryParameter("codigo")
            if (!codigo.isNullOrBlank()) {
                codigoInvitacion = codigo.trim()
                pantalla = Pantalla.REGISTRO
            }
            alConsumirDeepLink()
            return@LaunchedEffect
        }

        val userId = uri.getQueryParameter("userId")
        val secret = uri.getQueryParameter("secret")
        if (!userId.isNullOrBlank() && !secret.isNullOrBlank()) {
            when (uri.host) {
                "recovery" -> {
                    recuperacion = userId to secret
                    pantalla = Pantalla.RESTABLECER_CONTRASENA
                }
                "verify" -> {
                    AuthRepository.confirmarVerificacion(userId, secret).fold(
                        onSuccess = {
                            Toast.makeText(
                                context,
                                "Correo verificado ✅ Ya puedes iniciar sesión.",
                                Toast.LENGTH_LONG
                            ).show()
                            usuario?.let { usuario = it.copy(emailVerificado = true) }
                            // Si estaba en "Revisa tu correo", pasa al login
                            if (pantalla == Pantalla.REVISA_CORREO) {
                                pantalla = Pantalla.LOGIN
                            }
                        },
                        onFailure = {
                            Toast.makeText(
                                context,
                                it.message ?: "No se pudo verificar el correo.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            }
        }
        alConsumirDeepLink()
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
            onCodigoEnviado = { userId, email ->
                desafio = userId to email
                pantalla = Pantalla.VERIFICAR_CODIGO
            },
            onGoToRegister = { pantalla = Pantalla.REGISTRO },
            onForgotPassword = { pantalla = Pantalla.OLVIDE_CONTRASENA }
        )

        Pantalla.VERIFICAR_CODIGO -> desafio?.let { (userId, email) ->
            VerificarCodigoScreen(
                userId = userId,
                email = email,
                onVerificado = {
                    desafio = null
                    entrar(it)
                },
                onCancelar = {
                    desafio = null
                    pantalla = Pantalla.LOGIN
                }
            )
        } ?: run { pantalla = Pantalla.LOGIN }

        Pantalla.REGISTRO -> RegisterScreen(
            onRegisterSuccess = { correo ->
                correoPendiente = correo
                codigoInvitacion = null
                pantalla = Pantalla.REVISA_CORREO
            },
            onGoToLogin = {
                codigoInvitacion = null
                pantalla = Pantalla.LOGIN
            },
            codigoInvitacion = codigoInvitacion
        )

        Pantalla.REVISA_CORREO -> VerificarCorreoScreen(
            correo = correoPendiente,
            onIrLogin = { pantalla = Pantalla.LOGIN }
        )

        Pantalla.OLVIDE_CONTRASENA -> ForgotPasswordScreen(
            onVolverLogin = { pantalla = Pantalla.LOGIN }
        )

        Pantalla.RESTABLECER_CONTRASENA -> recuperacion?.let { (userId, secret) ->
            ResetPasswordScreen(
                userId = userId,
                secret = secret,
                onListo = {
                    recuperacion = null
                    Toast.makeText(context, "Contraseña actualizada. Inicia sesión.", Toast.LENGTH_LONG).show()
                    pantalla = Pantalla.LOGIN
                }
            )
        } ?: run { pantalla = Pantalla.LOGIN }

        Pantalla.SESION -> usuario?.let { u ->
            when (u.role) {
                // GERENTE -> panel de administración
                Rol.GERENTE -> AdminScreen(
                    usuario = u,
                    alCerrarSesion = { cerrarSesion() }
                )

                Rol.SUPERVISOR -> SupervisorScreen(
                    usuario = u,
                    alCerrarSesion = { cerrarSesion() }
                )

                // DUENO y SUPERVISOR -> flujo del negocio (empieza en MiNegocioScreen)
                Rol.DUENO -> NegocioNavHost(
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

    // Un único objeto compartido por todas las pantallas (sus listas son mutables)
    val negocio = remember(usuario.id) {
        NegocioData(propietarioEmail = usuario.email)
    }

    // Trae el historial guardado en Appwrite (reportes con sus archivos del bucket, y gráficos)
    // y actualiza las listas que leen las pantallas.
    suspend fun recargarHistorial() {
        HistorialRepository.cargar(usuario.id)
            .onSuccess { historial ->
                negocio.historialReportes.clear()
                negocio.historialReportes.addAll(historial.reportes)
                negocio.historialGraficos.clear()
                negocio.historialGraficos.addAll(historial.graficos)
            }
            .onFailure {
                avisarError(it.message, "No se pudo cargar tu historial de reportes y gráficos.")
            }
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
            // Aunque falle la subida del archivo, el texto se guarda: se refresca en ambos casos
            recargarHistorial()
        }
    }

    fun guardarGraficoEnHistorial(g: GraficoGenerado) {
        scope.launch {
            HistorialRepository.guardarGrafico(usuario.id, g)
                .onFailure { avisarError(it.message, "No se pudo guardar el gráfico en el historial.") }
            recargarHistorial()
        }
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
        recargarHistorial()
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
                negocioId = usuario.id,
                // Item 9: solo el dueño del negocio agrega/edita/elimina sucursales;
                // el supervisor solo puede verlas.
                puedeEditar = usuario.role == Rol.DUENO
            )
        }

        composable(Screen.Metricas.route) {
            MetricasScreen(navController = navController, negocioData = negocio, usuario = usuario)
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

        //composable(Screen.NuevaCuentaAdmin.route) {
        //    NuevaCuentaAdminScreen(navController = navController, negocioData = negocio)
        //}
    }
}