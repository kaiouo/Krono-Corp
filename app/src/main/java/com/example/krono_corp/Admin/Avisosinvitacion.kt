package com.example.krono_corp.Admin

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.krono_corp.MainActivity
import com.example.krono_corp.data.RegistroInvitado
import com.example.krono_corp.data.RegistrosInvitacionRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** "08/11/2026, 05:23 PM" (hora local del teléfono). */
fun formatoFechaHora(milisegundos: Long): String =
    if (milisegundos <= 0L) "—"
    else SimpleDateFormat("dd/MM/yyyy, hh:mm a", Locale("es", "SV")).format(Date(milisegundos))

/**
 * Avisos para el gerente cuando un supervisor se registra con su enlace de invitación.
 *
 * - AdminScreen llama a revisar() cada 30 segundos mientras la app está abierta.
 * - Si aparece alguien nuevo: notificación del sistema + insignia roja en el panel y el menú.
 * - Lo que el gerente ya vio (o ya existía la primera vez que se usa) se guarda en
 *   SharedPreferences por gerente, así no se repite el aviso.
 */
object AvisosInvitacion {

    private const val PREFS = "krono_avisos_invitaciones"
    private const val CANAL = "krono_invitaciones"

    /** Todos los supervisores registrados con el enlace de este gerente (más reciente primero). */
    var registros by mutableStateOf<List<RegistroInvitado>>(emptyList())
        private set

    /** Cuántos todavía no han sido vistos (para las insignias). */
    var nuevos by mutableStateOf(0)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var cargado by mutableStateOf(false)
        private set

    /** true mientras NuevoAdministradorScreen está en pantalla (no hace falta notificar). */
    var listaAbierta = false

    fun reiniciar() {
        registros = emptyList()
        nuevos = 0
        error = null
        cargado = false
    }

    /** Consulta Appwrite y avisa si hay registros nuevos. */
    suspend fun revisar(context: Context, gerenteId: String) {
        val app = context.applicationContext
        RegistrosInvitacionRepository.listar(gerenteId)
            .onFailure { error = it.message }
            .onSuccess { lista ->
                error = null
                registros = lista
                cargado = true
                procesar(app, gerenteId, lista)
            }
    }

    /** IDs que el gerente ya vio (sirve para destacar los nuevos con la etiqueta «Nuevo»). */
    fun idsVistos(context: Context, gerenteId: String): Set<String> =
        prefs(context).getStringSet("vistos_$gerenteId", emptySet())?.toSet() ?: emptySet()

    /** Marca todo lo cargado como visto y quita sus notificaciones. */
    fun marcarVistos(context: Context, gerenteId: String) {
        val app = context.applicationContext
        val preferencias = prefs(app)
        val ids = registros.map { it.id }
        preferencias.edit()
            .putBoolean("ini_$gerenteId", true)
            .putStringSet("vistos_$gerenteId", idsVistos(app, gerenteId) + ids)
            .putStringSet("notif_$gerenteId", idsNotificados(app, gerenteId) + ids)
            .apply()
        nuevos = 0
        val manager = NotificationManagerCompat.from(app)
        ids.forEach { manager.cancel(it.hashCode()) }
    }

    // ---------- internos ----------

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun idsNotificados(context: Context, gerenteId: String): Set<String> =
        prefs(context).getStringSet("notif_$gerenteId", emptySet())?.toSet() ?: emptySet()

    private fun procesar(app: Context, gerenteId: String, lista: List<RegistroInvitado>) {
        val preferencias = prefs(app)
        val ids = lista.map { it.id }.toSet()

        // Primera vez en este dispositivo: lo que ya existía no cuenta como "nuevo"
        if (!preferencias.getBoolean("ini_$gerenteId", false)) {
            preferencias.edit()
                .putBoolean("ini_$gerenteId", true)
                .putStringSet("vistos_$gerenteId", ids)
                .putStringSet("notif_$gerenteId", ids)
                .apply()
            nuevos = 0
            return
        }

        val vistos = idsVistos(app, gerenteId)
        val notificados = idsNotificados(app, gerenteId)
        val sinVer = lista.filter { it.id !in vistos }
        nuevos = sinVer.size

        val porAvisar = sinVer.filter { it.id !in notificados }
        if (porAvisar.isEmpty()) return

        preferencias.edit()
            .putStringSet("notif_$gerenteId", notificados + porAvisar.map { it.id })
            .apply()

        if (!listaAbierta) {
            val enviada = notificar(app, porAvisar)
            // Si el sistema no deja mostrar notificaciones, al menos un aviso en pantalla
            if (!enviada) {
                val texto = if (porAvisar.size == 1) {
                    "Nuevo supervisor registrado: ${porAvisar.first().alias}"
                } else {
                    "${porAvisar.size} supervisores nuevos se registraron con tu enlace"
                }
                Toast.makeText(app, texto, Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Muestra una notificación del sistema por cada registro nuevo. false si no se pudo. */
    private fun notificar(app: Context, lista: List<RegistroInvitado>): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return false

        val manager = NotificationManagerCompat.from(app)
        if (!manager.areNotificationsEnabled()) return false

        if (Build.VERSION.SDK_INT >= 26) {
            val canal = NotificationChannel(
                CANAL,
                "Invitaciones de supervisores",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Avisa cuando un supervisor se registra con tu enlace" }
            app.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
        }

        val abrirApp = PendingIntent.getActivity(
            app,
            0,
            Intent(app, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        for (registro in lista.take(5)) {
            val detalle = "${registro.alias}\n${registro.email}\n" +
                    "Se registró el ${formatoFechaHora(registro.registradoEn)}"
            val notificacion = NotificationCompat.Builder(app, CANAL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Nuevo supervisor registrado")
                .setContentText("${registro.alias} · ${registro.email}")
                .setStyle(NotificationCompat.BigTextStyle().bigText(detalle))
                .setContentIntent(abrirApp)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            try {
                manager.notify(registro.id.hashCode(), notificacion)
            } catch (_: SecurityException) {
                return false
            }
        }
        return true
    }
}