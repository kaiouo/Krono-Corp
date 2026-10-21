package com.example.krono_corp.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

//para la barra superior
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.IconButton

object AdminRoutes {
    const val PANEL = "Panel de control"
    const val EVALUACION = "Evaluación de Administrador"

    const val HISTORIAL = "Historial de reportes"
    const val ASISTENTE = "Asistente"

    const val NUEVO_ADMIN = "Añadir Admin"
}

private val AzulSeleccion = Color(0xFFD9E4FF)
private val Azul = Color(0xFF1260C4)
private val Gris = Color(0xFF747780)
private val TextoAdmin = Color(0xFF202124)


//barra superior
@Composable
fun AdminTopBar(
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
                tint = TextoAdmin
            )
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = "Administración",
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = TextoAdmin
        )
    }
}

//barra lateral
@Composable
fun AdminSidebar(
    pantallaActual: String,
    alSeleccionar: (String) -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.width(290.dp),
        drawerContainerColor = Color(0xFFF7F9FC),
        drawerContentColor = TextoAdmin
    ) {
        Spacer(Modifier.height(18.dp))

        Text(
            text = "Administrador",
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.padding(
                start = 20.dp,
                end = 16.dp,
                bottom = 16.dp
            )
        )

        // Perfil del administrador.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sustituye este cuadro por una imagen de perfil si lo necesitas.
            Surface(
                modifier = Modifier.size(42.dp),
                shape = MaterialTheme.shapes.medium,
                color = Color(0xFFDCE6F1)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("👤", fontSize = 23.sp)
                }
            }

            Spacer(Modifier.width(12.dp))

            Column {
                Text(
                    "Usuario Admin",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Gerente de Operaciones",
                    fontSize = 11.sp,
                    color = Gris
                )
                Text(
                    "División HQ",
                    fontSize = 10.sp,
                    color = Gris
                )
            }
        }

        Spacer(Modifier.height(26.dp))

        // Opciones del sidebar.
        NavigationDrawerItem(
            icon = {
                Icon(
                    Icons.Default.Dashboard,
                    contentDescription = null
                )
            },
            label = { Text("Panel de Control", fontSize = 12.sp) },
            selected = pantallaActual == AdminRoutes.PANEL,
            onClick = {
                alSeleccionar(AdminRoutes.PANEL)
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

        NavigationDrawerItem(
            icon = {
                Icon(
                    Icons.Default.Assessment,
                    contentDescription = null
                )
            },
            label = {
                Text("Evaluación de Administrador", fontSize = 12.sp)
            },
            selected = pantallaActual == AdminRoutes.EVALUACION,
            onClick = {
                alSeleccionar(AdminRoutes.EVALUACION)
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

        NavigationDrawerItem(
            icon = {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null
                )
            },
            label = { Text("HISTORIAL", fontSize = 12.sp) },
            selected = pantallaActual == AdminRoutes.HISTORIAL,
            onClick = {
                alSeleccionar(AdminRoutes.HISTORIAL)
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
