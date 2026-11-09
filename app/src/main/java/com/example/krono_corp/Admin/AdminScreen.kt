package com.example.krono_corp.Admin

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Store
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
import com.example.krono_corp.components.AdminBottomBar
import com.example.krono_corp.components.AdminRoutes
import com.example.krono_corp.components.AdminTopBar
import com.example.krono_corp.data.AppUser
import com.example.krono_corp.data.NegocioRepository
import com.example.krono_corp.negocio.MiNegocioScreen
import com.example.krono_corp.negocio.NegocioData
import com.example.krono_corp.negocio.SucursalesScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


// COLORES DE LA APLICACIÓN

private val Fondo = Color(0xFFF7F8FC)
private val Texto = Color(0xFF202633)
private val Gris = Color(0xFF747780)

private val Panel = AdminRoutes.PANEL
private val Evaluacion = AdminRoutes.EVALUACION
private val NuevoAdmin = AdminRoutes.NUEVO_ADMIN
private val Historial = AdminRoutes.HISTORIAL
private val Asistente = AdminRoutes.ASISTENTE
private val MiNegocio = AdminRoutes.MI_NEGOCIO
private val Sucursales = AdminRoutes.SUCURSALES

// Nueva opción (no necesita tocar AdminRoutes)
private const val Metricas = "Métricas de supervisores"


// PANTALLA PRINCIPAL Y NAVEGACIÓN

