package com.solventa.mobile.verificacion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalidadImagenTest {

    private val ancho = 32
    private val alto = 32

    private fun uniforme(valor: Int) = IntArray(ancho * alto) { valor }

    /** Tablero de ajedrez: máximo contraste entre vecinos, como un texto bien enfocado. */
    private fun nitida(claro: Int = 200, oscuro: Int = 60) =
        IntArray(ancho * alto) { if ((it % ancho + it / ancho) % 2 == 0) claro else oscuro }

    @Test
    fun `una imagen nitida y bien iluminada es aceptable`() {
        assertNull(CalidadImagen.evaluar(nitida(), ancho, alto))
    }

    @Test
    fun `una imagen oscura se rechaza`() {
        assertEquals(ProblemaImagen.OSCURA, CalidadImagen.evaluar(nitida(claro = 40, oscuro = 5), ancho, alto))
    }

    @Test
    fun `una imagen quemada por la luz se rechaza`() {
        assertEquals(
            ProblemaImagen.SOBREEXPUESTA,
            CalidadImagen.evaluar(nitida(claro = 255, oscuro = 230), ancho, alto),
        )
    }

    @Test
    fun `una imagen sin detalle por el desenfoque se rechaza`() {
        assertEquals(ProblemaImagen.BORROSA, CalidadImagen.evaluar(uniforme(128), ancho, alto))
    }

    @Test
    fun `un degradado suave tambien cuenta como borroso`() {
        val degradado = IntArray(ancho * alto) { 100 + (it % ancho) }

        assertEquals(ProblemaImagen.BORROSA, CalidadImagen.evaluar(degradado, ancho, alto))
    }

    @Test
    fun `la nitidez crece con el contraste`() {
        val baja = CalidadImagen.nitidez(nitida(claro = 130, oscuro = 120), ancho, alto)
        val alta = CalidadImagen.nitidez(nitida(claro = 220, oscuro = 40), ancho, alto)

        assertTrue(alta > baja)
    }

    @Test
    fun `los umbrales son configurables`() {
        val estricto = UmbralesCalidad(nitidezMinima = 1_000_000.0)

        assertEquals(ProblemaImagen.BORROSA, CalidadImagen.evaluar(nitida(), ancho, alto, estricto))
        assertNull(CalidadImagen.evaluar(nitida(), ancho, alto, UmbralesCalidad(nitidezMinima = 1.0)))
    }

    @Test
    fun `medidas inconsistentes o demasiado pequenas son ilegibles`() {
        assertEquals(ProblemaImagen.ILEGIBLE, CalidadImagen.evaluar(IntArray(10), ancho, alto))
        assertEquals(ProblemaImagen.ILEGIBLE, CalidadImagen.evaluar(IntArray(4), 2, 2))
    }
}
