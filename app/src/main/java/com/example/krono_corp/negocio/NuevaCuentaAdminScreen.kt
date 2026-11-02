package com.example.krono_corp.negocio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.krono_corp.data.NegocioRepository
import kotlinx.coroutines.launch

// Modulo de Persona 4: permite crear otra cuenta para administrar el mismo negocio
// sin tener que ingresar otro correo electronico (usa el correo del negocio).
// Las cuentas se guardan y se leen de la coleccion "cuentas_administracion" en Appwrite.

private const val CONTRASENA_MINIMA = 4

@Composable
fun NuevaCuentaAdminScreen(
    navController: NavController,
    negocioData: NegocioData
) {
    var nombre by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var mensajeExito by remember { mutableStateOf("") }
    var guardando by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Carga las cuentas ya creadas para este negocio al abrir la pantalla
    LaunchedEffect(negocioData.id) {
        if (negocioData.id.isEmpty()) return@LaunchedEffect
        NegocioRepository.listarCuentasAdministracion(negocioData.id).onSuccess { remotas ->
            negocioData.cuentasAdicionales.clear()
            negocioData.cuentasAdicionales.addAll(
                remotas.map { CuentaAdministracion(it.id, it.nombre, it.password) }
            )
        }
    }

    fun validarYCrear() {
        mensajeExito = ""
        val nombreLimpio = nombre.trim()

        when {
            negocioData.id.isEmpty() -> error = "Todavía se está cargando el negocio, espera un momento."
            nombreLimpio.isEmpty() -> error = "El nombre no puede estar vacío."
            negocioData.cuentasAdicionales.any { it.nombre.equals(nombreLimpio, ignoreCase = true) } ->
                error = "Ya existe una cuenta con el nombre \"$nombreLimpio\"."
            password.length < CONTRASENA_MINIMA ->
                error = "La contraseña debe tener al menos $CONTRASENA_MINIMA caracteres."
            password != confirmar -> error = "Las contraseñas no coinciden."
            negocioData.cuentasAdicionales.any { it.password.equals(password, ignoreCase = true) } ->
                error = "Esa contraseña ya la usa otra cuenta de este negocio. Elige una distinta."
            else -> {
                error = ""
                guardando = true
                scope.launch {
                    NegocioRepository.crearCuentaAdministracion(
                        negocioId = negocioData.id,
                        ownerId = negocioData.ownerId,
                        nombre = nombreLimpio,
                        password = password
                    ).onSuccess { remota ->
                        negocioData.cuentasAdicionales.add(
                            CuentaAdministracion(remota.id, remota.nombre, remota.password)
                        )
                        mensajeExito = "Cuenta \"$nombreLimpio\" creada correctamente."
                        nombre = ""
                        password = ""
                        confirmar = ""
                    }.onFailure { e ->
                        error = e.message ?: "No se pudo crear la cuenta."
                    }
                    guardando = false
                }
            }
        }
    }

    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            KronoBottomBar(navController = navController, currentRoute = Screen.NuevaCuentaAdmin.route)
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                KronoTopAvatarBar(
                    letraAvatar = "🔑",
                    onBackClick = {
                        if (!navController.popBackStack()) navController.navigate(Screen.Home.route)
                    },
                    onGearClick = { navController.navigate(Screen.Configuracion.route) }
                )
            }

            item {
                Column {
                    Text(
                        text = "Nueva cuenta de\nadministración",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = KronoTextDark,
                        lineHeight = 30.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Se creará otra cuenta para administrar \"${negocioData.nombreNegocio}\" " +
                            "con el correo ${negocioData.propietarioEmail}. No necesitas ingresar otro correo.",
                        fontSize = 13.sp,
                        color = KronoTextMuted
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it; error = "" },
                    label = { Text("Nombre de la nueva cuenta") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = "" },
                    label = { Text("Contraseña (mínimo $CONTRASENA_MINIMA caracteres)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = confirmar,
                    onValueChange = { confirmar = it; error = "" },
                    label = { Text("Confirmar contraseña") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (error.isNotBlank()) {
                item {
                    Text(error, color = Color(0xFFEB5757), fontSize = 13.sp)
                }
            }
            if (mensajeExito.isNotBlank()) {
                item {
                    Text(mensajeExito, color = Color(0xFF1EB564), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Para entrar con ella, inicia sesión con el correo ${negocioData.propietarioEmail} " +
                            "y esta nueva contraseña.",
                        fontSize = 12.sp,
                        color = KronoTextMuted
                    )
                }
            }

            item {
                Button(
                    onClick = { validarYCrear() },
                    enabled = !guardando,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KronoBlue)
                ) {
                    if (guardando) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Crear cuenta", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (negocioData.cuentasAdicionales.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Cuentas de este negocio",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = KronoTextDark
                    )
                }
                items(negocioData.cuentasAdicionales) { cuenta ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(KronoSurfaceVariant)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Person,
                            contentDescription = null,
                            tint = KronoBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(cuenta.nombre, fontSize = 13.sp, color = KronoTextDark)
                    }
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun NuevaCuentaAdminScreenPreview() {
    MaterialTheme {
        NuevaCuentaAdminScreen(
            navController = rememberNavController(),
            negocioData = NegocioData()
        )
    }
}
