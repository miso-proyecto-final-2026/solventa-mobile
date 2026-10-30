package com.solventa.mobile.firma

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.solventa.mobile.R
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * El lector dibuja las páginas del PDF de verdad. Una versión anterior reciclaba las bitmaps en cuanto el estado
 * pasaba de «cargando» a la lista, y la app se cerraba al intentar dibujarlas: esta prueba lo habría atrapado.
 */
class VisorPdfTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun pdfDePaginas(paginas: Int): ByteArray {
        val documento = PdfDocument()
        repeat(paginas) { indice ->
            val pagina = documento.startPage(PdfDocument.PageInfo.Builder(595, 842, indice + 1).create())
            pagina.canvas.drawText("Póliza de prueba, página ${indice + 1}", 40f, 80f, Paint().apply { textSize = 18f })
            documento.finishPage(pagina)
        }
        return ByteArrayOutputStream().also { documento.writeTo(it) }.toByteArray().also { documento.close() }
    }

    private fun texto(id: Int, vararg argumentos: Any) = composeTestRule.activity.getString(id, *argumentos)

    @Test
    fun dibujaTodasLasPaginasSinCerrarLaApp() {
        composeTestRule.setContent { MaterialTheme { VisorPdf(ContenidoPdf(pdfDePaginas(2)), onCerrar = {}) } }

        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithContentDescription(texto(R.string.firma_lector_pagina, 1, 2)).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription(texto(R.string.firma_lector_pagina, 1, 2)).assertIsDisplayed()
        composeTestRule.waitForIdle() // un dibujo más: con la bitmap reciclada aquí se rompía
    }

    @Test
    fun unArchivoQueNoEsPdfMuestraElErrorEnVezDeCerrarse() {
        composeTestRule.setContent { MaterialTheme { VisorPdf(ContenidoPdf("no es un pdf".toByteArray()), onCerrar = {}) } }

        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText(texto(R.string.firma_lector_error)).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(texto(R.string.firma_lector_error)).assertIsDisplayed()
    }

    @Test
    fun volverCierraElLector() {
        var cerrado = 0
        composeTestRule.setContent { MaterialTheme { VisorPdf(ContenidoPdf(pdfDePaginas(1)), onCerrar = { cerrado++ }) } }

        composeTestRule.onNodeWithContentDescription(texto(R.string.ui_volver)).performClick()

        assertEquals(1, cerrado)
    }
}
