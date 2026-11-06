package com.example.krono_corp.Supervisor



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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.krono_corp.Admin.AsistenteIAScreen
import com.example.krono_corp.Admin.HistorialReportesScreen
import kotlinx.coroutines.launch

import com.example.krono_corp.components.AdminTopBar
import com.example.krono_corp.components.AdminBottomBar
import com.example.krono_corp.components.AdminRoutes
import com.example.krono_corp.components.SupervisorBottomBar
import com.example.krono_corp.components.SupervisorRoutes
import com.example.krono_corp.components.SupervisorTopBar
import com.example.krono_corp.data.AppUser
import com.example.krono_corp.negocio.HistorialGraficosScreen
import com.example.krono_corp.negocio.SucursalesScreen
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavController
import com.example.krono_corp.negocio.NegocioData


// COLORES DE LA APLICACIÓN


private val Azul = Color(0xFF1677FF)
private val Fondo = Color(0xFFF7F8FC)
private val Texto = Color(0xFF202633)
private val Gris = Color(0xFF747780)


// RUTAS DE NAVEGACIÓN
private val Panel = SupervisorRoutes.PANEL
private val Sucursales = SupervisorRoutes.SUCURSALES
private val Asistente = SupervisorRoutes.ASISTENTE
private val Historial = SupervisorRoutes.HISTORIAL

//private val Sucursal = SupervisorRoutes.SUCURSALES
private val HistorialGraficos = SupervisorRoutes.HISTORIAL_GRAFICOS


// PANTALLA PRINCIPAL DEL SUPERVISOR


@Composable
fun SupervisorScreen(
    usuario: AppUser,
    alCerrarSesion: () -> Unit = {}
) {

    // Pantalla que se encuentra seleccionada
    var pantalla by remember { mutableStateOf(Panel) }

    // Controlador de navegación
    val navController = rememberNavController()

// Datos compartidos del negocio
    val negocio = remember(usuario.id) {
        NegocioData(propietarioEmail = usuario.email)
    }
    // Estado del menú lateral
    val drawerState = rememberDrawerState(
        initialValue = DrawerValue.Closed
    )

    val scope = rememberCoroutineScope()

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
                    Historial,
                    HistorialGraficos
                )

                opciones.forEach { opcion ->

                    val nombre = when (opcion) {
                        Panel -> "Panel principal"
                        Sucursales -> "Mis sucursales"
                        Asistente -> "Asistente IA"
                        Historial -> "Historial de reportes"
                        HistorialGraficos -> "Historial de gráficos"
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

                when (pantalla) {

                    // PANEL PRINCIPAL
                    Panel -> PanelControl(
                        abrirPantalla = {
                            pantalla = it
                        }
                    )

                    // MI SUCURSAL

                    Sucursales -> SucursalesScreen(
                        navController = navController,
                        negocioData = negocio,
                        negocioId = usuario.id,
                        puedeEditar = false
                    )

                    // ASISTENTE IA
                    Asistente -> AsistenteIAScreen(usuario)

                    // HISTORIAL DE REPORTES
                    Historial -> HistorialReportesScreen(usuario)

                    // HISTORIAL DE GRÁFICOS
                    HistorialGraficos -> HistorialGraficosScreen(
                        navController = navController,
                        negocioData = negocio
                    )
                }
            }
        }
    }
}


// PANEL PRINCIPAL DEL SUPERVISOR

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
            text = "Panel de Supervisión",
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

            TarjetaSupervisor(
                titulo = "Historial de gráficos",
                icono = Icons.Default.BarChart,
                colorIcono = Color(0xFF27AE60),
                modifier = Modifier.weight(1f)
            ) {
                abrirPantalla(HistorialGraficos)
            }
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


