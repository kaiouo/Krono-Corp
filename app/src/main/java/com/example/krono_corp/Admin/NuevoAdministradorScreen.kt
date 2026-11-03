package com.example.krono_corp.Admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.krono_corp.data.AppUser
import com.example.krono_corp.data.CuentaSupervisor
import com.example.krono_corp.data.EQUIPOS
import com.example.krono_corp.data.GerenteRepository
import kotlinx.coroutines.launch

/**
 * "Nuevo supervisor": el gerente asigna a su equipo las cuentas que se registraron
 * con el rol Supervisor de Ventas y todavía no tienen gerente.
 */
@Composable
fun NuevoAdministradorScreen(usuario: AppUser) {
    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var exito by remember { mutableStateOf<String?>(null) }
    var pendientes by remember { mutableStateOf<List<CuentaSupervisor>>(emptyList()) }
    var ocupadoId by remember { mutableStateOf<String?>(null) }
    var recarga by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(recarga) {
        cargando = true
        GerenteRepository.sinAsignar()
            .onSuccess { pendientes = it; error = null }
            .onFailure { error = it.message }
        cargando = false
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text("Nuevo supervisor", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = AdminTexto)
            Spacer(Modifier.height(6.dp))
            Text(
                "Un supervisor se registra desde la pantalla de registro de la app eligiendo el rol " +
                        "«Supervisor de Ventas». Cuando lo haga, aparecerá aquí para que lo asignes a tu " +
                        "equipo y a una sucursal.",
                fontSize = 13.sp,
                color = AdminGris
            )
            TextButton(onClick = { recarga++ }, enabled = !cargando) { Text("Actualizar lista") }
        }

        if (cargando) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        error?.let { mensaje -> item { Text(mensaje, color = AdminRojo, fontSize = 13.sp) } }
        exito?.let { mensaje ->
            item { Text(mensaje, color = AdminVerde, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }

        if (!cargando && error == null && pendientes.isEmpty()) {
            item {
                TextoVacio(
                    "No hay cuentas de supervisor pendientes. Pídele a la persona que se registre " +
                            "con el rol «Supervisor de Ventas» y toca «Actualizar lista»."
                )
            }
        }

        items(pendientes, key = { it.id }) { cuenta ->
            FilaSinAsignar(
                cuenta = cuenta,
                ocupado = ocupadoId == cuenta.id,
                alAsignar = { sucursal ->
                    scope.launch {
                        ocupadoId = cuenta.id
                        exito = null
                        error = null
                        GerenteRepository.asignar(cuenta.id, usuario.id, sucursal)
                            .onSuccess {
                                exito = "${cuenta.alias} ahora es supervisor de la sucursal $sucursal."
                                recarga++
                            }
                            .onFailure { error = it.message }
                        ocupadoId = null
                    }
                }
            )
        }
    }
}

@Composable
private fun FilaSinAsignar(
    cuenta: CuentaSupervisor,
    ocupado: Boolean,
    alAsignar: (String) -> Unit
) {
    var sucursal by remember(cuenta.id) { mutableStateOf(EQUIPOS.first()) }

    TarjetaBlanca {
        Text(cuenta.alias, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AdminTexto)
        Text(cuenta.email, fontSize = 13.sp, color = AdminGris)
        if (cuenta.phone.isNotBlank()) Text(cuenta.phone, fontSize = 13.sp, color = AdminGris)
        Spacer(Modifier.height(10.dp))
        SucursalDropdown(seleccionada = sucursal, alSeleccionar = { sucursal = it })
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { alAsignar(sucursal) },
            enabled = !ocupado,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AdminAzul)
        ) {
            Text(if (ocupado) "Asignando…" else "Asignar a mi equipo")
        }
    }
}
