package com.example.krono_corp.components


import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

import com.example.krono_corp.components.SupervisorRoutes

private val AzulInferiorSupervisor = Color(0xFF1260C4)

@Composable
fun SupervisorBottomBar(
    pantallaActual: String,
    alSeleccionar: (String) -> Unit
) {

    NavigationBar(
        containerColor = Color.White,
        contentColor = AzulInferiorSupervisor
    ) {

        // INICIO

        NavigationBarItem(
            selected = pantallaActual == SupervisorRoutes.PANEL,
            onClick = {
                alSeleccionar(SupervisorRoutes.PANEL)
            },
            icon = {
                Icon(
                    Icons.Default.Home,
                    contentDescription = "Inicio"
                )
            },
            label = {
                Text("Inicio")
            }
        )


        // ASISTENTE IA

        NavigationBarItem(
            selected = pantallaActual == SupervisorRoutes.ASISTENTE,
            onClick = {
                alSeleccionar(SupervisorRoutes.ASISTENTE)
            },
            icon = {
                Icon(
                    Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "Asistente"
                )
            },
            label = {
                Text("Asistente")
            }
        )


        // SUCURSALES

        NavigationBarItem(
            selected = pantallaActual == SupervisorRoutes.SUCURSALES,
            onClick = {
                alSeleccionar(SupervisorRoutes.SUCURSALES)
            },
            icon = {
                Icon(
                    Icons.Default.Store,
                    contentDescription = "Mis sucursales"
                )
            },
            label = {
                Text("Sucursales")
            }
        )


        // HISTORIAL DE REPORTES

        NavigationBarItem(
            selected = pantallaActual == SupervisorRoutes.HISTORIAL,
            onClick = {
                alSeleccionar(SupervisorRoutes.HISTORIAL)
            },
            icon = {
                Icon(
                    Icons.Default.Description,
                    contentDescription = "Historial de reportes"
                )
            },
            label = {
                Text("Reportes")
            }
        )


        // HISTORIAL DE GRÁFICOS

        NavigationBarItem(
            selected = pantallaActual == SupervisorRoutes.HISTORIAL_GRAFICOS,
            onClick = {
                alSeleccionar(SupervisorRoutes.HISTORIAL_GRAFICOS)
            },
            icon = {
                Icon(
                    Icons.Default.BarChart,
                    contentDescription = "Historial de gráficos"
                )
            },
            label = {
                Text("Gráficos")
            }
        )
    }
}
