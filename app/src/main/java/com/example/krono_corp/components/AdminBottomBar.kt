package com.example.krono_corp.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.krono_corp.components.AdminRoutes

private val AzulInferior = Color(0xFF1260C4)
@Composable
fun AdminBottomBar(
    pantallaActual: String,
    alSeleccionar: (String) -> Unit
) {
    NavigationBar(
        containerColor = Color.White,
        contentColor = AzulInferior
    ) {
        // Inicio
        NavigationBarItem(
            selected = pantallaActual == AdminRoutes.PANEL,
            onClick = {
                alSeleccionar(AdminRoutes.PANEL)
            },
            icon = {
                Icon(
                    Icons.Default.Home,
                    contentDescription = "Inicio"
                )
            },
            label = { Text("Inicio") }
        )

        // Asistente
        NavigationBarItem(
            selected = pantallaActual == AdminRoutes.ASISTENTE,
            onClick = {
                alSeleccionar(AdminRoutes.ASISTENTE)
            },
            icon = {
                Icon(
                    Icons.Default.Chat,
                    contentDescription = "Asistente"
                )
            },
            label = { Text("Asistente") }
        )

        // Evaluación
        NavigationBarItem(
            selected = pantallaActual == AdminRoutes.EVALUACION,
            onClick = {
                alSeleccionar(AdminRoutes.EVALUACION)
            },
            icon = {
                Icon(
                    Icons.Default.Assessment,
                    contentDescription = "Evaluación de administrador"
                )
            },
            label = { Text("Evaluación") }
        )

        // Historial de reportes
        NavigationBarItem(
            selected = pantallaActual == AdminRoutes.HISTORIAL,
            onClick = {
                alSeleccionar(AdminRoutes.HISTORIAL)
            },
            icon = {
                Icon(
                    Icons.Default.Description,
                    contentDescription = "Historial de reportes"
                )
            },
            label = { Text("Historial") }
        )

        // Añadir administrador
        NavigationBarItem(
            selected = pantallaActual == AdminRoutes.NUEVO_ADMIN,
            onClick = {
                alSeleccionar(AdminRoutes.NUEVO_ADMIN)
            },
            icon = {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Añadir administrador"
                )
            },
            label = { Text("Añadir") }
        )
    }
}