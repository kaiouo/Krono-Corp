package com.example.krono_corp.Login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.krono_corp.data.AppUser
import com.example.krono_corp.data.AuthRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Segundo paso del login: código de 6 dígitos enviado al correo de la cuenta.
@Composable
fun VerificarCodigoScreen(
    userId: String,
    email: String,
    onVerificado: (AppUser) -> Unit,
    onCancelar: () -> Unit
) {
    var codigo by rememberSaveable { mutableStateOf("") }
    var cargando by remember { mutableStateOf(false) }
    var reenviando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    // Segundos que faltan para poder pedir otro código
    var espera by remember { mutableIntStateOf(30) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(espera) {
        if (espera > 0) {
            delay(1000)
            espera -= 1
        }
    }

    fun verificar() {
        if (cargando) return
        if (codigo.length != 6) {
            error = "Ingresa el código de 6 dígitos."
            return
        }
        error = null
        info = null
        scope.launch {
            cargando = true
            AuthRepository.confirmarCodigo(userId, codigo).fold(
                onSuccess = { onVerificado(it) },
                onFailure = { error = it.message }
            )
            cargando = false
        }
    }

    fun reenviar() {
        if (reenviando || espera > 0) return
        error = null
        info = null
        scope.launch {
            reenviando = true
            AuthRepository.reenviarCodigo(userId, email).fold(
                onSuccess = {
                    info = "Te enviamos un código nuevo."
                    codigo = ""
                    espera = 30
                },
                onFailure = { error = it.message }
            )
            reenviando = false
        }
    }

    AuthScaffold {
        Text(
            "Verifica tu identidad",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = KronoColors.TextoOscuro
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Enviamos un código de 6 dígitos a $email. Escríbelo para entrar. " +
                    "Si no lo ves, revisa la carpeta de spam.",
            fontSize = 14.sp,
            color = KronoColors.TextoSuave
        )

        Spacer(Modifier.height(24.dp))
        AuthTextField(
            label = "Código de verificación",
            value = codigo,
            onValueChange = { codigo = it.filter { c -> c.isDigit() }.take(6) },
            placeholder = "000000",
            icon = Icons.Default.Security,
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
            onDone = { verificar() }
        )

        AuthError(error)
        if (info != null) {
            Spacer(Modifier.height(12.dp))
            Text(text = info!!, color = KronoColors.Azul, fontSize = 13.sp)
        }

        Spacer(Modifier.height(20.dp))
        AuthButton(
            text = "Verificar",
            icon = Icons.Default.Check,
            loading = cargando,
            onClick = { verificar() }
        )

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val puedeReenviar = espera == 0 && !reenviando
            Text(
                text = when {
                    reenviando -> "Enviando…"
                    espera > 0 -> "Reenviar código en ${espera}s"
                    else -> "Reenviar código"
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (puedeReenviar) KronoColors.Azul else KronoColors.TextoSuave,
                modifier = if (puedeReenviar) Modifier.clickable { reenviar() } else Modifier
            )
        }

        Spacer(Modifier.height(16.dp))
        AuthFooter("¿Cuenta equivocada?", "Volver", onCancelar)
    }
}