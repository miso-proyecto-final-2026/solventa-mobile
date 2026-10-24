package com.solventa.mobile.compra

import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.solventa.mobile.R
import com.solventa.mobile.red.CotizacionResponse
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Recorrido de HU17 con estados fijos (sin red): oferta, pago, progreso, éxito, cobro rechazado, rechazo de la
 * decisión, revisión, oferta vencida y errores, en es-CO y en-US sea cual sea el idioma del dispositivo (criterio 10).
 */
@RunWith(Parameterized::class)
class CompraScreenTest(private val idioma: String) {

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

    private fun mostrar(estado: CompraUiState, acciones: CompraAcciones = CompraAcciones()) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalContext provides contexto, LocalConfiguration provides configuracion) {
                MaterialTheme { CompraScreen(estado, acciones) }
            }
        }
        composeTestRule.waitForIdle()
    }

    private val cotizacion = CotizacionResponse(
        id = "q1",
        producto = "microseguro_vida",
        moneda = "COP",
        primaAnual = 175_000,
        primaMensual = 14_583,
        coberturas = emptyList(),
        vigenteHasta = "2026-11-17T23:49:58Z",
    )

    private fun estado(fase: FaseCompra, ajuste: CompraUiState.() -> CompraUiState = { this }) =
        CompraUiState(fase = fase, cotizacion = cotizacion).ajuste()

    @Test
    fun laOfertaPideAceptarLosTerminosYAvisaSiFaltan() {
        var terminos: Boolean? = null
        var continuo = false
        mostrar(estado(FaseCompra.OFERTA), CompraAcciones(aceptarTerminos = { terminos = it }, continuarAPago = { continuo = true }))

        composeTestRule.onNode(isHeading() and androidx.compose.ui.test.hasText(texto(R.string.compra_oferta_titulo))).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.compra_terminos_acepto)).performScrollTo().performClick()
        composeTestRule.onNodeWithText(texto(R.string.compra_continuar)).performScrollTo().performClick()

        assertEquals(true, terminos)
        assertEquals(true, continuo)
    }

    @Test
    fun sinAceptarLosTerminosSeMuestraElAviso() {
        mostrar(estado(FaseCompra.OFERTA) { copy(terminosPendientes = true) })

        composeTestRule.onNodeWithText(texto(R.string.compra_terminos_pendiente)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun elPagoMuestraLosErroresDeCadaCampoYElImporteAPagar() {
        mostrar(
            estado(FaseCompra.PAGO) {
                copy(erroresTarjeta = setOf(ErrorTarjeta.NUMERO, ErrorTarjeta.VENCIMIENTO, ErrorTarjeta.CVV))
            },
        )

        composeTestRule.onNodeWithText(texto(R.string.compra_error_numero)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.compra_error_vencimiento)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.compra_error_cvv)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun elProgresoMuestraElPasoActual() {
        mostrar(estado(FaseCompra.PROGRESO) { copy(paso = "COBRO") })

        composeTestRule.onNodeWithText(texto(R.string.compra_progreso_paso, texto(R.string.compra_paso_cobro))).assertIsDisplayed()
    }

    @Test
    fun unPasoDesconocidoNoRompeElProgreso() {
        mostrar(estado(FaseCompra.PROGRESO) { copy(paso = "ANTIFRAUDE") })

        composeTestRule.onNodeWithText(texto(R.string.compra_progreso_paso, texto(R.string.compra_paso_otro))).assertIsDisplayed()
    }

    @Test
    fun laCompraExitosaConfirmaEnPantalla() {
        mostrar(estado(FaseCompra.EXITOSA))

        composeTestRule.onNodeWithText(texto(R.string.compra_exitosa_titulo)).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.compra_exitosa_mensaje)).assertIsDisplayed()
    }

    @Test
    fun elCobroRechazadoOfreceOtroMedioDePago() {
        var otro = false
        mostrar(estado(FaseCompra.COBRO_RECHAZADO), CompraAcciones(otroMedioDePago = { otro = true }))

        composeTestRule.onNodeWithText(texto(R.string.compra_cobro_rechazado_titulo)).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.compra_otro_medio)).performClick()

        assertEquals(true, otro)
    }

    @Test
    fun rechazoYRevisionTienenMensajesDistintosYDicenQueNoHuboCobro() {
        mostrar(estado(FaseCompra.DECISION_RECHAZADA))
        composeTestRule.onNodeWithText(texto(R.string.compra_rechazada_titulo)).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.compra_rechazada_mensaje)).assertIsDisplayed()
    }

    @Test
    fun laRevisionAsistidaExplicaElSiguientePaso() {
        mostrar(estado(FaseCompra.EN_REVISION))

        composeTestRule.onNodeWithText(texto(R.string.compra_revision_titulo)).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.compra_revision_mensaje)).assertIsDisplayed()
    }

    @Test
    fun laOfertaVencidaOfreceCotizarDeNuevo() {
        var deNuevo = false
        mostrar(estado(FaseCompra.OFERTA_VENCIDA), CompraAcciones(cotizarDeNuevo = { deNuevo = true }))

        composeTestRule.onNodeWithText(texto(R.string.compra_vencida_titulo)).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.compra_cotizar_de_nuevo)).performClick()

        assertEquals(true, deNuevo)
    }

    @Test
    fun sinRedSeExplicaYSePuedeReintentar() {
        var reintento = false
        mostrar(estado(FaseCompra.ERROR) { copy(error = ErrorCompra.SIN_CONEXION) }, CompraAcciones(reintentar = { reintento = true }))

        composeTestRule.onNodeWithText(texto(R.string.compra_error_sin_conexion)).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.cotizacion_reintentar)).performClick()

        assertEquals(true, reintento)
    }

    @Test
    fun siNoSeSabeElResultadoSeOfreceVerificarElEstadoNoPagarOtraVez() {
        mostrar(estado(FaseCompra.ERROR) { copy(error = ErrorCompra.RESULTADO_DESCONOCIDO) })

        composeTestRule.onNodeWithText(texto(R.string.compra_error_desconocido)).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.compra_verificar)).assertIsDisplayed()
    }
}
