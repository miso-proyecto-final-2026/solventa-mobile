package com.solventa.mobile.consentimiento

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.solventa.mobile.setContentEnEspanol
import org.junit.Rule
import org.junit.Test

/**
 * Recorridos de aceptación y de rechazo de HU06. Requieren dispositivo/emulador (Espresso), así
 * que no corren en WSL ni en el CI hasta activar ENABLE_ESPRESSO (ver .github/workflows/ci.yml).
 */
class ConsentimientoScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun mostrar() = composeTestRule.setContentEnEspanol {
        ConsentimientoScreen(viewModel = ConsentimientoViewModel(), onContinuar = {})
    }

    @Test
    fun muestraLaInformacionPreviaYNingunaAceptacionImplicita() {
        mostrar()

        composeTestRule.onNodeWithText("Para qué los usamos").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Tu derecho a revocar").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(
            "Aceptar el uso de mis datos de Open Finance y Open Data",
        ).assertIsEnabled()
        composeTestRule.onNodeWithText("No por ahora").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun elDetalleLegalSeExpandeYSeOculta() {
        mostrar()

        composeTestRule.onNodeWithText("Ver texto legal completo").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Ocultar texto legal").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun rechazarMuestraLaConfirmacionSinCompartirDatos() {
        mostrar()

        composeTestRule.onNodeWithText("No por ahora").performScrollTo().performClick()

        composeTestRule.onNodeWithText("Continuarás sin compartir datos").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Continuar").performScrollTo().assertIsDisplayed()
    }
}
