package com.example.krono_corp.reportes

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

private fun mimeDe(archivo: File) = when (archivo.extension.lowercase()) {
    "pdf" -> "application/pdf"
    "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    else -> "*/*"
}

private fun uriDe(context: Context, archivo: File) =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", archivo)

fun abrirArchivo(context: Context, archivo: File) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uriDe(context, archivo), mimeDe(archivo))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(
            context,
            "No hay una app para abrir este archivo. Usa «Compartir» para guardarlo o enviarlo.",
            Toast.LENGTH_LONG
        ).show()
    }
}

fun compartirArchivo(context: Context, archivo: File) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeDe(archivo)
        putExtra(Intent.EXTRA_STREAM, uriDe(context, archivo))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir archivo"))
}