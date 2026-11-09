package com.example.krono_corp.reportes

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import java.io.File

/**
 * Archivo que se bajó de Appwrite Storage.
 *  - Android 10+  : se guarda en Descargas/KronoCorp y se usa su Uri (no necesita FileProvider).
 *  - Android 9-   : se guarda en la caché de la app y se abre con abrirArchivo()/compartirArchivo().
 */
class ArchivoDescargado(
    val nombre: String,
    val mime: String,
    val uri: Uri?,
    val archivo: File?
)

fun mimeDe(nombre: String): String = when {
    nombre.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
    nombre.endsWith(".xlsx", ignoreCase = true) ->
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    nombre.endsWith(".xls", ignoreCase = true) -> "application/vnd.ms-excel"
    else -> "application/octet-stream"
}

/** Escribe el archivo en disco. Llamar desde un hilo de IO (Dispatchers.IO). */
fun guardarDescarga(context: Context, nombre: String, bytes: ByteArray): ArchivoDescargado {
    val limpio = nombre.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    val mime = mimeDe(limpio)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val valores = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, limpio)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/KronoCorp")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valores)
            ?: throw IllegalStateException("No se pudo crear el archivo en Descargas.")
        val salida = resolver.openOutputStream(uri)
            ?: throw IllegalStateException("No se pudo escribir el archivo en Descargas.")
        salida.use { it.write(bytes) }
        valores.clear()
        valores.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, valores, null, null)
        return ArchivoDescargado(limpio, mime, uri, null)
    }

    val carpeta = File(context.cacheDir, "reportes").apply { mkdirs() }
    val archivo = File(carpeta, limpio)
    archivo.writeBytes(bytes)
    return ArchivoDescargado(limpio, mime, null, archivo)
}

/** Abre el archivo con la app del teléfono (visor de PDF, Excel, etc.). Llamar en el hilo principal. */
fun abrirDescarga(context: Context, descarga: ArchivoDescargado) {
    val uri = descarga.uri
    if (uri == null) {
        descarga.archivo?.let { abrirArchivo(context, it) }
        return
    }
    val intento = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, descarga.mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intento)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(
            context,
            "No hay una app para abrir este archivo. Quedó guardado en Descargas/KronoCorp.",
            Toast.LENGTH_LONG
        ).show()
    }
}

/** Abre el menú de compartir de Android con el archivo. Llamar en el hilo principal. */
fun compartirDescarga(context: Context, descarga: ArchivoDescargado) {
    val uri = descarga.uri
    if (uri == null) {
        descarga.archivo?.let { compartirArchivo(context, it) }
        return
    }
    val envio = Intent(Intent.ACTION_SEND).apply {
        type = descarga.mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val selector = Intent.createChooser(envio, "Compartir archivo").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(selector)
}