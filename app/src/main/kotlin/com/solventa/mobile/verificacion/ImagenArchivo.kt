package com.solventa.mobile.verificacion

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.max

/** Carga una captura reducida y orientada para mostrarla (las fotos de la cámara traen EXIF). */
object ImagenArchivo {

    fun cargar(ruta: String, ladoMaximo: Int = 1024): ImageBitmap? {
        val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(ruta, medidas)
        if (medidas.outWidth <= 0 || medidas.outHeight <= 0) return null
        var muestreo = 1
        while (max(medidas.outWidth, medidas.outHeight) / (muestreo * 2) >= ladoMaximo) muestreo *= 2
        val bitmap = BitmapFactory.decodeFile(ruta, BitmapFactory.Options().apply { inSampleSize = muestreo })
            ?: return null
        return orientar(bitmap, grados(ruta)).asImageBitmap()
    }

    private fun grados(ruta: String): Int =
        when (
            runCatching {
                ExifInterface(ruta).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

    private fun orientar(bitmap: Bitmap, grados: Int): Bitmap {
        if (grados == 0) return bitmap
        val matriz = Matrix().apply { postRotate(grados.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matriz, true)
    }
}
