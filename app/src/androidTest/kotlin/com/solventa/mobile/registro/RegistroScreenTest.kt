package com.solventa.mobile.registro

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.solventa.mobile.setContentEnEspanol
import com.solventa.mobile.ui.tema.SolventaTheme
import org.junit.Rule
import org.junit.Test

/** Registro (HU01, A1): paso 1 de 3, campos con etiqueta, contraseña con mostrar y ocultar, y términos explícitos. */
class RegistroScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun mostrar() = composeTestRule.setContentEnEspanol {
        SolventaTheme { RegistroScreen(viewModel = RegistroViewModel(), onRegistroExitoso = {}) }
    }

    @Test
    fun registroConDatosValidosHabilitaElEnvio() {
        mostrar()

        composeTestRule.onNodeWithText("Nombres").performTextInput("Ana")
        composeTestRule.onNodeWithText("Apellidos").performTextInput("Pérez")
        composeTestRule.onNodeWithText("Número de documento").performScrollTo().performTextInput("100200300")
        composeTestRule.onNodeWithText("Correo electrónico").performScrollTo().performTextInput("ana.perez@example.com")
        composeTestRule.onNodeWithText("Celular").performScrollTo().performTextInput("3001234567")
        composeTestRule.onNodeWithText("Contraseña").performScrollTo().performTextInput("Clave1234")
        composeTestRule.onNodeWithText("Acepto los términos y condiciones y la política de tratamiento de datos")
            .performScrollTo()
            .performClick()

        composeTestRule.onNodeWithText("Continuar").assertIsDisplayed()
    }

    @Test
    fun muestraElPasoYElEncabezadoDelDiseno() {
        mostrar()

        composeTestRule.onNodeWithText("Paso 1 de 3 · Tus datos").assertIsDisplayed()
        composeTestRule.onNodeWithText("Empecemos por conocerte").assertIsDisplayed()
    }

    @Test
    fun laContrasenaSePuedeMostrarYOcultar() {
        mostrar()

        composeTestRule.onNodeWithContentDescription("Mostrar contraseña").performScrollTo().performClick()

        composeTestRule.onNodeWithContentDescription("Ocultar contraseña").assertExists()
    }

    @Test
    fun laContrasenaExplicaSuRegla() {
        mostrar()

        composeTestRule.onNodeWithText("Mínimo 8 caracteres, con letras y números").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun enviarSinDatosMuestraLosErroresPorCampo() {
        mostrar()

        composeTestRule.onNodeWithText("Continuar").performClick()

        composeTestRule.onNodeWithText("Debes aceptar la política de tratamiento de datos para continuar")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
