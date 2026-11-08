package com.example.krono_corp.Admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.krono_corp.data.AppUser
import com.example.krono_corp.data.CuentaSupervisor
import com.example.krono_corp.data.GerenteRepository
import com.example.krono_corp.data.Invitacion
import com.example.krono_corp.data.InvitacionRepository
import com.example.krono_corp.data.SucursalesRepository
import com.example.krono_corp.negocio.SucursalItem
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Nuevo supervisor":
 * 1) El gerente genera un enlace de invitación y lo comparte.
 * 2) Quien lo abre se registra directamente con el rol Supervisor de Ventas.
 * 3) Esa persona aparece aquí (solo para el gerente que compartió el enlace)
 *    para ubicarla en una de las sucursales que el gerente creó.
 *    En Appwrite se guarda el ID de la sucursal.
 */
@Composable
fun NuevoAdministradorScreen(usuario: AppUser) {
    val context = LocalContext.current
    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var exito by remember { mutableStateOf<String?>(null) }
    var pendientes by remember { mutableStateOf<List<CuentaSupervisor>>(emptyList()) }
    var sucursales by remember { mutableStateOf<List<SucursalItem>>(emptyList()) }
    var ocupadoId by remember { mutableStateOf<String?>(null) }
    var recarga by remember { mutableStateOf(0) }

    // Enlace de invitación
    var invitacion by remember { mutableStateOf<Invitacion?>(null) }
    var generando by remember { mutableStateOf(false) }
    var errorEnlace by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    // Supervisores que se registraron con el enlace de ESTE gerente y esperan sucursal
    LaunchedEffect(recarga) {
        cargando = true
        error = null
        GerenteRepository.sinAsignarDe(usuario.id)
            .onSuccess { pendientes = it }
            .onFailure { error = it.message }
        SucursalesRepository.listar(usuario.id)
            .onSuccess { sucursales = it }
            .onFailure { if (error == null) error = it.message }
        cargando = false
    }

    // Si ya tiene un enlace vigente, se muestra en vez de crear otro
    LaunchedEffect(usuario.id) {
        InvitacionRepository.vigente(usuario.id)
            .onSuccess { invitacion = it; errorEnlace = null }
            .onFailure { errorEnlace = it.message }
    }

    fun generarEnlace() {
        if (generando) return
        scope.launch {
            generando = true
            errorEnlace = null
            InvitacionRepository.crear(usuario.id)
                .onSuccess { invitacion = it }
                .onFailure { errorEnlace = it.message }
            generando = false
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text("Nuevo supervisor", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = AdminTexto)
            Spacer(Modifier.height(6.dp))
            Text(
                "Genera un enlace de invitación y compártelo. Quien lo abra se registra como " +
                        "«Supervisor de Ventas» y aparecerá aquí para que lo ubiques en una sucursal.",
                fontSize = 13.sp,
                color = AdminGris
            )
        }

        // ---------- Enlace de invitación ----------
        item {
            TarjetaBlanca {
                Text(
                    "Enlace de invitación",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = AdminTexto
                )
                Spacer(Modifier.height(8.dp))

                val inv = invitacion
                if (inv == null) {
                    Text(
                        "Todavía no tienes un enlace activo. El enlace dura 7 días.",
                        fontSize = 13.sp,
                        color = AdminGris
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { generarEnlace() },
                        enabled = !generando,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AdminAzul)
                    ) {
                        Text(if (generando) "Generando…" else "Generar enlace de invitación")
                    }
                } else {
                    Text(inv.enlace, fontSize = 12.sp, color = AdminAzul)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Vence el ${fechaVencimientoInvitacion(inv.expiraEn)}",
                        fontSize = 12.sp,
                        color = AdminGris
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { copiarEnlaceInvitacion(context, inv.enlace) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Copiar") }
                        Button(
                            onClick = { compartirEnlaceInvitacion(context, inv) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = AdminAzul)
                        ) { Text("Compartir") }
                    }
                    TextButton(onClick = { generarEnlace() }, enabled = !generando) {
                        Text(
                            if (generando) "Generando…"
                            else "Generar enlace nuevo (el anterior dejará de servir)"
                        )
                    }
                }

                errorEnlace?.let { mensaje ->
                    Spacer(Modifier.height(6.dp))
                    Text(mensaje, color = AdminRojo, fontSize = 13.sp)
                }
            }
        }

        // ---------- Supervisores pendientes de ubicar ----------
        item {
            Text(
                "Pendientes de ubicar",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AdminTexto
            )
            TextButton(onClick = { recarga++ }, enabled = !cargando) { Text("Actualizar lista") }
        }

        if (cargando) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        error?.let { mensaje -> item { Text(mensaje, color = AdminRojo, fontSize = 13.sp) } }
        exito?.let { mensaje ->
            item { Text(mensaje, color = AdminVerde, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }

        if (!cargando && sucursales.isEmpty()) {
            item {
                TextoVacio(
                    "Todavía no tienes sucursales. Primero crea una en «Sucursales» " +
                            "para poder ubicar a tus supervisores."
                )
            }
        }

        if (!cargando && error == null && pendientes.isEmpty()) {
            item {
                TextoVacio(
                    "Nadie está pendiente. Comparte tu enlace de invitación y, cuando la persona " +
                            "se registre, toca «Actualizar lista»."
                )
            }
        }

        items(pendientes, key = { it.id }) { cuenta ->
            FilaSinAsignar(
                cuenta = cuenta,
                sucursales = sucursales,
                ocupado = ocupadoId == cuenta.id,
                alAsignar = { sucursal ->
                    scope.launch {
                        ocupadoId = cuenta.id
                        exito = null
                        error = null
                        // Se guarda el ID de la sucursal, no el nombre
                        GerenteRepository.asignar(cuenta.id, usuario.id, sucursal.id)
                            .onSuccess {
                                exito = "${cuenta.alias} ahora es supervisor de ${sucursal.nombre}."
                                recarga++
                            }
                            .onFailure { error = it.message }
                        ocupadoId = null
                    }
                }
            )
        }
    }
}

@Composable
private fun FilaSinAsignar(
    cuenta: CuentaSupervisor,
    sucursales: List<SucursalItem>,
    ocupado: Boolean,
    alAsignar: (SucursalItem) -> Unit
) {
    var elegida by remember(cuenta.id, sucursales) {
        mutableStateOf(sucursales.firstOrNull { it.activa } ?: sucursales.firstOrNull())
    }

    TarjetaBlanca {
        Text(cuenta.alias, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AdminTexto)
        Text(cuenta.email, fontSize = 13.sp, color = AdminGris)
        if (cuenta.phone.isNotBlank()) Text(cuenta.phone, fontSize = 13.sp, color = AdminGris)
        Spacer(Modifier.height(10.dp))
        SucursalSelector(
            sucursales = sucursales,
            seleccionada = elegida,
            alSeleccionar = { elegida = it }
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { elegida?.let(alAsignar) },
            enabled = !ocupado && elegida != null,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AdminAzul)
        ) {
            Text(if (ocupado) "Asignando…" else "Asignar a mi equipo")
        }
    }
}

// ---------- helpers del enlace ----------

private fun fechaVencimientoInvitacion(milisegundos: Long): String =
    SimpleDateFormat("dd/MM/yyyy", Locale("es", "SV")).format(Date(milisegundos))

private fun copiarEnlaceInvitacion(context: Context, enlace: String) {
    val portapapeles = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    portapapeles.setPrimaryClip(ClipData.newPlainText("Enlace de invitación", enlace))
    Toast.makeText(context, "Enlace copiado", Toast.LENGTH_SHORT).show()
}

private fun compartirEnlaceInvitacion(context: Context, invitacion: Invitacion) {
    val mensaje = "Te invito a unirte a mi equipo en Krono como Supervisor de Ventas. " +
            "Regístrate con este enlace (válido hasta el ${fechaVencimientoInvitacion(invitacion.expiraEn)}): " +
            invitacion.enlace
    val envio = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Invitación a Krono")
        putExtra(Intent.EXTRA_TEXT, mensaje)
    }
    context.startActivity(Intent.createChooser(envio, "Compartir invitación"))
}