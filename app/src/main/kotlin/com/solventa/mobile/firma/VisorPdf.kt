package com.solventa.mobile.firma

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.solventa.mobile.R
import com.solventa.mobile.ui.componentes.BarraSuperior
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lector del PDF de la póliza dentro de la app (HU21, criterio 1: leer el documento completo antes de firmar). Usa el
 * `PdfRenderer` del sistema: no hace falta otra aplicación ni compartir el archivo, que lleva datos personales.
 */
@Composable
internal fun VisorPdf(contenido: ContenidoPdf, onCerrar: () -> Unit) {
    val contexto = LocalContext.current
    val anchoPx = contexto.resources.displayMetrics.widthPixels
    val paginas by produceState<List<Bitmap>?>(initialValue = null, contenido) {
        value = withContext(Dispatchers.IO) { renderizar(contexto.cacheDir, contenido.bytes, anchoPx) }
    }
    // Se libera cada lista al dejar de usarse. La lista se captura aquí: dentro de `onDispose`, `paginas` (un delegado
    // de estado) devolvería la lista *actual* y no la que este efecto vio, y reciclaría las bitmaps que se están dibujando.
    val aLiberar = paginas
    DisposableEffect(aLiberar) { onDispose { aLiberar?.forEach { if (!it.isRecycled) it.recycle() } } }

    Column(Modifier.fillMaxSize()) {
        BarraSuperior(titulo = stringResource(R.string.firma_lector_titulo), onAtras = onCerrar)
        val lista = paginas
        when {
            lista == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            lista.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.firma_lector_error), style = MaterialTheme.typography.bodyLarge)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(lista) { indice, pagina ->
                    Image(
                        bitmap = pagina.asImageBitmap(),
                        contentDescription = stringResource(R.string.firma_lector_pagina, indice + 1, lista.size),
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * Dibuja cada página al ancho de la pantalla. El PDF se escribe en la caché solo el tiempo que dura el dibujo y se
 * borra enseguida; una lista vacía significa que el archivo no se pudo leer.
 */
internal fun renderizar(cache: File, bytes: ByteArray, anchoPx: Int): List<Bitmap> {
    val archivo = File.createTempFile("poliza", ".pdf", cache)
    return try {
        archivo.writeBytes(bytes)
        ParcelFileDescriptor.open(archivo, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                List(renderer.pageCount) { indice ->
                    renderer.openPage(indice).use { pagina ->
                        val alto = (anchoPx.toFloat() * pagina.height / pagina.width).toInt().coerceAtLeast(1)
                        Bitmap.createBitmap(anchoPx, alto, Bitmap.Config.ARGB_8888).also {
                            it.eraseColor(Color.WHITE) // el renderizador no pinta el fondo
                            pagina.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        }
    } catch (_: Exception) {
        emptyList()
    } finally {
        archivo.delete()
    }
}
