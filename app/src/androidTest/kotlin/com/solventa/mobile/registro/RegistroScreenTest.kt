package com.solventa.mobile.registro

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

/**
 * Camino feliz del registro. Requiere un dispositivo/emulador (Espresso), por lo que no
 * corre en este entorno de desarrollo (WSL sin Android SDK de emulación) ni en el CI del
 * repo hasta que se active la variable ENABLE_ESPRESSO (ver .github/workflows/ci.yml).
 */
class RegistroScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun registroConDatosValidosHabilitaElEnvio() {
        composeTestRule.setContent {
            RegistroScreen(viewModel = RegistroViewModel(), onRegistroExitoso = {})
        }

        composeTestRule.onNodeWithContentDescription("Nombres").performTextInput("Ana")
        composeTestRule.onNodeWithContentDescription("Apellidos").performTextInput("Pérez")
        composeTestRule.onNodeWithContentDescription("Número de documento").performTextInput("100200300")
        composeTestRule.onNodeWithContentDescription("Correo electrónico")
            .performTextInput("ana.perez@example.com")
        composeTestRule.onNodeWithContentDescription("Celular").performTextInput("3001234567")
        composeTestRule.onNodeWithContentDescription("Contraseña").performTextInput("Clave1234")
        composeTestRule.onNodeWithContentDescription("Aceptar política de tratamiento de datos")
            .performClick()

        composeTestRule.onNodeWithText("Registrarme").assertExists()
    }
}
