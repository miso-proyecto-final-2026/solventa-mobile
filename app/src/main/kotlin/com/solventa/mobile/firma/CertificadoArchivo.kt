package com.solventa.mobile.firma

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Escribe el PDF en el archivo que el cliente eligió con el selector del sistema (no hace falta ningún permiso). */
suspend fun guardarEnArchivo(contexto: Context, destino: Uri, contenido: ByteArray): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            contexto.contentResolver.openOutputStream(destino, "wt")?.use { it.write(contenido) } != null
        }.getOrDefault(false)
    }

/** Abre el PDF guardado en el visor que el cliente tenga. Sin visor instalado no pasa nada. */
fun abrirPdf(contexto: Context, archivo: Uri) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(archivo, "application/pdf")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { contexto.startActivity(intent) }
}

/**
 * Pide permiso persistente de lectura sobre el archivo que el cliente eligió, para abrirlo después sin conexión (HU36).
 * `false` si el proveedor de documentos no lo concede.
 */
fun conservarAcceso(contexto: Context, archivo: Uri): Boolean =
    runCatching { contexto.contentResolver.takePersistableUriPermission(archivo, Intent.FLAG_GRANT_READ_URI_PERMISSION) }.isSuccess

/** El archivo sigue existiendo y se puede leer: el cliente pudo haberlo movido o borrado. */
fun documentoDisponible(contexto: Context, archivo: Uri): Boolean =
    runCatching { contexto.contentResolver.openFileDescriptor(archivo, "r")?.use { true } ?: false }.getOrDefault(false)
