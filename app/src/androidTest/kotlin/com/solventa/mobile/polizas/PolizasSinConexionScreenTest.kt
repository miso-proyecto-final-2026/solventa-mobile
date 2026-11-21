package com.solventa.mobile.polizas

import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.solventa.mobile.R
import com.solventa.mobile.red.CoberturaPolizaResponse
import com.solventa.mobile.red.PolizaResponse
import java.time.Instant
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * «Mis pólizas» sin conexión (HU36) con estados fijos: la copia con su aviso y fecha, lo que requiere conexión y los
 * documentos descargados, en es-CO y en-US sea cual sea el idioma del dispositivo (criterio 9). El modo avión real se
 * muestra en el video de la historia; aquí la falta de red llega como estado.
 */
@RunWith(Parameterized::class)
class PolizasSinConexionScreenTest(private val idioma: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun idiomas() = listOf("es-CO", "en-US")
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val configuracion: Configuration by lazy {
        Configuration(composeTestRule.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag(idioma)) }
    }
    private val contexto: Context by lazy { composeTestRule.activity.createConfigurationContext(configuracion) }

    private fun texto(id: Int, vararg argumentos: Any) = contexto.getString(id, *argumentos)

    private fun mostrar(contenido: @Composable () -> Unit) {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalContext provides contexto,
                LocalConfiguration provides configuracion,
                LocalActivityResultRegistryOwner provides composeTestRule.activity,
            ) { MaterialTheme { contenido() } }
        }
        composeTestRule.waitForIdle()
    }

    private val ahora = Instant.parse("2026-11-14T12:00:00Z")
    private val sincronizado = Instant.parse("2026-11-10T09:15:00Z")

    /** Como vuelve de la copia local: sin prima (criterio 7). */
    private fun poliza(id: String, estado: String = "VIGENTE", vence: String = "2027-06-12T00:00:00Z") = PolizaResponse(
        id = "p-$id", suscripcionId = id, numeroPoliza = "SOL-$id", producto = "microseguro_vida", estado = estado,
        vigenciaDesde = "2026-06-12T00:00:00Z", fechaVencimiento = vence,
        coberturas = listOf(CoberturaPolizaResponse("fallecimiento", 50_000_000)),
    )

    private val vigente = poliza("s1")
    private val vencioSinRed = poliza("s2", vence = "2026-11-12T00:00:00Z")
    private val pendiente = poliza("s4", estado = "PENDIENTE_FIRMA")

    private fun copia(vararg polizas: PolizaResponse, ajuste: PolizasUiState.() -> PolizasUiState = { this }) = PolizasUiState(
        fase = FasePolizas.LISTA,
        polizas = polizas.toList(),
        ahora = ahora,
        sinConexion = true,
        sincronizadoEn = sincronizado,
    ).ajuste()

    // --- lista ---------------------------------------------------------------------------------------------------------

    @Test
    fun sin_conexion_la_lista_avisa_que_es_una_copia_y_muestra_la_ultima_sincronizacion() {
        mostrar { PolizasPantalla(copia(vigente), PolizasAcciones()) }

        composeTestRule.onNodeWithTag("aviso_copia")
            .assertIsDisplayed()
            .assertTextContains(texto(R.string.polizas_copia_aviso), substring = true)
            .assertTextContains(texto(R.string.polizas_copia_sincronizado, "").trim(), substring = true)
        composeTestRule.onNodeWithText("SOL-s1", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun con_conexion_no_hay_aviso_de_copia() {
        mostrar { PolizasPantalla(copia(vigente) { copy(sinConexion = false) }, PolizasAcciones()) }

        composeTestRule.onNodeWithTag("aviso_copia").assertDoesNotExist()
    }

    @Test
    fun una_poliza_que_vencio_sin_red_se_ve_vencida() {
        mostrar { PolizasPantalla(copia(vigente, vencioSinRed) { copy(filtro = FiltroPolizas.VENCIDAS) }, PolizasAcciones()) }

        composeTestRule.onNodeWithTag("poliza_s2").assertTextContains(texto(R.string.polizas_estado_vencida), substring = true)
    }

    @Test
    fun sin_conexion_retomar_la_firma_esta_deshabilitado_y_dice_que_requiere_conexion() {
        mostrar { PolizasPantalla(copia(pendiente), PolizasAcciones()) }

        composeTestRule.onNodeWithTag("retomar_s4", useUnmergedTree = true).assertIsNotEnabled()
        composeTestRule.onNodeWithTag("requiere_conexion", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun sin_conexion_y_sin_polizas_cotizar_requiere_conexion() {
        mostrar { PolizasPantalla(copia(), PolizasAcciones()) }

        composeTestRule.onNodeWithTag("polizas_cotizar").assertIsNotEnabled()
        composeTestRule.onNodeWithText(texto(R.string.polizas_requiere_conexion)).assertIsDisplayed()
    }

    // --- detalle -------------------------------------------------------------------------------------------------------

    private fun detalle(poliza: PolizaResponse, ajuste: PolizasUiState.() -> PolizasUiState = { this }) =
        copia(poliza) { copy(seleccionada = poliza, qr = EnlaceQr.RequiereConexion).ajuste() }

    @Test
    fun sin_conexion_el_detalle_muestra_coberturas_y_vigencia_sin_qr_ni_prima() {
        mostrar { PolizaDetallePantalla(detalle(vigente), vigente, PolizaDetalleAcciones()) }

        composeTestRule.onNodeWithTag("aviso_copia").assertIsDisplayed()
        composeTestRule.onNodeWithTag("qr_requiere_conexion").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(texto(R.string.polizas_qr_descripcion, "SOL-s1")).assertDoesNotExist()
        composeTestRule.onNodeWithText("Fallecimiento", substring = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.polizas_prima_anual)).assertDoesNotExist()
        composeTestRule.onNodeWithText(texto(R.string.polizas_prima_mensual)).assertDoesNotExist()
    }

    @Test
    fun sin_conexion_un_documento_descargado_se_abre_y_el_otro_pide_conexion() {
        val abiertos = mutableListOf<DocumentoPoliza>()
        mostrar {
            PolizaDetallePantalla(
                detalle(vigente),
                vigente,
                PolizaDetalleAcciones(abrirDocumento = { abiertos += it }),
                documentosDisponibles = setOf(DocumentoPoliza.CERTIFICADO),
            )
        }

        composeTestRule.onNodeWithTag("abrir_certificado").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("sin_descargar_poliza_firmada").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("descargar_certificado").assertDoesNotExist()

        assertEquals(listOf(DocumentoPoliza.CERTIFICADO), abiertos)
    }

    @Test
    fun sin_conexion_y_sin_documentos_descargados_ambos_piden_conexion() {
        mostrar { PolizaDetallePantalla(detalle(vigente), vigente, PolizaDetalleAcciones()) }

        composeTestRule.onNodeWithTag("sin_descargar_poliza_firmada").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("sin_descargar_certificado").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun sin_conexion_retomar_la_firma_desde_el_detalle_esta_deshabilitado() {
        mostrar { PolizaDetallePantalla(detalle(pendiente) { copy(qr = EnlaceQr.NoAplica) }, pendiente, PolizaDetalleAcciones()) }

        composeTestRule.onNodeWithTag("retomar_firma").performScrollTo().assertIsNotEnabled()
    }
}
