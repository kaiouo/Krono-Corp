package com.example.krono_corp.Admin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.krono_corp.negocio.SucursalItem

/**
 * Busca la sucursal a la que apunta un valor guardado en Appwrite.
 * Lo normal es que sea el ID; si no coincide se intenta por nombre
 * (para datos viejos como "Norte"). Devuelve null si no hay coincidencia.
 */
fun resolverSucursal(valor: String, lista: List<SucursalItem>): SucursalItem? {
    if (valor.isBlank()) return null
    return lista.firstOrNull { it.id == valor }
        ?: lista.firstOrNull { it.nombre.equals(valor, ignoreCase = true) }
}

/** Menú desplegable simple con las sucursales que creó el gerente. */
@Composable
fun SucursalSelector(
    sucursales: List<SucursalItem>,
    seleccionada: SucursalItem?,
    alSeleccionar: (SucursalItem) -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true
) {
    var abierto by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { abierto = true },
            enabled = habilitado && sucursales.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = seleccionada?.nombre ?: "Elegir sucursal",
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }

        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            sucursales.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.nombre + if (s.activa) "" else " (inactiva)") },
                    onClick = {
                        abierto = false
                        alSeleccionar(s)
                    }
                )
            }
        }
    }
}