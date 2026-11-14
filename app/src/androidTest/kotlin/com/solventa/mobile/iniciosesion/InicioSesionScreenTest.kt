package com.solventa.mobile.iniciosesion

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.solventa.mobile.R
import com.solventa.mobile.setContentEnEspanol
import com.solventa.mobile.ui.tema.SolventaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Inicio de sesión con correo y contraseña (HU05) con estados fijos. */
class InicioSesionScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun mostrar(
        estado: InicioSesionUiState = InicioSesionUiState(),
        acciones: InicioSesionAcciones = InicioSesionAcciones(),
        aviso: Int? = null,
    ) {
        composeTestRule.setContentEnEspanol { SolventaTheme { InicioSesionScreen(estado, acciones, aviso) } }
    }

    @Test
    fun muestra_el_encabezado_los_campos_y_las_acciones() {
        mostrar()

        composeTestRule.onNode(isHeading() and hasText("Qué bueno verte de nuevo")).assertIsDisplayed()
        composeTestRule.onNodeWithText("Correo electrónico").assertIsDisplayed()
        composeTestRule.onNodeWithText("Contraseña").assertIsDisplayed()
        // El título de la barra y el botón principal.
        composeTestRule.onAllNodesWithText("Iniciar sesión").assertCountEquals(2)
        composeTestRule.onNodeWithText("Crear una cuenta").assertHeightIsAtLeast(48.dp).assertIsDisplayed()
    }

    @Test
    fun el_correo_guardado_llega_prellenado() {
        mostrar(InicioSesionUiState(email = "ana@example.com"))

        composeTestRule.onNodeWithText("ana@example.com").assertIsDisplayed()
    }

    @Test
    fun escribir_en_los_campos_avisa_al_flujo() {
        var correo = ""
        var clave = ""
        // Con un estado que cambia, como el del ViewModel real: un valor fijo no refleja lo que se escribe.
        composeTestRule.setContentEnEspanol {
            SolventaTheme {
                var estado by remember { mutableStateOf(InicioSesionUiState()) }
                InicioSesionScreen(
                    estado,
                    InicioSesionAcciones(
                        onEmailChange = { correo = it; estado = estado.copy(email = it) },
                        onPasswordChange = { clave = it; estado = estado.copy(password = it) },
                    ),
                )
            }
        }

        composeTestRule.onNodeWithText("Correo electrónico").performTextInput("ana@example.com")
        composeTestRule.onNodeWithText("Contraseña").performTextInput("Clave1234")

        composeTestRule.waitForIdle()
        assertEquals("ana@example.com", correo)
        assertEquals("Clave1234", clave)
    }

    @Test
    fun la_contrasena_se_oculta_y_se_puede_mostrar() {
        mostrar(InicioSesionUiState(password = "Clave1234"))

        composeTestRule.onNodeWithText("Clave1234").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Mostrar contraseña").assertExists()
    }

    @Test
    fun el_boton_principal_envia_y_crear_cuenta_vuelve_al_registro() {
        var enviado = 0
        var registro = 0
        mostrar(
            InicioSesionUiState(email = "ana@example.com", password = "x"),
            InicioSesionAcciones(enviar = { enviado++ }, crearCuenta = { registro++ }),
        )

        composeTestRule.onAllNodes(hasText("Iniciar sesión") and hasClickAction())[0].performClick()
        composeTestRule.onNodeWithText("Crear una cuenta").performClick()

        assertEquals(1, enviado)
        assertEquals(1, registro)
    }

    @Test
    fun credenciales_incorrectas_no_dicen_cual_falla() {
        mostrar(InicioSesionUiState(error = ErrorInicioSesion.CREDENCIALES))

        composeTestRule.onNodeWithText("Correo o contraseña incorrectos.").assertIsDisplayed()
    }

    @Test
    fun el_bloqueo_dice_cuantos_minutos_faltan() {
        mostrar(InicioSesionUiState(error = ErrorInicioSesion.BLOQUEADO, minutosBloqueo = 15))

        composeTestRule.onNodeWithText("Demasiados intentos. Intenta de nuevo en 15 minutos.").assertIsDisplayed()
    }

    @Test
    fun con_un_minuto_usa_el_singular() {
        mostrar(InicioSesionUiState(error = ErrorInicioSesion.BLOQUEADO, minutosBloqueo = 1))

        composeTestRule.onNodeWithText("Demasiados intentos. Intenta de nuevo en 1 minuto.").assertIsDisplayed()
    }

    @Test
    fun cada_falla_de_red_tiene_su_mensaje() {
        mostrar(InicioSesionUiState(error = ErrorInicioSesion.SIN_CONEXION))

        composeTestRule.onNodeWithText("No hay conexión. Verifica tu red e intenta de nuevo.").assertIsDisplayed()
    }

    @Test
    fun tras_la_biometria_fallida_se_explica_por_que_se_pide_la_contrasena() {
        mostrar(aviso = R.string.iniciosesion_aviso_tres_fallos)

        composeTestRule.onNodeWithText("No pudimos reconocer tu biometría tres veces. Por seguridad, entra con tu contraseña.")
            .assertIsDisplayed()
    }

    @Test
    fun mientras_carga_muestra_el_progreso() {
        mostrar(InicioSesionUiState(cargando = true))

        composeTestRule.onNodeWithText("Iniciando sesión…").assertIsDisplayed()
    }
}
