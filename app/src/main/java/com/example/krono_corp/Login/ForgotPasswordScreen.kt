package com.example.krono_corp.Login

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.krono_corp.data.AuthRepository
import kotlinx.coroutines.launch

// Item 1: "Olvidé mi contraseña" ya real. Appwrite envía un correo con un enlace
// kronocorp://recovery?userId=...&secret=... que abre ResetPasswordScreen directo en la app.
@Composable
fun ForgotPasswordScreen(onVolverLogin: () -> Unit) {
    var correo by rememberSaveable { mutableStateOf("") }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var enviado by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AuthScaffold {
        Text(
            "Recuperar contraseña",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = KronoColors.TextoOscuro
        )
        Spacer(Modifier.height(8.dp))

        if (enviado) {
            Text(
                "Listo. Revisa tu correo ($correo) y toca el enlace para elegir una nueva contraseña.",
                fontSize = 14.sp,
                color = KronoColors.TextoSuave
            )
            Spacer(Modifier.height(24.dp))
            AuthFooter("¿Ya la recuperaste?", "Inicia sesión", onVolverLogin)
        } else {
            Text(
                "Ingresa el correo con el que te registraste. Te enviaremos un enlace para " +
                    "elegir una nueva contraseña.",
                fontSize = 14.sp,
                color = KronoColors.TextoSuave
            )
            Spacer(Modifier.height(24.dp))
            AuthTextField(
                label = "Correo electrónico",
                value = correo,
                onValueChange = { correo = it },
                placeholder = "tu@empresa.com",
                icon = Icons.Default.Email
            )
            AuthError(error)
            Spacer(Modifier.height(24.dp))
            AuthButton(
                text = "Enviar enlace",
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                loading = cargando,
                onClick = {
                    if (!esCorreoValido(correo)) {
                        error = "Ingresa un correo válido."
                        return@AuthButton
                    }
                    error = null
                    scope.launch {
                        cargando = true
                        AuthRepository.solicitarRecuperacion(correo.trim()).fold(
                            onSuccess = { enviado = true },
                            onFailure = { error = it.message }
                        )
                        cargando = false
                    }
                }
            )
            Spacer(Modifier.height(24.dp))
            AuthFooter("¿Recordaste tu contraseña?", "Inicia sesión", onVolverLogin)
        }
    }
}
