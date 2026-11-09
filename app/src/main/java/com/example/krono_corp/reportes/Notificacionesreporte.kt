package com.example.krono_corp.reportes

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

/**
 * Avisa con una notificación cuando Krono termina de generar un reporte, un PDF o un Excel.
 * Al tocarla se abre el archivo (o la app, si el reporte no tiene archivo).
 *
 * En Android 13+ necesita el permiso POST_NOTIFICATIONS (MainActivity lo pide al abrir la app);
 * si el usuario no lo concede, simplemente no se muestra nada.
 */
object NotificacionesReporte {

    private const val CANAL = "reportes_krono"

    fun avisar(context: Context, titulo: String, texto: String, archivo: File? = null) {
        val app = context.applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CANAL, "Reportes", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Avisos cuando Krono genera un reporte, PDF o Excel"
                }
            )
        }

        val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val pendiente = PendingIntent.getActivity(
            app, id, intentDe(app, archivo),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notificacion = NotificationCompat.Builder(app, CANAL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setContentIntent(pendiente)
            .setAutoCancel(true)
            .build()

        try {
            manager.notify(id, notificacion)
        } catch (_: SecurityException) {
            // Sin permiso de notificaciones: no se muestra
        }
    }

    /** Abre el archivo con la app del teléfono; si no hay archivo, abre Krono. */
    private fun intentDe(app: Context, archivo: File?): Intent {
        if (archivo != null && archivo.exists()) {
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", archivo)
            return Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeDe(archivo.name))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        return (app.packageManager.getLaunchIntentForPackage(app.packageName) ?: Intent())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}