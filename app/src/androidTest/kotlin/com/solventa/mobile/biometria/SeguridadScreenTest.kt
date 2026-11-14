package com.solventa.mobile.biometria

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.solventa.mobile.setContentEnEspanol
import com.solventa.mobile.ui.tema.SolventaTheme
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Recorrido de HU04 con estados fijos (sin red ni sensor): disponibilidad, KYC, activar, desactivar y errores
 * (criterios 1, 2, 3, 4, 7 y 9). El diálogo biométrico del sistema es de Android y no se automatiza; el ViewModel
 * lo prueba con un [Autenticador] simulado. TalkBack se verifica a mano en un dispositivo.
 */
class SeguridadScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val disponible = Capacidad(Disponibilidad.DISPONIBLE, listOf(MetodoBiometrico.HUELLA, MetodoBiometrico.FACIAL))

    private var configuracionOriginal: Configuration? = null

    /**
     * Los diálogos son otra ventana y no heredan el contexto que `setContentEnEspanol` sobrescribe: usan los recursos de
     * la actividad. Se fija es-CO ahí (el emulador del CI está en en-US) y se restaura al terminar.
     */
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

    private fun mostrar(estado: BiometriaUiState, acciones: SeguridadAcciones = SeguridadAcciones()) {
        fijarEspanolEnLaActividad()
        composeTestRule.setContentEnEspanol { SolventaTheme { SeguridadScreen(estado, acciones) } }
    }

    private fun listo(extra: BiometriaUiState.() -> BiometriaUiState = { this }) =
        BiometriaUiState(fase = FaseBiometria.ESTADO, capacidad = disponible, kycAprobado = true).extra()

    @Test
    fun disponible_ofrece_activar_con_las_garantias_de_privacidad() {
        var pedido = 0
        mostrar(listo(), SeguridadAcciones(abrirFormulario = { pedido++ }))

        composeTestRule.onNodeWithText("Tus datos biométricos se quedan en tu teléfono; Solventa nunca los recibe.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Inactivo").assertIsDisplayed()
        composeTestRule.onNodeWithText("Activar acceso biométrico").assertIsEnabled().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, pedido)
    }

    @Test
    fun la_pantalla_tiene_encabezado_para_el_lector_de_pantalla() {
        mostrar(listo())

        composeTestRule.onNode(isHeading() and hasText("Seguridad")).assertIsDisplayed()
        composeTestRule.onNode(isHeading() and hasText("Acceso biométrico")).assertIsDisplayed()
    }

    @Test
    fun sin_sensor_no_se_puede_activar_y_se_explica_por_que() {
        mostrar(listo { copy(capacidad = Capacidad(Disponibilidad.SIN_SENSOR, emptyList())) })

        composeTestRule.onNodeWithText("Este dispositivo no tiene un sensor de huella ni de reconocimiento facial compatible.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Activar acceso biométrico").assertIsNotEnabled()
    }

    @Test
    fun sin_biometria_registrada_se_pide_registrarla_en_ajustes() {
        mostrar(listo { copy(capacidad = Capacidad(Disponibilidad.SIN_REGISTRO, emptyList())) })

        composeTestRule.onNodeWithText(
            "No hay una huella ni un rostro registrados en este teléfono. Regístralos en los ajustes del sistema y vuelve aquí.",
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("Activar acceso biométrico").assertIsNotEnabled()
    }

    @Test
    fun con_el_kyc_sin_aprobar_no_se_puede_activar_y_se_indica_el_motivo() {
        mostrar(listo { copy(kycAprobado = false) })

        composeTestRule.onNodeWithText(
            "Primero debes verificar tu identidad. Cuando tu verificación esté aprobada podrás activar el acceso biométrico.",
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("Activar acceso biométrico").assertIsNotEnabled()
    }

    @Test
    fun el_formulario_deja_elegir_el_metodo_y_pide_la_contrasena_actual() {
        var contrasena: String? = null
        var metodo: MetodoBiometrico? = null
        mostrar(
            listo { copy(fase = FaseBiometria.FORMULARIO, metodoElegido = MetodoBiometrico.HUELLA) },
            SeguridadAcciones(confirmarActivacion = { contrasena = it }, elegirMetodo = { metodo = it }),
        )

        composeTestRule.onNodeWithText("Huella digital").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reconocimiento facial").performClick()
        assertEquals(MetodoBiometrico.FACIAL, metodo)

        composeTestRule.onNode(hasSetTextAction()).performTextInput("Clave1234")
        composeTestRule.onNodeWithText("Continuar").performClick()
        assertEquals("Clave1234", contrasena)
    }

    @Test
    fun la_contrasena_se_oculta_mientras_se_escribe() {
        mostrar(listo { copy(fase = FaseBiometria.FORMULARIO, metodoElegido = MetodoBiometrico.HUELLA) })

        composeTestRule.onNode(hasSetTextAction()).performTextInput("Clave1234")

        composeTestRule.onNodeWithText("Clave1234").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Mostrar contraseña").assertExists()
    }

    @Test
    fun una_contrasena_incorrecta_se_muestra_en_el_campo() {
        mostrar(
            listo {
                copy(fase = FaseBiometria.FORMULARIO, metodoElegido = MetodoBiometrico.HUELLA, error = ErrorAcceso.CONTRASENA_INCORRECTA)
            },
        )

        composeTestRule.onNodeWithText("La contraseña no es correcta.").assertIsDisplayed()
    }

    @Test
    fun cancelar_el_formulario_lo_cierra() {
        var cancelado = 0
        mostrar(
            listo { copy(fase = FaseBiometria.FORMULARIO, metodoElegido = MetodoBiometrico.HUELLA) },
            SeguridadAcciones(cancelarFormulario = { cancelado++ }),
        )

        composeTestRule.onNodeWithText("Cancelar").performClick()

        assertEquals(1, cancelado)
    }

    @Test
    fun activo_muestra_el_metodo_y_permite_desactivar_con_confirmacion() {
        var pedido = 0
        var confirmado = 0
        val activo = listo { copy(activo = true, metodoActivo = MetodoBiometrico.FACIAL) }
        mostrar(activo, SeguridadAcciones(pedirDesactivar = { pedido++ }, confirmarDesactivar = { confirmado++ }))

        composeTestRule.onNodeWithText("Activo").assertIsDisplayed()
        composeTestRule.onNodeWithText("Activado con Reconocimiento facial").assertIsDisplayed()
        composeTestRule.onNodeWithText("Desactivar acceso biométrico").performScrollTo().performClick()
        assertEquals(1, pedido)
        assertEquals(0, confirmado)
    }

    @Test
    fun la_confirmacion_de_desactivar_explica_que_se_borra_la_clave() {
        var confirmado = 0
        mostrar(
            listo { copy(fase = FaseBiometria.CONFIRMANDO_DESACTIVAR, activo = true, metodoActivo = MetodoBiometrico.HUELLA) },
            SeguridadAcciones(confirmarDesactivar = { confirmado++ }),
        )

        composeTestRule.onNodeWithText("¿Desactivar el acceso biométrico?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Desactivar").performClick()
        assertEquals(1, confirmado)
    }

    @Test
    fun si_cambiaron_las_biometrias_se_avisa_que_hay_que_volver_a_activar() {
        mostrar(listo { copy(aviso = AvisoAcceso.CLAVE_INVALIDADA) })

        composeTestRule.onNodeWithText(
            "Las biometrías de tu teléfono cambiaron, así que desactivamos el acceso biométrico por seguridad. Vuelve a activarlo si quieres usarlo.",
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("Activar acceso biométrico").assertIsEnabled()
    }

    @Test
    fun una_falla_de_red_ofrece_reintentar() {
        var reintentos = 0
        mostrar(
            listo { copy(error = ErrorAcceso.SIN_CONEXION, pendiente = AccionAcceso.CONSULTAR) },
            SeguridadAcciones(reintentar = { reintentos++ }),
        )

        composeTestRule.onNodeWithText("No hay conexión. Verifica tu red e intenta de nuevo.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reintentar").performClick()
        assertEquals(1, reintentos)
    }

    @Test
    fun cancelar_la_biometria_explica_que_no_se_activo() {
        mostrar(listo { copy(error = ErrorAcceso.CANCELADO) })

        composeTestRule.onNodeWithText("Cancelaste la confirmación biométrica. No activamos el acceso.").assertIsDisplayed()
    }

    @Test
    fun la_sesion_vencida_lleva_a_iniciar_de_nuevo() {
        var vencida = 0
        mostrar(listo { copy(error = ErrorAcceso.SESION_VENCIDA) }, SeguridadAcciones(sesionVencida = { vencida++ }))

        composeTestRule.onNodeWithText("Iniciar sesión de nuevo").performClick()

        assertEquals(1, vencida)
    }

    @Test
    fun mientras_carga_muestra_el_progreso() {
        mostrar(BiometriaUiState(fase = FaseBiometria.CARGANDO))

        composeTestRule.onNodeWithText("Consultando tu acceso biométrico…").assertIsDisplayed()
    }
}
