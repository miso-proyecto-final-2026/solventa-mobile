package com.solventa.mobile.ui

import androidx.compose.ui.graphics.Color
import com.solventa.mobile.ui.tema.ColoresExtra
import com.solventa.mobile.ui.tema.SolventaColorScheme as C
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/** El diseño exige 4.5:1 en todo el texto. Cambiar un color sin revisarlo rompe esta prueba. */
class ContrasteTest {

    private fun luminancia(c: Color): Double {
        fun lineal(v: Float) = v.toDouble().let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * lineal(c.red) + 0.7152 * lineal(c.green) + 0.0722 * lineal(c.blue)
    }

    private fun contraste(a: Color, b: Color): Double {
        val (claro, oscuro) = listOf(luminancia(a), luminancia(b)).sortedDescending()
        return (claro + 0.05) / (oscuro + 0.05)
    }

    private fun verificar(nombre: String, texto: Color, fondo: Color, minimo: Double = 4.5) {
        val razon = contraste(texto, fondo)
        assertTrue("$nombre: contraste %.2f < %.1f".format(razon, minimo), razon >= minimo)
    }

    @Test
    fun `el texto cumple 4_5 a 1 sobre su fondo`() {
        val extra = ColoresExtra()
        verificar("onPrimary/primary", C.onPrimary, C.primary)
        verificar("onPrimaryContainer/primaryContainer", C.onPrimaryContainer, C.primaryContainer)
        verificar("onSecondaryContainer/secondaryContainer", C.onSecondaryContainer, C.secondaryContainer)
        verificar("onSurface/surface", C.onSurface, C.surface)
        verificar("onSurface/surfaceContainer", C.onSurface, C.surfaceContainer)
        verificar("onSurfaceVariant/surface", C.onSurfaceVariant, C.surface)
        verificar("onSurfaceVariant/surfaceContainer", C.onSurfaceVariant, C.surfaceContainer)
        verificar("primary/surface (enlaces y botones de texto)", C.primary, C.surface)
        verificar("primary/surfaceContainer", C.primary, C.surfaceContainer)
        verificar("error/surface (acción destructiva)", C.error, C.surface)
        verificar("onError/error", C.onError, C.error)
        verificar("onErrorContainer/errorContainer", C.onErrorContainer, C.errorContainer)
        verificar("onTertiaryContainer/tertiaryContainer (éxito)", C.onTertiaryContainer, C.tertiaryContainer)
        verificar("onAdvertencia/advertencia", extra.onAdvertencia, extra.advertenciaContenedor)
        verificar("onSurfaceVariant/chipInactivo", C.onSurfaceVariant, extra.chipInactivo)
    }

    @Test
    fun `el borde de los campos distingue del fondo al menos 3 a 1`() {
        verificar("outline/surface", C.outline, C.surface, minimo = 3.0)
    }
}
