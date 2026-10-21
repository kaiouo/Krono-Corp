package com.example.krono_corp.negocio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController

@Composable
fun SucursalesScreen(
    navController: NavController,
    negocioData: NegocioData,
    onGuardarSucursales: (List<SucursalItem>) -> Unit = {}
) {
    val sucursales = remember { mutableStateListOf(*negocioData.sucursales.toTypedArray()) }
    var textoBusqueda by remember { mutableStateOf("") }

    var mostrarDialogo by remember { mutableStateOf(false) }
    var nombreNuevaSucursal by remember { mutableStateOf("") }
    var codigoSucursalDialogo by remember { mutableStateOf("") }
    var estaActivaDialogo by remember { mutableStateOf(true) }
    var indiceEditando by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            KronoBottomBar(navController = navController, currentRoute = Screen.Sucursales.route)
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                KronoTopAvatarBar(
                    letraAvatar = "🏢",
                    onBackClick = {
                        if (!navController.popBackStack()) {
                            navController.navigate(Screen.Home.route)
                        }
                    },
                    onGearClick = { navController.navigate(Screen.Configuracion.route) }
                )
            }

            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Administración de\nSucursales",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = KronoTextDark,
                        lineHeight = 30.sp
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = textoBusqueda,
                    onValueChange = { textoBusqueda = it },
                    placeholder = { Text("Buscar sucursales", color = KronoTextMuted, fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Buscar",
                            tint = KronoTextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = KronoSurfaceVariant,
                        focusedContainerColor = Color.White,
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = KronoBlue
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            itemsIndexed(
                sucursales.filter { it.nombre.contains(textoBusqueda, ignoreCase = true) }
            ) { index, sucursal ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFEBF3FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Storefront,
                                contentDescription = null,
                                tint = KronoBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sucursal.nombre,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = KronoTextDark
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (sucursal.activa) KronoGreenBadge else Color(0xFFFFECEC))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (sucursal.activa) "ACTIVA" else "INACTIVA",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (sucursal.activa) KronoGreenText else Color(0xFFEB5757)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                nombreNuevaSucursal = sucursal.nombre
                                codigoSucursalDialogo = sucursal.codigoAcceso
                                estaActivaDialogo = sucursal.activa
                                indiceEditando = index
                                mostrarDialogo = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Editar",
                                tint = KronoTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                sucursales.removeAt(index)
                                onGuardarSucursales(sucursales.toList())
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Eliminar",
                                tint = Color(0xFFEB5757),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                FloatingActionButton(
                    onClick = {
                        nombreNuevaSucursal = ""
                        codigoSucursalDialogo = ""
                        estaActivaDialogo = true
                        indiceEditando = null
                        mostrarDialogo = true
                    },
                    containerColor = KronoBlue,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Agregar",
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        nombreNuevaSucursal = ""
                        codigoSucursalDialogo = ""
                        estaActivaDialogo = true
                        indiceEditando = null
                        mostrarDialogo = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, KronoBlue),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = KronoBlue)
                ) {
                    Text(
                        text = "Agregar Sucursal",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (mostrarDialogo) {
        AlertDialog(
            onDismissRequest = { mostrarDialogo = false },
            title = {
                Text(
                    text = if (indiceEditando == null) "Nueva Sucursal" else "Editar Sucursal",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nombreNuevaSucursal,
                        onValueChange = { nombreNuevaSucursal = it },
                        label = { Text("Nombre de la sucursal") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = codigoSucursalDialogo,
                        onValueChange = { codigoSucursalDialogo = it },
                        label = { Text("Código de acceso") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Estado de la sede:", fontSize = 14.sp)
                        Switch(
                            checked = estaActivaDialogo,
                            onCheckedChange = { estaActivaDialogo = it }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nombreNuevaSucursal.trim().isNotEmpty()) {
                            val idx = indiceEditando
                            if (idx == null) {
                                val id = "SUC-${sucursales.size + 1}"
                                sucursales.add(
                                    SucursalItem(
                                        id = id,
                                        nombre = nombreNuevaSucursal.trim(),
                                        activa = estaActivaDialogo,
                                        codigoAcceso = codigoSucursalDialogo.trim().ifEmpty { "1234" }
                                    )
                                )
                            } else {
                                sucursales[idx] = sucursales[idx].copy(
                                    nombre = nombreNuevaSucursal.trim(),
                                    activa = estaActivaDialogo,
                                    codigoAcceso = codigoSucursalDialogo.trim().ifEmpty { "1234" }
                                )
                            }
                            onGuardarSucursales(sucursales.toList())
                            mostrarDialogo = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KronoBlue)
                ) {
                    Text(if (indiceEditando == null) "Guardar" else "Actualizar")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogo = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun SucursalesScreenPreview() {
    MaterialTheme {
        SucursalesScreen(
            navController = rememberNavController(),
            negocioData = NegocioData()
        )
    }
}