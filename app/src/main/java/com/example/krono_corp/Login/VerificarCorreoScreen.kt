package com.example.krono_corp.Login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Se muestra justo después de registrarse: la cuenta existe pero falta confirmar el correo.
@Composable
fun VerificarCorreoScreen(
    correo: String,
    onIrLogin: () -> Unit
) {
    AuthScaffold {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(KronoColors.AzulClaro),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Email, contentDescription = null, tint = KronoColors.Azul)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Revisa tu correo",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = KronoColors.TextoOscuro
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Te enviamos un enlace de verificación a $correo. Tócalo desde tu celular para " +
                    "confirmar que la cuenta es tuya. Después podrás iniciar sesión.",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
            color = KronoColors.TextoSuave
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Si no lo ves, revisa la carpeta de spam. También puedes intentar iniciar sesión " +
                    "y te reenviaremos el enlace.",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            color = KronoColors.TextoSuave
        )

        Spacer(Modifier.height(24.dp))
        AuthButton(
            text = "Ir a iniciar sesión",
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            loading = false,
            onClick = onIrLogin
        )
    }
}