package com.example.krono_corp.Admin


import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.krono_corp.components.AdminTopBar
import com.example.krono_corp.components.AdminBottomBar
import kotlinx.coroutines.launch
import com.example.krono_corp.components.AdminRoutes
import com.example.krono_corp.data.AppUser
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Domain
import androidx.compose.runtime.mutableStateListOf


// COLORES DE LA APLICACIÓN

private val Azul = Color(0xFF1677FF)
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



// PANTALLA PRINCIPAL Y NAVEGACIÓN

@Composable
fun AdminScreen(usuario: AppUser, alCerrarSesion: () -> Unit = {}) {

    var pantalla by remember { mutableStateOf(Panel) }

    val drawerState = rememberDrawerState(
        initialValue = DrawerValue.Closed
    )

    val scope = rememberCoroutineScope()

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
                    NuevoAdmin,
                    Historial
                )

                opciones.forEach { opcion ->

                    NavigationDrawerItem(
                        label = { Text(opcion) },
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
            //barra inferior
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
                    MiNegocio -> MiNegocioGerenteScreen(
                        usuario = usuario,
                        alVolver = { pantalla = Panel }
                    )
                    Asistente -> AsistenteIAScreen(usuario)

                    Evaluacion -> EvaluacionScreen(usuario)

                    NuevoAdmin -> NuevoAdministradorScreen(usuario)

                    Historial -> HistorialReportesScreen(usuario)

                   // Sucursales -> SucursalesScreen(usuario)
                    // AdminRoutes.VENTAS -> ReportesVentasScreen()
                }
            }
        }
    }
}

// PANEL PRINCIPAL C

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
                icono = Icons.Default.Chat,
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
                modifier = Modifier.weight(1f)
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
    }
}

// TARJETA REUTILIZABLE

@Composable
private fun TarjetaAdmin(
    titulo: String,
    icono: ImageVector,
    colorIcono: Color,
    modifier: Modifier = Modifier,
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



