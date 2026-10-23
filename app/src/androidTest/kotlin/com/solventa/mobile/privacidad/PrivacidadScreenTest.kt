package com.solventa.mobile.privacidad

import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.solventa.mobile.R
import com.solventa.mobile.red.ConsentimientoResponse
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Recorrido de HU07 con estados fijos (sin red): estado, confirmación, progreso, éxito y cada error, en es-CO y
 * en-US sea cual sea el idioma del dispositivo (criterio 9). TalkBack se verifica a mano en un dispositivo.
 */
@RunWith(Parameterized::class)
class PrivacidadScreenTest(private val idioma: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun idiomas() = listOf("es-CO", "en-US")
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val locale: Locale = Locale.forLanguageTag(idioma)

    private val configuracion: Configuration by lazy {
        Configuration(composeTestRule.activity.resources.configuration).apply { setLocale(locale) }
    }

    private val contexto: Context by lazy { composeTestRule.activity.createConfigurationContext(configuracion) }

    private fun texto(id: Int, vararg argumentos: Any) = contexto.getString(id, *argumentos)

    private fun mostrar(estado: PrivacidadUiState, acciones: PrivacidadAcciones = PrivacidadAcciones()) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalContext provides contexto, LocalConfiguration provides configuracion) {
                MaterialTheme { PrivacidadScreen(estado, acciones) }
            }
        }
        composeTestRule.waitForIdle()
    }

    private val otorgado = ConsentimientoResponse(
        clienteId = "c1",
        tipo = TIPO_CONSENTIMIENTO,
        estado = ESTADO_OTORGADO,
        versionTexto = "v1",
        fechaOtorgamiento = "2026-10-20T15:00:00Z",
    )
    private val revocado = otorgado.copy(estado = ESTADO_REVOCADO, fechaRevocacion = "2026-10-22T18:30:00Z")
    private val sinOtorgar = ConsentimientoResponse(clienteId = "c1", tipo = TIPO_CONSENTIMIENTO, estado = ESTADO_NO_OTORGADO)

    private fun estado(consentimiento: ConsentimientoResponse? = otorgado, fase: FasePrivacidad = FasePrivacidad.ESTADO) =
        PrivacidadUiState(fase = fase, consentimiento = consentimiento)

    @Test
    fun otorgado_muestra_el_estado_con_su_fecha_la_finalidad_y_la_accion_de_revocar() {
        mostrar(estado())

        composeTestRule.onNode(isHeading() and hasText(texto(R.string.privacidad_titulo))).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.privacidad_consentimiento_titulo)).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.privacidad_finalidad)).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.privacidad_revocar)).assertIsDisplayed().assertHasClickAction()
        composeTestRule.onAllNodesWithText(texto(R.string.privacidad_compartir)).assertCountEquals(0)
    }

    @Test
    fun revocado_muestra_la_fecha_de_revocacion_y_ofrece_compartir_de_nuevo() {
        mostrar(estado(revocado))

        composeTestRule.onNodeWithText(texto(R.string.privacidad_compartir)).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(texto(R.string.privacidad_revocar)).assertCountEquals(0)
    }

    @Test
    fun sin_otorgar_ofrece_compartir_y_no_revocar() {
        mostrar(estado(sinOtorgar))

        composeTestRule.onNodeWithText(texto(R.string.privacidad_estado_no_otorgado)).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.privacidad_compartir)).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(texto(R.string.privacidad_revocar)).assertCountEquals(0)
    }

    @Test
    fun revocar_pide_confirmacion_con_las_consecuencias() {
        var pedido = 0
        mostrar(estado(), PrivacidadAcciones(pedirRevocar = { pedido++ }))

        composeTestRule.onNodeWithText(texto(R.string.privacidad_revocar)).performScrollTo().performClick()

        assertEquals(1, pedido)
    }

    @Test
    fun la_confirmacion_explica_las_consecuencias_y_permite_confirmar_o_cancelar() {
        var confirmado = 0
        var cancelado = 0
        mostrar(
            estado(fase = FasePrivacidad.CONFIRMANDO),
            PrivacidadAcciones(confirmarRevocar = { confirmado++ }, cancelar = { cancelado++ }),
        )

        composeTestRule.onNodeWithText(texto(R.string.privacidad_confirmar_titulo)).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.privacidad_confirmar_texto)).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.privacidad_confirmar_no)).assertHeightIsAtLeast(48.dp).performClick()
        composeTestRule.onNodeWithText(texto(R.string.privacidad_confirmar_si)).assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(1, cancelado)
        assertEquals(1, confirmado)
    }

    @Test
    fun mientras_revoca_muestra_el_progreso_y_bloquea_volver() {
        mostrar(estado(fase = FasePrivacidad.REVOCANDO))

        composeTestRule.onNodeWithText(texto(R.string.privacidad_revocando)).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.privacidad_volver)).assertIsNotEnabled()
    }

    @Test
    fun mientras_consulta_muestra_el_progreso() {
        mostrar(PrivacidadUiState(fase = FasePrivacidad.CARGANDO))

        composeTestRule.onNodeWithText(texto(R.string.privacidad_cargando)).assertIsDisplayed()
    }

    @Test
    fun tras_confirmar_el_servidor_muestra_el_aviso_de_exito() {
        mostrar(estado(revocado).copy(recienRevocado = true))

        composeTestRule.onNodeWithText(texto(R.string.privacidad_revocado_ok)).assertIsDisplayed()
    }

    @Test
    fun cada_falla_tiene_su_mensaje_y_su_accion() {
        val casos = listOf(
            ErrorPrivacidad.TIEMPO_AGOTADO to R.string.privacidad_error_tiempo,
            ErrorPrivacidad.SIN_CONEXION to R.string.privacidad_error_sin_conexion,
            ErrorPrivacidad.SERVICIO to R.string.privacidad_error_servicio,
        )
        casos.forEach { (error, mensaje) ->
            var reintentos = 0
            composeTestRule.setContent {
                CompositionLocalProvider(LocalContext provides contexto, LocalConfiguration provides configuracion) {
                    MaterialTheme {
                        PrivacidadScreen(
                            estado().copy(error = error, pendiente = AccionPrivacidad.REVOCAR),
                            PrivacidadAcciones(reintentar = { reintentos++ }),
                        )
                    }
                }
            }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText(texto(mensaje)).assertIsDisplayed()
            composeTestRule.onNodeWithText(texto(R.string.privacidad_reintentar)).performClick()
            assertEquals(1, reintentos)
        }
    }

    @Test
    fun si_no_hubo_confirmacion_el_consentimiento_sigue_mostrandose_vigente_con_aviso() {
        mostrar(estado().copy(error = ErrorPrivacidad.SIN_CONEXION, pendiente = AccionPrivacidad.REVOCAR))

        composeTestRule.onNodeWithText(texto(R.string.privacidad_sigue_vigente)).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.privacidad_revocar)).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(texto(R.string.privacidad_revocado_ok)).assertCountEquals(0)
    }

    @Test
    fun sesion_vencida_ofrece_iniciar_sesion_y_no_reintentar() {
        var sesion = 0
        mostrar(
            estado().copy(error = ErrorPrivacidad.SESION_VENCIDA, pendiente = AccionPrivacidad.REVOCAR),
            PrivacidadAcciones(sesionVencida = { sesion++ }),
        )

        composeTestRule.onNodeWithText(texto(R.string.privacidad_error_sesion)).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(texto(R.string.privacidad_reintentar)).assertCountEquals(0)
        composeTestRule.onNodeWithText(texto(R.string.privacidad_iniciar_sesion)).performClick()
        assertEquals(1, sesion)
    }

    @Test
    fun compartir_y_volver_disparan_sus_acciones() {
        var compartir = 0
        var volver = 0
        mostrar(estado(revocado), PrivacidadAcciones(compartirDatos = { compartir++ }, volver = { volver++ }))

        composeTestRule.onNodeWithText(texto(R.string.privacidad_compartir)).performScrollTo().performClick()
        composeTestRule.onNodeWithText(texto(R.string.privacidad_volver)).performScrollTo().performClick()

        assertEquals(1, compartir)
        assertEquals(1, volver)
    }

    @Test
    fun los_botones_principales_miden_al_menos_48dp() {
        mostrar(estado())

        composeTestRule.onNodeWithText(texto(R.string.privacidad_revocar)).assertHeightIsAtLeast(48.dp)
        composeTestRule.onNodeWithText(texto(R.string.privacidad_volver)).assertHeightIsAtLeast(48.dp)
    }
}
