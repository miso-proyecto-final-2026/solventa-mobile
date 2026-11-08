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
 * «Mis pólizas» (HU35) con estados fijos, sin red: lista, vacío, error, detalle con QR y documentos, en es-CO y en-US sea
 * cual sea el idioma del dispositivo (criterio 11).
 */
@RunWith(Parameterized::class)
class PolizasScreenTest(private val idioma: String) {

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

    private val ahora = Instant.parse("2026-11-08T12:00:00Z")

    private fun poliza(id: String, estado: String = "VIGENTE", vence: String = "2027-06-12T00:00:00Z") = PolizaResponse(
        id = "p-$id", suscripcionId = id, numeroPoliza = "SOL-$id", producto = "microseguro_vida", estado = estado,
        vigenciaDesde = "2026-06-12T00:00:00Z", fechaVencimiento = vence, fechaEmision = "2026-06-12T00:00:00Z",
        primaAnual = 175_000, primaMensual = 15_000,
        coberturas = listOf(CoberturaPolizaResponse("fallecimiento", 50_000_000), CoberturaPolizaResponse("incapacidad_total", 20_000_000, 500_000)),
    )

    private val vigente = poliza("s1")
    private val proxima = poliza("s2", vence = "2026-11-20T00:00:00Z")
    private val vencida = poliza("s3", vence = "2026-10-01T00:00:00Z")
    private val pendiente = poliza("s4", estado = "PENDIENTE_FIRMA")

    private fun lista(vararg polizas: PolizaResponse, ajuste: PolizasUiState.() -> PolizasUiState = { this }) =
        PolizasUiState(fase = FasePolizas.LISTA, polizas = polizas.toList(), ahora = ahora).ajuste()

    // --- lista ---------------------------------------------------------------------------------------------------------

