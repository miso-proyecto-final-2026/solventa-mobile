package com.solventa.mobile.verificacion

/** Por qué se rechaza una captura antes de enviarla (criterio 2 de HU02). */
enum class ProblemaImagen { OSCURA, SOBREEXPUESTA, BORROSA, ILEGIBLE }

/** Umbrales configurables: el valor correcto depende del proveedor real y de la cámara. */
data class UmbralesCalidad(
    val brilloMinimo: Double = 45.0,
    val brilloMaximo: Double = 225.0,
    val nitidezMinima: Double = 60.0,
)

/**
 * Chequeo local de calidad sobre una imagen en escala de grises (0..255, fila a fila).
 * Es puro (sin Android) para poder probarlo con imágenes sintéticas.
 */
object CalidadImagen {

    /** Devuelve el problema encontrado o `null` si la imagen es aceptable. */
    fun evaluar(
        gris: IntArray,
        ancho: Int,
        alto: Int,
        umbrales: UmbralesCalidad = UmbralesCalidad(),
    ): ProblemaImagen? {
        if (ancho < 3 || alto < 3 || gris.size != ancho * alto) return ProblemaImagen.ILEGIBLE
        val brillo = gris.average()
        return when {
            brillo < umbrales.brilloMinimo -> ProblemaImagen.OSCURA
            brillo > umbrales.brilloMaximo -> ProblemaImagen.SOBREEXPUESTA
            nitidez(gris, ancho, alto) < umbrales.nitidezMinima -> ProblemaImagen.BORROSA
            else -> null
        }
    }

    /** Varianza del laplaciano de 4 vecinos: baja en imágenes borrosas o sin detalle. */
    fun nitidez(gris: IntArray, ancho: Int, alto: Int): Double {
        var suma = 0.0
        var sumaCuadrados = 0.0
        var n = 0
        for (y in 1 until alto - 1) {
            for (x in 1 until ancho - 1) {
                val i = y * ancho + x
                val laplaciano = 4 * gris[i] - gris[i - 1] - gris[i + 1] - gris[i - ancho] - gris[i + ancho]
                suma += laplaciano
                sumaCuadrados += laplaciano.toDouble() * laplaciano
                n++
            }
        }
        val media = suma / n
        return sumaCuadrados / n - media * media
    }
}
