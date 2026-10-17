package com.example.krono_corp.negocio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController

data class AccionCardItem(
    val titulo: String,
    val icono: ImageVector,
    val iconColor: Color,
    val onClick: () -> Unit
)

@Composable
fun HomeScreen(
    navController: NavController,
    negocioData: NegocioData
) {
    Scaffold(
        containerColor = KronoBackground,
        bottomBar = {
            KronoBottomBar(navController = navController, currentRoute = Screen.Home.route)
        },
        floatingActionButton = {
            // Botón flotante IA de Figma (esquina inferior derecha)
            FloatingActionButton(
                onClick = { /* Módulo Asistente IA */ },
                containerColor = KronoBlue,
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .size(60.dp)
                    .offset(y = (-8).dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = "Asistente IA",
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header con ícono decorativo y avatar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE8F1FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ElectricBolt,
                        contentDescription = null,
                        tint = KronoBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD8D2C2)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Avatar",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Título principal
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(
                    text = "Administración de\nVentas",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = KronoTextDark,
                    lineHeight = 32.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Bienvenido, Admin",
                    fontSize = 14.sp,
                    color = KronoTextMuted
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "ACCIONES RÁPIDAS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = KronoTextDark,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            val acciones = listOf(
                AccionCardItem("Mi Negocio", Icons.Outlined.Storefront, KronoBlue) {
                    navController.navigate(Screen.MiNegocio.route)
                },
                AccionCardItem("Sucursales", Icons.Outlined.Domain, KronoBlue) {
                    navController.navigate(Screen.Sucursales.route)
                },
                AccionCardItem("Asistente", Icons.Outlined.ChatBubbleOutline, Color(0xFF2F80ED)) {},
                AccionCardItem("Historial de\nreportes", Icons.Outlined.Description, Color(0xFFF2994A)) {},
                AccionCardItem("Historial de\nGráficos", Icons.Outlined.BarChart, Color(0xFF27AE60)) {},
                AccionCardItem("Nueva cuenta de\nAdm.", Icons.Outlined.VpnKey, Color(0xFF9B51E0)) {}
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(acciones) { accion ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clickable { accion.onClick() },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = KronoCardBg),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(accion.iconColor.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = accion.icono,
                                    contentDescription = accion.titulo,
                                    tint = accion.iconColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = accion.titulo,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = KronoTextDark,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun HomeScreenPreview() {
    MaterialTheme {
        HomeScreen(
            navController = rememberNavController(),
            negocioData = NegocioData()
        )
    }
}