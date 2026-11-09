package com.example.krono_corp.Supervisor

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.rememberNavController
import com.example.krono_corp.Admin.HistorialSupervisorScreen
import com.example.krono_corp.components.SupervisorBottomBar
import com.example.krono_corp.components.SupervisorRoutes
import com.example.krono_corp.components.SupervisorTopBar
import com.example.krono_corp.data.AppUser
import com.example.krono_corp.data.AuthRepository
import com.example.krono_corp.data.ConfigVentasRepository
import com.example.krono_corp.data.GraficoReporte
import com.example.krono_corp.data.NegocioRepository
import com.example.krono_corp.data.ReportesRepository
import com.example.krono_corp.negocio.AsistenteScreen
import com.example.krono_corp.negocio.NegocioData
import com.example.krono_corp.negocio.SucursalesScreen
import kotlinx.coroutines.launch
import java.io.File


// COLORES DE LA APLICACIÓN

private val Fondo = Color(0xFFF7F8FC)
private val Texto = Color(0xFF202633)
private val Gris = Color(0xFF747780)


// RUTAS DE NAVEGACIÓN
private val Panel = SupervisorRoutes.PANEL
private val Sucursales = SupervisorRoutes.SUCURSALES
private val Asistente = SupervisorRoutes.ASISTENTE
private val Historial = SupervisorRoutes.HISTORIAL


// PANTALLA PRINCIPAL DEL SUPERVISOR

