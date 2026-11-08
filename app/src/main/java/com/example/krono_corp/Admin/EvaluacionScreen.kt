package com.example.krono_corp.Admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.krono_corp.data.AppUser
import com.example.krono_corp.data.CuentaSupervisor
import com.example.krono_corp.data.GerenteRepository
import com.example.krono_corp.data.SucursalesRepository
import com.example.krono_corp.negocio.SucursalItem
import kotlinx.coroutines.launch

/**
 * Pantalla de evaluación y gestión de supervisores asignados al gerente.
 * Muestra la lista de supervisores del equipo, su sucursal actual y
 * permite reasignarlos o quitarlos del equipo.
 */
@Composable
fun EvaluacionScreen(usuario: AppUser) {
    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var exito by remember { mutableStateOf<String?>(null) }
    var supervisores by remember { mutableStateOf<List<CuentaSupervisor>>(emptyList()) }
    var sucursales by remember { mutableStateOf<List<SucursalItem>>(emptyList()) }
    var busqueda by remember { mutableStateOf("") }
    var ocupadoId by remember { mutableStateOf<String?>(null) }
    var recarga by remember { mutableStateOf(0) }
    var aEliminar by remember { mutableStateOf<CuentaSupervisor?>(null) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(recarga) {
        cargando = true
        error = null
        GerenteRepository.supervisoresDe(usuario.id)
            .onSuccess { supervisores = it }
            .onFailure { error = it.message }

        SucursalesRepository.listar(usuario.id)
            .onSuccess { sucursales = it }
            .onFailure { if (error == null) error = it.message }
        cargando = false
    }

    val filtrados = supervisores.filter { s ->
        busqueda.isBlank() ||
                s.alias.contains(busqueda, ignoreCase = true) ||
                s.email.contains(busqueda, ignoreCase = true)
    }

    // Modal de confirmación para eliminar supervisor del equipo
    aEliminar?.let { cuenta ->
        AlertDialog(
            onDismissRequest = { aEliminar = null },
            title = { Text("Quitar del equipo") },
            text = { Text("¿Deseas quitar a ${cuenta.alias} (${cuenta.email}) de tu equipo? La cuenta no se borrará, pero quedará sin sucursal asignada.") },
            confirmButton = {
                Button(
                    onClick = {
                        val c = aEliminar
                        aEliminar = null
                        if (c != null) {
                            scope.launch {
                                ocupadoId = c.id
                                error = null
                                exito = null
                                GerenteRepository.quitar(c.id)
                                    .onSuccess {
                                        exito = "${c.alias} fue removido del equipo."
                                        recarga++
                                    }
                                    .onFailure { error = it.message }
                                ocupadoId = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AdminRojo)
                ) {
                    Text("Quitar", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { aEliminar = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text(
                "Mis supervisores",
                fontSize = 24.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                color = AdminTexto
            )
            Text(
                "Supervisión y asignación de sucursales a tu equipo",
                fontSize = 13.sp,
                color = AdminGris
            )
            Spacer(Modifier.height(8.dp))

            if (cargando) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }

            error?.let { err ->
                Text(err, color = AdminRojo, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
            }

            exito?.let { ex ->
                Text(ex, color = AdminVerde, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
            }

            TarjetaBlanca {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Equipo activo", fontSize = 12.sp, color = AdminGris)
                        Text(
                            "${supervisores.size} supervisores",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = AdminTexto
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Sucursales", fontSize = 12.sp, color = AdminGris)
                        Text(
                            "${sucursales.count { it.activa }} activas",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = AdminTexto
                        )
                    }
                }
            }
        }

        if (supervisores.isNotEmpty()) {
            item {
                OutlinedTextField(
                    value = busqueda,
                    onValueChange = { busqueda = it },
                    placeholder = { Text("Buscar supervisor por nombre o correo…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (!cargando && filtrados.isEmpty()) {
            item {
                if (supervisores.isEmpty()) {
                    TarjetaBlanca {
                        Text(
                            "No tienes supervisores asignados en tu equipo.",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = AdminTexto
                        )
                        Spacer(Modifier.height(4.dp))
                        TextoVacio("Usa la sección «Nuevo supervisor» para generar un enlace de invitación y asignar supervisores a tus sucursales.")
                    }
                } else {
                    TextoVacio("No se encontraron supervisores que coincidan con «$busqueda».")
                }
            }
        }

        items(filtrados, key = { it.id }) { cuenta ->
            FilaSupervisorEquipo(
                cuenta = cuenta,
                sucursales = sucursales,
                ocupado = ocupadoId == cuenta.id,
                alReasignar = { nuevaSucursal ->
                    scope.launch {
                        ocupadoId = cuenta.id
                        error = null
                        exito = null
                        GerenteRepository.asignar(cuenta.id, usuario.id, nuevaSucursal.id)
                            .onSuccess {
                                exito = "${cuenta.alias} fue reasignado a ${nuevaSucursal.nombre}."
                                recarga++
                            }
                            .onFailure { error = it.message }
                        ocupadoId = null
                    }
                },
                alQuitar = { aEliminar = cuenta }
            )
        }
    }
}

@Composable
private fun FilaSupervisorEquipo(
    cuenta: CuentaSupervisor,
    sucursales: List<SucursalItem>,
    ocupado: Boolean,
    alReasignar: (SucursalItem) -> Unit,
    alQuitar: () -> Unit
) {
    val sucursalActual = remember(cuenta.sucursal, sucursales) {
        resolverSucursal(cuenta.sucursal, sucursales)
    }

    var editando by remember { mutableStateOf(false) }
    var seleccionada by remember(cuenta.id, sucursales) {
        mutableStateOf(sucursalActual ?: sucursales.firstOrNull { it.activa } ?: sucursales.firstOrNull())
    }

    TarjetaBlanca {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = AdminAzul,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text(
                        cuenta.alias,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = AdminTexto
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(cuenta.email, fontSize = 13.sp, color = AdminGris)
                if (cuenta.phone.isNotBlank()) {
                    Text("Tel: ${cuenta.phone}", fontSize = 12.sp, color = AdminGris)
                }

                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Store,
                        contentDescription = null,
                        tint = AdminVerde,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(
                        text = sucursalActual?.nombre ?: if (cuenta.sucursal.isBlank()) "Sin sucursal" else cuenta.sucursal,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (sucursalActual != null) AdminVerde else AdminNaranja
                    )
                }
            }

            IconButton(
                onClick = alQuitar,
                enabled = !ocupado
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Quitar del equipo",
                    tint = AdminRojo
                )
            }
        }

        if (editando) {
            Spacer(Modifier.height(10.dp))
            SucursalSelector(
                sucursales = sucursales,
                seleccionada = seleccionada,
                alSeleccionar = { seleccionada = it }
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { editando = false }) {
                    Text("Cancelar")
                }
                Button(
                    onClick = {
                        editando = false
                        seleccionada?.let(alReasignar)
                    },
                    enabled = !ocupado && seleccionada != null && seleccionada?.id != sucursalActual?.id,
                    colors = ButtonDefaults.buttonColors(containerColor = AdminAzul)
                ) {
                    Text(if (ocupado) "Guardando…" else "Guardar cambio")
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { editando = true },
                enabled = !ocupado && sucursales.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cambiar sucursal")
            }
        }
    }
}
