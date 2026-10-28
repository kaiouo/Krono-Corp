package com.example.krono_corp.Login

import android.util.Patterns
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Paleta tomada de AdminScreen.kt + el azul de los mockups */
object KronoColors {
    val Fondo = Color(0xFFF7F8FC)        // mismo fondo de AdminScreen
    val Borde = Color(0xFFD6DEEA)        // mismo borde de las tarjetas de AdminScreen
    val TextoOscuro = Color(0xFF202633)  // mismo texto de TarjetaAdmin
    val TextoSuave = Color(0xFF6B7280)
    val Campo = Color(0xFFF3F5F9)
    val Azul = Color(0xFF0A5BC4)         // azul de los botones del mockup
    val AzulClaro = Color(0xFFE8EEF9)
    val Error = Color(0xFFD32F2F)
}

fun esCorreoValido(correo: String) = Patterns.EMAIL_ADDRESS.matcher(correo.trim()).matches()

fun esTelefonoValido(telefono: String) = telefono.count { it.isDigit() } >= 8

/** Fondo + tarjeta blanca centrada (estructura común de login y registro) */
@Composable
fun AuthScaffold(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KronoColors.Fondo)
            .systemBarsPadding()   // necesario porque MainActivity usa enableEdgeToEdge()
            .imePadding(),         // sube el contenido cuando aparece el teclado
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, KronoColors.Borde)
            ) {
                Column(modifier = Modifier.padding(24.dp), content = content)
            }
        }
    }
}

@Composable
fun AuthTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
    labelAction: (@Composable () -> Unit)? = null
) {
    var visible by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = KronoColors.TextoOscuro
            )
            labelAction?.invoke()
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(placeholder, color = Color(0xFF9AA3B2), fontSize = 14.sp) },
            leadingIcon = { Icon(icon, contentDescription = null, tint = KronoColors.TextoSuave) },
            trailingIcon = if (isPassword) {
                {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (visible) "Ocultar contraseña" else "Mostrar contraseña",
                            tint = KronoColors.TextoSuave
                        )
                    }
                }
            } else null,
            visualTransformation = if (isPassword && !visible) PasswordVisualTransformation()
            else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
                imeAction = imeAction
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = KronoColors.Campo,
                unfocusedContainerColor = KronoColors.Campo,
                focusedBorderColor = KronoColors.Azul,
                unfocusedBorderColor = KronoColors.Borde,
                cursorColor = KronoColors.Azul
            )
        )
    }
}

@Composable
fun AuthButton(
    text: String,
    icon: ImageVector,
    loading: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = !loading,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = KronoColors.Azul)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        } else {
            Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun AuthError(mensaje: String?) {
    if (mensaje != null) {
        Spacer(Modifier.height(12.dp))
        Text(text = mensaje, color = KronoColors.Error, fontSize = 13.sp)
    }
}

@Composable
fun AuthFooter(pregunta: String, accion: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$pregunta ", fontSize = 13.sp, color = KronoColors.TextoSuave)
        Text(
            text = accion,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = KronoColors.Azul,
            modifier = Modifier.clickable(onClick = onClick)
        )
    }
}