package com.example.krono_corp.Admin

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.krono_corp.data.AppUser

@Composable
fun MiNegocioGerenteScreen(
    usuario: AppUser,
    alVolver: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = alVolver) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Mi Negocio",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = AdminTexto
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        TarjetaBlanca {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Store,
                    contentDescription = null,
                    tint = AdminAzul,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Información del Negocio", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AdminTexto)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Gerente / Administrador: ${usuario.alias}", fontSize = 13.sp, color = AdminGris)
                    Text("Correo: ${usuario.email}", fontSize = 13.sp, color = AdminGris)
                    Text("Rol: ${usuario.role.etiqueta}", fontSize = 13.sp, color = AdminGris)
                }
            }
        }
    }
}
