package com.solventa.mobile.ui

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontListFontFamily
import com.solventa.mobile.ui.tema.Roboto
import com.solventa.mobile.ui.tema.SolventaTipografia
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** El diseño pide Roboto siempre: va empaquetada y todos los estilos la usan, no la fuente del sistema. */
class TipografiaTest {

    private val estilos = with(SolventaTipografia) {
        listOf(headlineMedium, headlineSmall, titleLarge, titleMedium, titleSmall, bodyLarge, bodyMedium, bodySmall, labelLarge, labelMedium, labelSmall)
    }

    @Test
    fun `todos los estilos usan Roboto empaquetada y no la fuente del sistema`() {
        estilos.forEach {
            assertEquals(Roboto, it.fontFamily)
            assertNotEquals(FontFamily.Default, it.fontFamily)
        }
    }

    @Test
    fun `Roboto trae los pesos 400, 500 y 700 del diseno`() {
        val pesos = (Roboto as FontListFontFamily).fonts.map { it.weight }

        assertEquals(listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.Bold), pesos)
    }

    @Test
    fun `los archivos de la fuente existen y son TrueType`() {
        listOf("regular", "medium", "bold").forEach {
            val archivo = File("src/main/res/font/roboto_$it.ttf")
            assertTrue("Falta ${archivo.path}", archivo.exists())
            // Cabecera TrueType: 0x00010000.
            assertEquals(listOf(0, 1, 0, 0), archivo.readBytes().take(4).map { b -> b.toInt() })
        }
    }

    @Test
    fun `la escala del diseno se conserva`() {
        assertEquals(28f, SolventaTipografia.headlineMedium.fontSize.value, 0f)
        assertEquals(36f, SolventaTipografia.headlineMedium.lineHeight.value, 0f)
        assertEquals(22f, SolventaTipografia.titleLarge.fontSize.value, 0f)
        assertEquals(12f, SolventaTipografia.labelMedium.fontSize.value, 0f)
        assertEquals(FontWeight.Medium, SolventaTipografia.labelMedium.fontWeight)
    }
}
