package com.solventa.mobile.bloqueo

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.solventa.mobile.setContentEnEspanol
import com.solventa.mobile.ui.tema.SolventaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Bloqueo biométrico (B2 del diseño, HU05) con estados fijos. El diálogo del sistema no se automatiza. */
class BloqueoScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun mostrar(
        estado: BloqueoUiState = BloqueoUiState(),
        nombre: String? = "Ana",
        acciones: BloqueoAcciones = BloqueoAcciones(),
    ) {
        composeTestRule.setContentEnEspanol { SolventaTheme { BloqueoScreen(estado, nombre, acciones) } }
    }

    @Test
    fun saluda_por_el_nombre_y_pide_confirmar_la_identidad() {
        mostrar()

        composeTestRule.onNode(isHeading() and hasText("Hola de nuevo, Ana")).assertIsDisplayed()
        composeTestRule.onNodeWithText("Confirma que eres tú para entrar.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Solventa").assertIsDisplayed()
    }

    @Test
    fun sin_nombre_saluda_sin_inventarlo() {
        mostrar(nombre = null)

        composeTestRule.onNodeWithText("Hola de nuevo").assertIsDisplayed()
    }

    @Test
    fun el_boton_de_biometria_vuelve_a_pedirla() {
        var pedida = 0
        mostrar(acciones = BloqueoAcciones(usarHuella = { pedida++ }))

        composeTestRule.onNodeWithContentDescription("Entrar con huella o rostro").assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(1, pedida)
    }

    @Test
    fun usar_contrasena_esta_siempre_a_mano() {
        var elegida = 0
        mostrar(acciones = BloqueoAcciones(usarContrasena = { elegida++ }))

        composeTestRule.onNodeWithText("Usar contraseña").assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(1, elegida)
    }

    @Test
    fun tras_cancelar_se_explica_y_se_puede_reintentar() {
        mostrar(BloqueoUiState(cancelado = true))

        composeTestRule.onNodeWithText("Cancelaste la confirmación. Toca el botón para intentarlo de nuevo o usa tu contraseña.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Usar contraseña").assertIsDisplayed()
    }

    @Test
    fun tras_un_fallo_dice_cuantos_intentos_quedan() {
        mostrar(BloqueoUiState(fallidos = 1))

        composeTestRule.onNodeWithText("No te reconocimos. Te quedan 2 intentos antes de pedirte la contraseña.").assertIsDisplayed()
    }

    @Test
    fun con_un_solo_intento_restante_usa_el_singular() {
        mostrar(BloqueoUiState(fallidos = 2))

        composeTestRule.onNodeWithText("No te reconocimos. Te queda 1 intento antes de pedirte la contraseña.").assertIsDisplayed()
    }

    @Test
    fun mientras_el_sistema_pide_la_biometria_no_se_puede_tocar_nada() {
        mostrar(BloqueoUiState(fase = FaseBloqueo.AUTENTICANDO))

        composeTestRule.onNodeWithContentDescription("Entrar con huella o rostro").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Usar contraseña").assertIsNotEnabled()
    }
}
