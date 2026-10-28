package com.example.krono_corp.Login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.krono_corp.data.AppUser
import com.example.krono_corp.data.AuthRepository
import com.example.krono_corp.data.Rol
import kotlinx.coroutines.launch

@Composable
fun RegisterScreen(
    onRegisterSuccess: (AppUser) -> Unit,
    onGoToLogin: () -> Unit
) {
    var correo by rememberSaveable { mutableStateOf("") }
    var telefono by rememberSaveable { mutableStateOf("") }
    // El mockup muestra "Supervisor de ventas" preseleccionado
    var rol by rememberSaveable { mutableStateOf(Rol.SUPERVISOR) }
    var clave by rememberSaveable { mutableStateOf("") }
    var confirmar by rememberSaveable { mutableStateOf("") }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun registrar() {
        error = when {
            correo.isBlank() || telefono.isBlank() || clave.isBlank() || confirmar.isBlank() ->
                "Completa todos los campos."
            !esCorreoValido(correo) -> "Ingresa un correo válido."
            !esTelefonoValido(telefono) -> "Ingresa un número de teléfono válido."
            clave.length < 8 -> "La contraseña debe tener al menos 8 caracteres."
            clave != confirmar -> "Las contraseñas no coinciden."
            else -> null
        }
        if (error != null) return

        scope.launch {
            cargando = true
            // Se guarda con el prefijo +503 si el usuario no lo escribió
            val tel = telefono.trim().let { if (it.startsWith("+")) it else "+503 $it" }
            AuthRepository.register(correo.trim(), tel, clave, rol).fold(
                onSuccess = { onRegisterSuccess(it) },
                onFailure = { error = it.message }
            )
            cargando = false
        }
    }

    AuthScaffold {
        // Icono de escudo centrado
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(KronoColors.AzulClaro),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Security, contentDescription = null, tint = KronoColors.Azul)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Crear una cuenta",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = KronoColors.TextoOscuro
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Acceso al sistema administrativo corporativo",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 13.sp,
            color = KronoColors.TextoSuave
        )

        Spacer(Modifier.height(20.dp))
        AuthTextField(
            label = "Correo Gmail",
            value = correo,
            onValueChange = { correo = it },
            placeholder = "ejemplo@gmail.com",
            icon = Icons.Default.Email,
            keyboardType = KeyboardType.Email
        )

        Spacer(Modifier.height(14.dp))
        AuthTextField(
            label = "Número de teléfono",
            value = telefono,
            onValueChange = { telefono = it },
            placeholder = "+503 0000 0000",
            icon = Icons.Default.Phone,
            keyboardType = KeyboardType.Phone
        )

        Spacer(Modifier.height(14.dp))
        RoleDropdown(
            label = "Roles",
            seleccionado = rol,
            onSeleccion = { rol = it }
        )

        Spacer(Modifier.height(14.dp))
        AuthTextField(
            label = "Contraseña",
            value = clave,
            onValueChange = { clave = it },
            placeholder = "••••••••",
            icon = Icons.Default.Lock,
            isPassword = true
        )

        Spacer(Modifier.height(14.dp))
        AuthTextField(
            label = "Confirmar contraseña",
            value = confirmar,
            onValueChange = { confirmar = it },
            placeholder = "••••••••",
            icon = Icons.Default.Lock,
            isPassword = true,
            imeAction = ImeAction.Done,
            onDone = { registrar() }
        )

        AuthError(error)

        Spacer(Modifier.height(20.dp))
        AuthButton(
            text = "Registrarse",
            icon = Icons.Default.PersonAdd,
            loading = cargando,
            onClick = { registrar() }
        )

        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(KronoColors.Borde)
        )
        Spacer(Modifier.height(16.dp))
        AuthFooter("¿Ya tienes una cuenta?", "Inicia Sesión", onGoToLogin)
    }
}