package com.example.krono_corp.Login

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.krono_corp.data.AuthRepository
import kotlinx.coroutines.launch

/** Pantalla que abre el enlace de recuperación (kronocorp://recovery?userId=...&secret=...). */
@Composable
fun ResetPasswordScreen(userId: String, secret: String, onListo: () -> Unit) {
    var clave by rememberSaveable { mutableStateOf("") }
    var confirmar by rememberSaveable { mutableStateOf("") }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AuthScaffold {
        Text(
            "Elige tu nueva contraseña",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = KronoColors.TextoOscuro
        )
        Spacer(Modifier.height(24.dp))

        AuthTextField(
            label = "Nueva contraseña",
            value = clave,
            onValueChange = { clave = it },
            placeholder = "••••••••",
            icon = Icons.Default.Lock,
            isPassword = true
        )
        Spacer(Modifier.height(16.dp))
        AuthTextField(
            label = "Confirmar contraseña",
            value = confirmar,
            onValueChange = { confirmar = it },
            placeholder = "••••••••",
            icon = Icons.Default.Lock,
            isPassword = true,
            imeAction = ImeAction.Done
        )

        AuthError(error)

        Spacer(Modifier.height(24.dp))
        AuthButton(
            text = "Guardar contraseña",
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            loading = cargando,
            onClick = {
                error = when {
                    clave.length < 8 -> "La contraseña debe tener al menos 8 caracteres."
                    clave != confirmar -> "Las contraseñas no coinciden."
                    else -> null
                }
                if (error != null) return@AuthButton
                scope.launch {
                    cargando = true
                    AuthRepository.confirmarRecuperacion(userId, secret, clave).fold(
                        onSuccess = { onListo() },
                        onFailure = { error = it.message }
                    )
                    cargando = false
                }
            }
        )
    }
}