@Composable
fun AdminScreen(usuario: AppUser, alCerrarSesion: () -> Unit = {}) {

    var pantalla by remember { mutableStateOf(Panel) }
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Datos compartidos del negocio
    val negocio = remember(usuario.id) {
        NegocioData(propietarioEmail = usuario.email)
    }

    // Carga lo que el gerente guardó en Appwrite (nombre, preguntas IA y preguntas de venta)
    var negocioListo by remember(usuario.id) { mutableStateOf(false) }
    LaunchedEffect(usuario.id) {
        NegocioRepository.cargar(usuario.id)
            .onSuccess { guardado ->
                if (guardado != null) {
                    if (guardado.nombreNegocio.isNotBlank()) {
                        negocio.nombreNegocio = guardado.nombreNegocio
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
                    it.message ?: "No se pudo cargar la configuración del negocio.",
                    Toast.LENGTH_LONG
                ).show()
            }
        negocioListo = true
    }

    // Avisos: revisa cada 30 s si algún supervisor se registró con el enlace de este gerente
    LaunchedEffect(usuario.id) {
        AvisosInvitacion.reiniciar()
        while (true) {
            AvisosInvitacion.revisar(context, usuario.id)
            delay(30_000)
        }
    }

    val drawerState = rememberDrawerState(
        initialValue = DrawerValue.Closed
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {

            ModalDrawerSheet(
                modifier = Modifier.width(290.dp),
                drawerContainerColor = Color.White
            ) {

                Spacer(Modifier.height(20.dp))

                Text(
                    text = "Administrador",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                Spacer(Modifier.height(20.dp))

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

                val opciones = listOf(
                    Panel,
                    Asistente,
                    Evaluacion,
                    Metricas,
                    NuevoAdmin,
                    Historial
                )

                opciones.forEach { opcion ->

                    NavigationDrawerItem(
                        label = { Text(opcion) },
                        selected = pantalla == opcion,
                        badge = {
                            if (opcion == NuevoAdmin && AvisosInvitacion.nuevos > 0) {
                                Etiqueta("${AvisosInvitacion.nuevos}", AdminRojo)
                            }
                        },
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

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                NavigationDrawerItem(
                    label = { Text("Cerrar sesión") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        alCerrarSesion()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)
                )
            }
        }
    ) {

        Scaffold(
            containerColor = Fondo,

            // BARRA SUPERIOR
            topBar = {
                AdminTopBar(
                    alAbrirMenu = {
                        scope.launch {
                            drawerState.open()
                        }
                    }
                )
            },
            // BARRA INFERIOR
            bottomBar = {
                AdminBottomBar(
                    pantallaActual = pantalla,
                    alSeleccionar = { destino ->
                        pantalla = destino
                    }
                )
            },
        ) { espacio ->

            // AQUÍ CAMBIA EL CONTENIDO SEGÚN LA PANTALLA

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(espacio)
                    .consumeWindowInsets(espacio)
            ) {

                when (pantalla) {

                    Panel -> PanelControl(
                        abrirPantalla = { pantalla = it }
                    )

                    // MI NEGOCIO: el gerente configura aquí las preguntas de sus supervisores
                    MiNegocio -> {
                        if (!negocioListo) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            MiNegocioScreen(
                                navController = navController,
                                negocioData = negocio,
                                mostrarBarra = false,
                                onGuardarCambios = { nombre, preguntas ->
                                    negocio.nombreNegocio = nombre.ifBlank { "Mi negocio" }
                                    negocio.preguntasIA.clear()
                                    negocio.preguntasIA.addAll(preguntas)
                                },
                                onGuardarPreguntasVenta = { lista ->
                                    negocio.preguntasVenta.clear()
                                    negocio.preguntasVenta.addAll(lista)
                                    // Se guarda todo junto en una sola escritura
                                    scope.launch {
                                        NegocioRepository.guardar(
                                            ownerId = usuario.id,
                                            nombreNegocio = negocio.nombreNegocio,
                                            preguntasIA = negocio.preguntasIA.toList(),
                                            preguntasVenta = lista
                                        ).onFailure {
                                            Toast.makeText(
                                                context,
                                                it.message ?: "No se pudo guardar el negocio.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            )
                        }
                    }

                    Asistente -> AsistenteIAScreen(usuario)

                    Evaluacion -> EvaluacionScreen(usuario)

                    Metricas -> MetricasSupervisoresScreen(usuario)

                    NuevoAdmin -> NuevoAdministradorScreen(usuario)

                    Historial -> HistorialReportesScreen(usuario)

                    // SUCURSALES
                    Sucursales -> SucursalesScreen(
                        navController = navController,
                        negocioData = negocio,
                        negocioId = usuario.id,
                        puedeEditar = true,
                        mostrarBarras = false
                    )
                }
            }
        }
    }
}

// PANEL PRINCIPAL

@Composable
private fun PanelControl(
    abrirPantalla: (String) -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            text = "Administración de Empleados",
            fontSize = 25.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Bold,
            color = Texto
        )

        Spacer(Modifier.height(20.dp))

        // PRIMERA FILA

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            TarjetaAdmin(
                titulo = "Asistente",
                icono = Icons.AutoMirrored.Filled.Chat,
                colorIcono = Color(0xFF3984F5),
                modifier = Modifier.weight(1f)
            ) {
                abrirPantalla(Asistente)
            }

            TarjetaAdmin(
                titulo = "Reportes de supervisores",
                icono = Icons.Default.Description,
                colorIcono = Color(0xFFE7A12B),
                modifier = Modifier.weight(1f)
            ) {
                abrirPantalla(Historial)
            }
        }

        Spacer(Modifier.height(12.dp))

        // SEGUNDA FILA

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            TarjetaAdmin(
                titulo = "Mis supervisores",
                icono = Icons.Default.Assessment,
                colorIcono = Color(0xFF24A779),
                modifier = Modifier.weight(1f)
            ) {
                abrirPantalla(Evaluacion)
            }

            TarjetaAdmin(
                titulo = "Nuevo supervisor",
                icono = Icons.Default.PersonAdd,
                colorIcono = Color(0xFFA16AF3),
                modifier = Modifier.weight(1f),
                insignia = AvisosInvitacion.nuevos
            ) {
                abrirPantalla(NuevoAdmin)
            }
        }

        Spacer(Modifier.height(12.dp))

        // TERCERA FILA

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            TarjetaAdmin(
                titulo = "Mi negocio",
                icono = Icons.Default.Store,
                colorIcono = Color(0xFF3984F5),
                modifier = Modifier.weight(1f)
            ) {
                abrirPantalla(MiNegocio)
            }

            TarjetaAdmin(
                titulo = "Sucursales",
                icono = Icons.Default.Domain,
                colorIcono = Color(0xFF24A779),
                modifier = Modifier.weight(1f)
            ) {
                abrirPantalla(Sucursales)
            }
        }

        Spacer(Modifier.height(12.dp))

        // CUARTA FILA

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            TarjetaAdmin(
                titulo = "Métricas de supervisores",
                icono = Icons.Default.BarChart,
                colorIcono = Color(0xFFE7A12B),
                modifier = Modifier.weight(1f)
            ) {
                abrirPantalla(Metricas)
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

// TARJETA REUTILIZABLE

@Composable
private fun TarjetaAdmin(
    titulo: String,
    icono: ImageVector,
    colorIcono: Color,
    modifier: Modifier = Modifier,
    insignia: Int = 0,
    alPulsar: () -> Unit
) {

    Card(
        modifier = modifier
            .height(112.dp)
            .clickable { alPulsar() },
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icono,
                    contentDescription = titulo,
                    tint = colorIcono,
                    modifier = Modifier.size(26.dp)
                )
                if (insignia > 0) {
                    Etiqueta(if (insignia == 1) "1 nuevo" else "$insignia nuevos", AdminRojo)
                }
            }

            Text(
                text = titulo,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Texto
            )
        }
    }
}