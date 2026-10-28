package com.example.krono_corp.Login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.krono_corp.data.AppUser
import com.example.krono_corp.data.AuthRepository
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginSuccess: (AppUser) -> Unit,
    onGoToRegister: () -> Unit,
    onForgotPassword: () -> Unit = {}
) {
    var correo by rememberSaveable { mutableStateOf("") }
    var clave by rememberSaveable { mutableStateOf("") }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun iniciarSesion() {
        error = when {
            correo.isBlank() || clave.isBlank() -> "Completa todos los campos."
            !esCorreoValido(correo) -> "Ingresa un correo válido."
            else -> null
        }
        if (error != null) return

        scope.launch {
            cargando = true
            AuthRepository.login(correo.trim(), clave).fold(
                onSuccess = { onLoginSuccess(it) },
                onFailure = { error = it.message }
            )
            cargando = false
        }
    }

    AuthScaffold {
        // Logo + nombre
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(KronoColors.Azul),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Business, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.width(10.dp))
            Text("Krono Corp", color = KronoColors.Azul, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Bienvenido de nuevo",
            fontSize = 28.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
            color = KronoColors.TextoOscuro
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Ingresa tus credenciales para acceder a tu panel de control.",
            fontSize = 14.sp,
            color = KronoColors.TextoSuave
        )

        Spacer(Modifier.height(24.dp))
        AuthTextField(
            label = "Correo electrónico",
            value = correo,
            onValueChange = { correo = it },
            placeholder = "tu@empresa.com",
            icon = Icons.Default.Email,
            keyboardType = KeyboardType.Email
        )

        Spacer(Modifier.height(16.dp))
        AuthTextField(
            label = "Contraseña",
            value = clave,
            onValueChange = { clave = it },
            placeholder = "••••••••",
            icon = Icons.Default.Lock,
            isPassword = true,
            imeAction = ImeAction.Done,
            onDone = { iniciarSesion() },
            labelAction = {
                Text(
                    "¿Olvidé mi contraseña?",
                    fontSize = 12.sp,
                    color = KronoColors.Azul,
                    modifier = Modifier.clickable(onClick = onForgotPassword)
                )
            }
        )

        AuthError(error)

        Spacer(Modifier.height(24.dp))
        AuthButton(
            text = "Iniciar Sesión",
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            loading = cargando,
            onClick = { iniciarSesion() }
        )

        Spacer(Modifier.height(24.dp))
        AuthFooter("¿No tienes una cuenta?", "Regístrate", onGoToRegister)
    }
}