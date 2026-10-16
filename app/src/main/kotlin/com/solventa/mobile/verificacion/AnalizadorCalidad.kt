package com.solventa.mobile.verificacion

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Revisa una captura antes de aceptarla. `null` significa que es aceptable. */
fun interface AnalizadorCalidad {
    suspend fun analizar(ruta: String): ProblemaImagen?
}

class AnalizadorCalidadAndroid(
    private val umbrales: UmbralesCalidad = UmbralesCalidad(),
    private val ladoMaximo: Int = 640,
) : AnalizadorCalidad {

    override suspend fun analizar(ruta: String): ProblemaImagen? = withContext(Dispatchers.Default) {
        val bitmap = decodificarReducido(ruta) ?: return@withContext ProblemaImagen.ILEGIBLE
        try {
            val pixeles = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixeles, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val gris = IntArray(pixeles.size) { luminancia(pixeles[it]) }
            CalidadImagen.evaluar(gris, bitmap.width, bitmap.height, umbrales)
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodificarReducido(ruta: String): Bitmap? {
        val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(ruta, medidas)
        if (medidas.outWidth <= 0 || medidas.outHeight <= 0) return null
        var muestreo = 1
        while (max(medidas.outWidth, medidas.outHeight) / (muestreo * 2) >= ladoMaximo) muestreo *= 2
        val opciones = BitmapFactory.Options().apply { inSampleSize = muestreo }
        return BitmapFactory.decodeFile(ruta, opciones)
    }

    private fun luminancia(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (0.299 * r + 0.587 * g + 0.114 * b).toInt()
    }
}
