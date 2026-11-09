package com.example.krono_corp.Admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.krono_corp.data.EQUIPOS

// Mismos colores de AdminScreen (compartidos por las pantallas del gerente)
internal val AdminAzul = Color(0xFF1677FF)
internal val AdminFondo = Color(0xFFF7F8FC)
internal val AdminTexto = Color(0xFF202633)
internal val AdminGris = Color(0xFF747780)
internal val AdminBorde = Color(0xFFD6DEEA)
internal val AdminVerde = Color(0xFF24A779)
internal val AdminNaranja = Color(0xFFE7A12B)
internal val AdminRojo = Color(0xFFD32F2F)

@Composable
internal fun TarjetaBlanca(
    modifier: Modifier = Modifier,
    contenido: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, AdminBorde)
    ) {
        Column(modifier = Modifier.padding(14.dp), content = contenido)
    }
}

@Composable
internal fun Etiqueta(texto: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(texto, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
internal fun TextoVacio(texto: String) {
    Text(texto, color = AdminGris, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
}

/** Dropdown con las sucursales (Norte, Centro, Sur) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SucursalDropdown(
    seleccionada: String,
    alSeleccionar: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expandido by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expandido,
        onExpandedChange = { expandido = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = seleccionada,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Sucursal") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandido) },
            // Si tu versión de material3 es anterior a 1.3 usa: Modifier.menuAnchor()
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expandido, onDismissRequest = { expandido = false }) {
            EQUIPOS.forEach { sucursal ->
                DropdownMenuItem(
                    text = { Text(sucursal) },
                    onClick = {
                        alSeleccionar(sucursal)
                        expandido = false
                    }
                )
            }
        }
    }
}
