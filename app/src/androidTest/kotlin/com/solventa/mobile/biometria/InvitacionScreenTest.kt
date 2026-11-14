package com.solventa.mobile.biometria

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.solventa.mobile.setContentEnEspanol
import com.solventa.mobile.ui.tema.SolventaTheme
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Invitación tras el KYC (B1 del diseño, HU04) con estados fijos. TalkBack se verifica a mano en un dispositivo. */
class InvitacionScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val disponible = Capacidad(Disponibilidad.DISPONIBLE, listOf(MetodoBiometrico.HUELLA, MetodoBiometrico.FACIAL))
    private var configuracionOriginal: Configuration? = null

    /** Los diálogos usan los recursos de la actividad: se fija es-CO ahí (el emulador del CI está en en-US). */
    @Suppress("DEPRECATION")
    private fun fijarEspanolEnLaActividad() {
        val recursos = composeTestRule.activity.resources
        if (configuracionOriginal == null) configuracionOriginal = Configuration(recursos.configuration)
        val espanol = Configuration(recursos.configuration).apply { setLocale(Locale.forLanguageTag("es-CO")) }
        recursos.updateConfiguration(espanol, recursos.displayMetrics)
    }

    @After
    @Suppress("DEPRECATION")
    fun restaurarIdioma() {
        val original = configuracionOriginal ?: return
        val recursos = composeTestRule.activity.resources
        recursos.updateConfiguration(original, recursos.displayMetrics)
    }

    private fun mostrar(estado: BiometriaUiState, acciones: InvitacionAcciones = InvitacionAcciones()) {
        fijarEspanolEnLaActividad()
        composeTestRule.setContentEnEspanol { SolventaTheme { InvitacionScreen(estado, acciones) } }
    }

    private fun listo(extra: BiometriaUiState.() -> BiometriaUiState = { this }) =
        BiometriaUiState(fase = FaseBiometria.ESTADO, capacidad = disponible, kycAprobado = true).extra()

    @Test
    fun muestra_la_invitacion_con_sus_dos_garantias() {
        mostrar(listo())

        composeTestRule.onNode(isHeading() and hasText("Entra más rápido y más seguro")).assertIsDisplayed()
        composeTestRule.onNodeWithText("Usa tu huella o reconocimiento facial en vez de escribir tu contraseña cada vez.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tus datos biométricos se quedan en tu teléfono; Solventa nunca los recibe.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Siempre puedes volver a usar tu contraseña desde Ajustes.").assertIsDisplayed()
    }

    @Test
    fun activar_abre_el_flujo_de_activacion() {
        var activar = 0
        mostrar(listo(), InvitacionAcciones(activar = { activar++ }))

        composeTestRule.onNodeWithText("Activar acceso biométrico").assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(1, activar)
    }

    @Test
    fun ahora_no_se_ofrece_y_no_activa_nada() {
        var ahoraNo = 0
        var activar = 0
        mostrar(listo(), InvitacionAcciones(ahoraNo = { ahoraNo++ }, activar = { activar++ }))

        composeTestRule.onNodeWithText("Ahora no").assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(1, ahoraNo)
        assertEquals(0, activar)
    }

    @Test
    fun al_activar_se_pide_la_contrasena_en_el_formulario() {
        mostrar(listo { copy(fase = FaseBiometria.FORMULARIO, metodoElegido = MetodoBiometrico.HUELLA) })

        composeTestRule.onNodeWithText("¿Con qué quieres entrar?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continuar").assertIsDisplayed()
    }

    @Test
    fun mientras_se_procesa_no_se_puede_volver_a_activar() {
        mostrar(listo { copy(fase = FaseBiometria.PROCESANDO) })

        composeTestRule.onNodeWithText("Activar acceso biométrico").assertIsNotEnabled()
    }

    @Test
    fun si_cancela_la_biometria_se_explica_y_se_puede_reintentar() {
        mostrar(listo { copy(error = ErrorAcceso.CANCELADO) })

        composeTestRule.onNodeWithText("Cancelaste la confirmación biométrica. No activamos el acceso.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Activar acceso biométrico").assertIsDisplayed()
    }
}
