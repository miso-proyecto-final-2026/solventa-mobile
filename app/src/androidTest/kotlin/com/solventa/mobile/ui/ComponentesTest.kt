package com.solventa.mobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.solventa.mobile.setContentEnEspanol
import com.solventa.mobile.ui.componentes.BarraSuperior
import com.solventa.mobile.ui.componentes.BotonDestructivo
import com.solventa.mobile.ui.componentes.BotonPrimario
import com.solventa.mobile.ui.componentes.BotonSecundario
import com.solventa.mobile.ui.componentes.CampoTexto
import com.solventa.mobile.ui.componentes.ChipEstado
import com.solventa.mobile.ui.componentes.IndicadorPasos
import com.solventa.mobile.ui.componentes.TipoEstado
import com.solventa.mobile.ui.tema.SolventaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Los componentes del diseño cumplen las medidas y la accesibilidad que pide el handoff (48 dp, etiquetas, pasos). */
class ComponentesTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun mostrar(contenido: @androidx.compose.runtime.Composable () -> Unit) =
        composeTestRule.setContentEnEspanol { SolventaTheme { contenido() } }

    @Test
    fun losBotonesMidenAlMenos48dpYRespondenAlToque() {
        var toques = 0
        mostrar {
            BotonPrimario("Continuar", onClick = { toques++ })
            BotonSecundario("Ahora no", onClick = { toques += 10 })
        }

        composeTestRule.onNodeWithText("Continuar").assertHeightIsAtLeast(48.dp).assertHasClickAction().performClick()
        composeTestRule.onNodeWithText("Ahora no").assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(11, toques)
    }

    @Test
    fun unBotonDeshabilitadoNoRespondeYSeNota() {
        mostrar { BotonPrimario("Continuar", onClick = {}, habilitado = false) }

        composeTestRule.onNodeWithText("Continuar").assertIsNotEnabled()
    }

    @Test
    fun elBotonDestructivoSeMuestra() {
        mostrar { BotonDestructivo("Revocar", onClick = {}) }

        composeTestRule.onNodeWithText("Revocar").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun elCampoMuestraSuEtiquetaYRecibeTexto() {
        val valor = mutableStateOf("")
        mostrar { CampoTexto(valor = valor.value, onValorChange = { valor.value = it }, etiqueta = "Correo electrónico") }

        composeTestRule.onNodeWithText("Correo electrónico").performTextInput("ana@correo.com")

        assertEquals("ana@correo.com", valor.value)
    }

    @Test
    fun elCampoConErrorMuestraElMensajeEnLugarDeLaAyuda() {
        mostrar { CampoTexto("x", {}, etiqueta = "Contraseña", ayuda = "Mínimo 8 caracteres", error = "Muy corta") }

        composeTestRule.onNodeWithText("Muy corta").assertIsDisplayed()
        composeTestRule.onNodeWithText("Mínimo 8 caracteres").assertDoesNotExist()
    }

    @Test
    fun elIndicadorDePasosDiceEnQuePasoVa() {
        mostrar { IndicadorPasos(paso = 2, total = 3, nombre = "Pago") }

        composeTestRule.onNodeWithText("Paso 2 de 3 · Pago").assertIsDisplayed()
    }

    @Test
    fun elChipDeEstadoLlevaTextoAdemasDelColor() {
        mostrar { ChipEstado("Activo", Icons.Filled.Check, TipoEstado.ACTIVO) }

        composeTestRule.onNodeWithText("Activo").assertIsDisplayed()
    }

    @Test
    fun laBarraSuperiorTieneVolverAccesibleDe48dp() {
        var atras = false
        mostrar { BarraSuperior("Crear cuenta", onAtras = { atras = true }) }

        composeTestRule.onNodeWithText("Crear cuenta").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Volver").assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(true, atras)
    }
}