    @Test
    fun la_lista_muestra_numero_estado_y_fechas_de_cada_poliza_activa() {
        mostrar { PolizasPantalla(lista(vigente, proxima, vencida, pendiente), PolizasAcciones()) }

        composeTestRule.onNodeWithText(texto(R.string.polizas_titulo)).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.polizas_numero, "SOL-s1")).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.polizas_estado_proxima)).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.polizas_estado_pendiente_firma)).assertIsDisplayed()
        composeTestRule.onNodeWithTag("poliza_s3").assertDoesNotExist() // la vencida está en la otra pestaña
    }

    @Test
    fun la_pestana_vencidas_muestra_las_vencidas() {
        mostrar { PolizasPantalla(lista(vigente, vencida) { copy(filtro = FiltroPolizas.VENCIDAS) }, PolizasAcciones()) }

        // «Expired» también es el nombre de la pestaña en en-US: el estado se busca dentro de la tarjeta.
        composeTestRule.onNodeWithTag("poliza_s3").assertIsDisplayed().assertTextContains(texto(R.string.polizas_estado_vencida), substring = true)
        composeTestRule.onNodeWithTag("poliza_s1").assertDoesNotExist()
    }

    @Test
    fun cambiar_de_pestana_y_abrir_una_poliza_llega_al_flujo() {
        var filtro: FiltroPolizas? = null
        var abierta: PolizaResponse? = null
        mostrar {
            PolizasPantalla(lista(vigente), PolizasAcciones(elegirFiltro = { filtro = it }, abrir = { abierta = it }))
        }

        composeTestRule.onNodeWithTag("filtro_vencidas").performClick()
        composeTestRule.onNodeWithTag("poliza_s1").performClick()

        assertEquals(FiltroPolizas.VENCIDAS, filtro)
        assertEquals(vigente, abierta)
    }

    @Test
    fun una_pestana_sin_polizas_lo_dice() {
        mostrar { PolizasPantalla(lista(vigente) { copy(filtro = FiltroPolizas.VENCIDAS) }, PolizasAcciones()) }

        composeTestRule.onNodeWithText(texto(R.string.polizas_vacio_vencidas)).assertIsDisplayed()
    }

    @Test
    fun la_firma_pendiente_ofrece_retomarla() {
        var retomada: PolizaResponse? = null
        mostrar { PolizasPantalla(lista(pendiente), PolizasAcciones(retomarFirma = { retomada = it })) }

        composeTestRule.onNodeWithTag("retomar_s4").performClick()

        assertEquals(pendiente, retomada)
    }

    @Test
    fun sin_polizas_hay_un_estado_vacio_con_acceso_a_cotizar() {
        var cotizar = 0
        mostrar { PolizasPantalla(lista(), PolizasAcciones(cotizar = { cotizar++ })) }

        composeTestRule.onNodeWithText(texto(R.string.polizas_vacio_titulo)).assertIsDisplayed()
        composeTestRule.onNodeWithTag("polizas_cotizar").performClick()

        assertEquals(1, cotizar)
    }

    @Test
    fun cargando_y_error_tienen_su_estado() {
        mostrar { PolizasPantalla(PolizasUiState(fase = FasePolizas.CARGANDO), PolizasAcciones()) }
        composeTestRule.onNodeWithTag("polizas_cargando").assertIsDisplayed()
    }

    @Test
    fun el_error_de_conexion_permite_reintentar() {
        var reintentos = 0
        mostrar {
            PolizasPantalla(
                PolizasUiState(fase = FasePolizas.ERROR, error = ErrorPolizas.SIN_CONEXION),
                PolizasAcciones(reintentar = { reintentos++ }),
            )
        }

        composeTestRule.onNodeWithText(texto(R.string.polizas_error_conexion)).assertIsDisplayed()
        composeTestRule.onNodeWithTag("polizas_reintentar").performClick()

        assertEquals(1, reintentos)
    }

    // --- detalle -------------------------------------------------------------------------------------------------------

    private fun detalle(poliza: PolizaResponse, ajuste: PolizasUiState.() -> PolizasUiState = { this }) =
        lista(poliza) { copy(seleccionada = poliza) }.ajuste()

    @Test
    fun el_detalle_muestra_la_credencial_con_el_qr_las_coberturas_y_la_prima() {
        val estado = detalle(vigente) { copy(qr = EnlaceQr.Listo("https://d1.cloudfront.net/v1/certificados/abc.def")) }
        mostrar { PolizaDetallePantalla(estado, vigente, PolizaDetalleAcciones()) }

        composeTestRule.onNodeWithTag("credencial").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(texto(R.string.polizas_qr_descripcion, "SOL-s1")).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.polizas_coberturas)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Fallecimiento", substring = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.polizas_prima_anual)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun mientras_llega_el_enlace_se_anuncia_la_generacion_y_si_falla_se_puede_reintentar() {
        var reintentos = 0
        val estado = detalle(vigente) { copy(qr = EnlaceQr.Fallido) }
        mostrar { PolizaDetallePantalla(estado, vigente, PolizaDetalleAcciones(reintentarQr = { reintentos++ })) }

        composeTestRule.onNodeWithText(texto(R.string.polizas_qr_error)).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.polizas_reintentar)).performClick()

        assertEquals(1, reintentos)
    }

    @Test
    fun una_poliza_vencida_no_muestra_qr_pero_conserva_los_documentos() {
        mostrar { PolizaDetallePantalla(detalle(vencida), vencida, PolizaDetalleAcciones()) }

        composeTestRule.onNodeWithContentDescription(texto(R.string.polizas_qr_descripcion, "SOL-s3")).assertDoesNotExist()
        composeTestRule.onNodeWithTag("descargar_certificado").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun la_poliza_con_firma_pendiente_ofrece_retomar_y_no_ofrece_documentos() {
        var retomada: PolizaResponse? = null
        mostrar { PolizaDetallePantalla(detalle(pendiente), pendiente, PolizaDetalleAcciones(retomarFirma = { retomada = it })) }

        composeTestRule.onNodeWithTag("retomar_firma").performScrollTo().performClick()

        assertEquals(pendiente, retomada)
        composeTestRule.onNodeWithTag("documentos").assertDoesNotExist()
    }

    @Test
    fun descargar_el_certificado_o_la_poliza_firmada_llega_al_flujo() {
        val pedidos = mutableListOf<DocumentoPoliza>()
        mostrar { PolizaDetallePantalla(detalle(vigente), vigente, PolizaDetalleAcciones(descargar = { pedidos += it })) }

        composeTestRule.onNodeWithTag("descargar_firmada").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("descargar_certificado").performScrollTo().performClick()

        assertEquals(listOf(DocumentoPoliza.POLIZA_FIRMADA, DocumentoPoliza.CERTIFICADO), pedidos)
    }

    @Test
    fun el_estado_de_la_descarga_se_anuncia() {
        val estado = detalle(vigente) { copy(documento = DocumentoPoliza.CERTIFICADO, descarga = EstadoDescarga.NO_DISPONIBLE, pdf = null) }
        mostrar { PolizaDetallePantalla(estado, vigente, PolizaDetalleAcciones()) }

        composeTestRule.onNodeWithText(texto(R.string.polizas_descarga_no_disponible)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun el_deducible_va_en_su_propio_renglon_debajo_de_la_cobertura() {
        mostrar { PolizaDetallePantalla(detalle(vigente), vigente, PolizaDetalleAcciones()) }

        // La cobertura con deducible es la segunda (índice 1): su fila no menciona el deducible, la siguiente sí.
        composeTestRule.onNodeWithText("Incapacidad total").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("deducible_1").performScrollTo().assertIsDisplayed()
            .assertTextContains(texto(R.string.polizas_deducible), substring = true)
        composeTestRule.onNodeWithTag("deducible_0").assertDoesNotExist() // la primera cobertura no tiene deducible
    }

    @Test
    fun cada_estado_muestra_su_chip() {
        mostrar { PolizasPantalla(lista(vigente, proxima, pendiente), PolizasAcciones()) }

        composeTestRule.onNodeWithTag("poliza_s1").assertTextContains(texto(R.string.polizas_estado_vigente), substring = true)
        composeTestRule.onNodeWithTag("poliza_s2").assertTextContains(texto(R.string.polizas_estado_proxima), substring = true)
        composeTestRule.onNodeWithTag("poliza_s4").assertTextContains(texto(R.string.polizas_estado_pendiente_firma), substring = true)
    }

    @Test
    fun una_poliza_sin_coberturas_guardadas_lo_dice() {
        val antigua = vigente.copy(coberturas = emptyList())
        mostrar { PolizaDetallePantalla(detalle(antigua), antigua, PolizaDetalleAcciones()) }

        composeTestRule.onNodeWithTag("sin_coberturas").performScrollTo().assertIsDisplayed()
    }
}
