package com.example.krono_corp.components


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.example.krono_corp.data.AppUser


// RUTAS DEL SUPERVISOR

object SupervisorRoutes {

    const val PANEL = "Panel de control"

    const val SUCURSALES = "Mis sucursales"

    const val ASISTENTE = "Asistente"

    const val HISTORIAL = "Historial de reportes"

    const val HISTORIAL_GRAFICOS = "Historial de gráficos"
}


// COLORES


private val AzulSeleccion = Color(0xFFD9E4FF)
private val Azul = Color(0xFF1260C4)
private val Gris = Color(0xFF747780)
private val TextoSupervisor = Color(0xFF202124)


// BARRA SUPERIOR DEL SUPERVISOR

@Composable
fun SupervisorTopBar(
    alAbrirMenu: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 14.dp),

        verticalAlignment = Alignment.CenterVertically
    ) {

        IconButton(
            onClick = alAbrirMenu
        ) {

            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Abrir menú",
                tint = TextoSupervisor
            )
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = "Supervisión",
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = TextoSupervisor
        )
    }
}


// BARRA LATERAL DEL SUPERVISOR

@Composable
fun SupervisorSidebar(
    usuario: AppUser,
    pantallaActual: String,
    alSeleccionar: (String) -> Unit
) {

    ModalDrawerSheet(
        modifier = Modifier.width(290.dp),

        drawerContainerColor = Color(0xFFF7F9FC),

        drawerContentColor = TextoSupervisor
    ) {

        Spacer(Modifier.height(18.dp))

        // TÍTULO

        Text(
            text = "Supervisor",
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.padding(
                start = 20.dp,
                end = 16.dp,
                bottom = 16.dp
            )
        )

        // INFORMACIÓN DEL USUARIO


        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),

            verticalAlignment = Alignment.CenterVertically
        ) {

            Surface(
                modifier = Modifier.size(42.dp),

                shape = MaterialTheme.shapes.medium,

                color = Color(0xFFDCE6F1)
            ) {

                Box(
                    contentAlignment = Alignment.Center
                ) {

                    Text(
                        "👤",
                        fontSize = 23.sp
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column {

                Text(
                    text = usuario.alias,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = usuario.role.etiqueta,
                    fontSize = 11.sp,
                    color = Gris
                )

                Text(
                    text = usuario.email,
                    fontSize = 10.sp,
                    color = Gris
                )
            }
        }


        Spacer(Modifier.height(26.dp))

        // OPCIÓN: PANEL PRINCIPAL


        NavigationDrawerItem(

            icon = {
                Icon(
                    Icons.Default.Dashboard,
                    contentDescription = null
                )
            },

            label = {
                Text("Panel de control", fontSize = 12.sp)
            },

            selected = pantallaActual == SupervisorRoutes.PANEL,

            onClick = {
                alSeleccionar(SupervisorRoutes.PANEL)
            },

            modifier = Modifier.padding(
                horizontal = 8.dp,
                vertical = 3.dp
            ),

            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = AzulSeleccion,
                selectedIconColor = Azul,
                selectedTextColor = Color(0xFF173D78),
                unselectedContainerColor = Color.Transparent
            )
        )


        // OPCIÓN: MIS SUCURSALES

        NavigationDrawerItem(

            icon = {
                Icon(
                    Icons.Default.Store,
                    contentDescription = null
                )
            },

            label = {
                Text("Mis sucursales", fontSize = 12.sp)
            },

            selected = pantallaActual == SupervisorRoutes.SUCURSALES,

            onClick = {
                alSeleccionar(SupervisorRoutes.SUCURSALES)
            },

            modifier = Modifier.padding(
                horizontal = 8.dp,
                vertical = 3.dp
            ),

            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = AzulSeleccion,
                selectedIconColor = Azul,
                selectedTextColor = Color(0xFF173D78)
            )
        )


        // OPCIÓN: ASISTENTE IA


        NavigationDrawerItem(

            icon = {
                Icon(
                    Icons.AutoMirrored.Filled.Chat,
                    contentDescription = null
                )
            },

            label = {
                Text("Asistente IA", fontSize = 12.sp)
            },

            selected = pantallaActual == SupervisorRoutes.ASISTENTE,

            onClick = {
                alSeleccionar(SupervisorRoutes.ASISTENTE)
            },

            modifier = Modifier.padding(
                horizontal = 8.dp,
                vertical = 3.dp
            ),

            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = AzulSeleccion,
                selectedIconColor = Azul,
                selectedTextColor = Color(0xFF173D78)
            )
        )


        // OPCIÓN: HISTORIAL DE REPORTES

        NavigationDrawerItem(

            icon = {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null
                )
            },

            label = {
                Text("Historial de reportes", fontSize = 12.sp)
            },

            selected = pantallaActual == SupervisorRoutes.HISTORIAL,

            onClick = {
                alSeleccionar(SupervisorRoutes.HISTORIAL)
            },

            modifier = Modifier.padding(
                horizontal = 8.dp,
                vertical = 3.dp
            ),

            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = AzulSeleccion,
                selectedIconColor = Azul,
                selectedTextColor = Color(0xFF173D78)
            )
        )


        // OPCIÓN: HISTORIAL DE GRÁFICOS


        NavigationDrawerItem(

            icon = {
                Icon(
                    Icons.Default.BarChart,
                    contentDescription = null
                )
            },

            label = {
                Text("Historial de gráficos", fontSize = 12.sp)
            },

            selected = pantallaActual == SupervisorRoutes.HISTORIAL_GRAFICOS,

            onClick = {
                alSeleccionar(SupervisorRoutes.HISTORIAL_GRAFICOS)
            },

            modifier = Modifier.padding(
                horizontal = 8.dp,
                vertical = 3.dp
            ),

            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = AzulSeleccion,
                selectedIconColor = Azul,
                selectedTextColor = Color(0xFF173D78)
            )
        )


        Spacer(Modifier.weight(1f))
    }
}