@Composable
fun SupervisorScreen(
    usuario: AppUser,
    alCerrarSesion: () -> Unit = {}
) {

    // Pantalla que se encuentra seleccionada
    var pantalla by remember { mutableStateOf(Panel) }

    // Controlador de navegación (solo lo piden las pantallas del paquete negocio)
    val navController = rememberNavController()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Datos compartidos del chat de ventas
    val negocio = remember(usuario.id) {
        NegocioData(propietarioEmail = usuario.email)
    }

    // Perfil actualizado: el gerente pudo asignar la sucursal después de que el supervisor inició sesión
    var perfil by remember(usuario.id) { mutableStateOf(usuario) }
    LaunchedEffect(pantalla) {
        if (pantalla == Panel || pantalla == Sucursales) {
            AuthRepository.currentUser()?.let { perfil = it }
        }
    }

    // Carga la configuración del chat antes de mostrar las pantallas
    var configLista by remember(usuario.id) { mutableStateOf(false) }
    LaunchedEffect(usuario.id) {
        val fresco = AuthRepository.currentUser()
        if (fresco != null) perfil = fresco
        // El negocio es el de su gerente (si ya tiene uno asignado)
        val negocioId = (fresco ?: usuario).gerenteId.ifBlank { usuario.id }

        // Nombre del negocio y preguntas: son las del GERENTE (el supervisor solo las usa)
        var nombreCargado = false
        NegocioRepository.cargar(negocioId)
            .onSuccess { guardado ->
                if (guardado != null) {
                    if (guardado.nombreNegocio.isNotBlank()) {
                        negocio.nombreNegocio = guardado.nombreNegocio
                        nombreCargado = true
                    }
                    guardado.preguntasIA?.let {
                        negocio.preguntasIA.clear()
                        negocio.preguntasIA.addAll(it)
                    }
                    guardado.preguntasVenta?.let {
                        negocio.preguntasVenta.clear()
                        negocio.preguntasVenta.addAll(it)
                    }
                }
            }
            .onFailure {
                Toast.makeText(
                    context,
                    "No se pudieron cargar las preguntas de tu gerente.",
                    Toast.LENGTH_LONG
                ).show()
            }
        if (!nombreCargado) negocio.nombreNegocio = "tu negocio"

        // ANTES FALTABA ESTO: sin esta línea la pantalla se quedaba cargando para siempre
        configLista = true
    }

    // Guarda en Appwrite lo que genera la IA: queda en el historial del supervisor
    // y su gerente también lo ve (los archivos Excel/PDF se suben al almacenamiento).
    // Sube cuando termina de guardarse un reporte: el historial abierto se vuelve a leer
    var versionHistorial by remember { mutableStateOf(0) }

    fun enviarAlHistorial(
        tipo: String,
        titulo: String,
        fecha: String,
        contenido: String,
        archivos: List<File> = emptyList(),
        grafico: GraficoReporte? = null
    ) {
        scope.launch {
            ReportesRepository.guardar(perfil, tipo, titulo, fecha, contenido, archivos, grafico)
                .onFailure {
                    Toast.makeText(
                        context,
                        it.message ?: "No se pudo guardar el reporte en tu historial.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            versionHistorial++
        }
    }

    // Estado del menú lateral
    val drawerState = rememberDrawerState(
        initialValue = DrawerValue.Closed
    )

    ModalNavigationDrawer(

        drawerState = drawerState,

        // MENÚ LATERAL DEL SUPERVISOR
        drawerContent = {

            ModalDrawerSheet(
                modifier = Modifier.width(290.dp),
                drawerContainerColor = Color.White
            ) {

                Spacer(Modifier.height(20.dp))

                Text(
                    text = "Supervisor",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                Spacer(Modifier.height(20.dp))

                // Información del usuario

                Text(
                    text = "👤  ${usuario.alias}",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                Text(
                    text = "     ${usuario.role.etiqueta}",
                    color = Gris,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                Text(
                    text = "     ${usuario.email}",
                    color = Gris,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 18.dp)
                )

                // Opciones del supervisor

                val opciones = listOf(
                    Panel,
                    Sucursales,
                    Asistente,
                    Historial
                )

                opciones.forEach { opcion ->

                    val nombre = when (opcion) {
                        Panel -> "Panel principal"
                        Sucursales -> "Mis sucursales"
                        Asistente -> "Asistente IA"
                        Historial -> "Historial de reportes"
                        else -> opcion
                    }

                    NavigationDrawerItem(
                        label = { Text(nombre) },
                        selected = pantalla == opcion,

                        onClick = {
                            pantalla = opcion

                            scope.launch {
                                drawerState.close()
                            }
                        },

                        modifier = Modifier.padding(
                            horizontal = 12.dp,
                            vertical = 3.dp
                        )
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                // Cerrar sesión

                NavigationDrawerItem(
                    label = { Text("Cerrar sesión") },
                    selected = false,

                    onClick = {
                        scope.launch {
                            drawerState.close()
                        }

                        alCerrarSesion()
                    },

                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }

    ) {

        // ESTRUCTURA GENERAL DE LA PANTALLA

        Scaffold(

            containerColor = Fondo,

            // BARRA SUPERIOR
            topBar = {
                SupervisorTopBar(
                    alAbrirMenu = {
                        scope.launch {
                            drawerState.open()
                        }
                    }
                )
            },

            // BARRA INFERIOR
            bottomBar = {
                SupervisorBottomBar(
                    pantallaActual = pantalla,
                    alSeleccionar = { destino ->
                        pantalla = destino
                    }
                )
            }

        ) { espacio ->

            // CONTENIDO SEGÚN PANTALLA ELEGIDA

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(espacio)
                    .consumeWindowInsets(espacio)
            ) {

                if (!configLista) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    when (pantalla) {

                        // PANEL PRINCIPAL
                        Panel -> PanelControl(
                            sinAsignar = perfil.gerenteId.isBlank(),
                            abrirPantalla = {
                                pantalla = it
                            }
                        )

                        // MI SUCURSAL
                        // Las sucursales son del GERENTE: así no se pierden si se elimina al supervisor
                        Sucursales -> SucursalesScreen(
                            navController = navController,
                            negocioData = negocio,
                            negocioId = perfil.gerenteId.ifBlank { usuario.id },
                            puedeEditar = false,
                            mostrarBarras = false,
                            sucursalAsignada = perfil.sucursal
                        )

                        // ASISTENTE IA: chat de ventas (registrar, consultar, exportar, gráficos)
                        Asistente -> AsistenteScreen(
                            navController = navController,
                            negocioData = negocio,
                            usuario = perfil,
                            mostrarBarras = false,
                            onReporteGenerado = { r ->
                                enviarAlHistorial(
                                    ReportesRepository.TIPO_REPORTE,
                                    r.titulo,
                                    r.fecha,
                                    r.contenido,
                                    r.archivos.map { File(it) },
                                    r.grafico
                                )
                            }
                        )

                        // HISTORIAL DE REPORTES, con sus archivos y su gráfico (solo los del propio supervisor)
                        Historial -> HistorialSupervisorScreen(perfil, versionHistorial)
                    }
                }
            }
        }
    }
}


// PANEL PRINCIPAL DEL SUPERVISOR

@Composable
private fun PanelControl(
    sinAsignar: Boolean = false,
    abrirPantalla: (String) -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            text = "Panel de Supervisión",
            fontSize = 25.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Bold,
            color = Texto
        )

        if (sinAsignar) {
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4DD))
            ) {
                Text(
                    text = "Tu gerente todavía no te asigna a una sucursal. " +
                            "Cuando lo haga, cierra sesión y vuelve a entrar para ver tu sucursal.",
                    fontSize = 13.sp,
                    color = Color(0xFF7A5200),
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // PRIMERA FILA

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            TarjetaSupervisor(
                titulo = "Mi sucursal",
                icono = Icons.Default.Domain,
                colorIcono = Color(0xFF24A779),
                modifier = Modifier.weight(1f)
            ) {
                abrirPantalla(Sucursales)
            }

            TarjetaSupervisor(
                titulo = "Asistente IA",
                icono = Icons.Default.Chat,
                colorIcono = Color(0xFF3984F5),
                modifier = Modifier.weight(1f)
            ) {
                abrirPantalla(Asistente)
            }
        }

        Spacer(Modifier.height(12.dp))

        // SEGUNDA FILA

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            TarjetaSupervisor(
                titulo = "Historial de reportes",
                icono = Icons.Default.Description,
                colorIcono = Color(0xFFE7A12B),
                modifier = Modifier.weight(1f)
            ) {
                abrirPantalla(Historial)
            }

            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))
    }
}


// TARJETA REUTILIZABLE DEL SUPERVISOR

@Composable
private fun TarjetaSupervisor(
    titulo: String,
    icono: ImageVector,
    colorIcono: Color,
    modifier: Modifier = Modifier,
    alPulsar: () -> Unit
) {

    Card(
        modifier = modifier
            .height(112.dp)
            .clickable {
                alPulsar()
            },

        shape = RoundedCornerShape(16.dp),

        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),

        border = BorderStroke(
            1.dp,
            Color(0xFFD6DEEA)
        )
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),

            verticalArrangement = Arrangement.SpaceBetween
        ) {

            Icon(
                imageVector = icono,
                contentDescription = titulo,
                tint = colorIcono,
                modifier = Modifier.size(26.dp)
            )

            Text(
                text = titulo,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Texto
            )
        }
    }
}