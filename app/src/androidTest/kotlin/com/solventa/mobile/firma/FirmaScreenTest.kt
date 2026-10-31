package com.solventa.mobile.firma

import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.solventa.mobile.R
import com.solventa.mobile.red.DocumentoFirmaResponse
import com.solventa.mobile.red.PolizaResponse
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Recorrido de HU21 con estados fijos (sin red): formulario de firma, avisos, resultados y errores, en es-CO y en-US
 * sea cual sea el idioma del dispositivo (criterio 9).
 */
@RunWith(Parameterized::class)
class FirmaScreenTest(private val idioma: String) {

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

    private fun mostrar(estado: FirmaUiState, acciones: FirmaAcciones = FirmaAcciones()) {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalContext provides contexto,
                LocalConfiguration provides configuracion,
                // El contexto con otro idioma ya no es la actividad: sin esto el selector de archivos no encuentra su registro.
                LocalActivityResultRegistryOwner provides composeTestRule.activity,
            ) {
                MaterialTheme { FirmaScreen(estado, acciones) }
            }
        }
        composeTestRule.waitForIdle()
    }

    private val documento = DocumentoFirmaResponse("s1", "36GBQKV2CWMM3EBB8Q6Q", "h".repeat(64), "PENDIENTE", "2026-10-31T20:43:00Z")

    private fun estado(fase: FaseFirma = FaseFirma.LISTA, ajuste: FirmaUiState.() -> FirmaUiState = { this }) =
        FirmaUiState(fase = fase, documento = documento).ajuste()

    @Test
    fun elFormularioMuestraElDocumentoElCodigoYLaConstancia() {
        mostrar(estado())

        composeTestRule.onNodeWithText(texto(R.string.firma_barra)).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.ui_paso_de, 3, 3, texto(R.string.compra_paso_firma))).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.firma_poliza, "36GBQKV2CWMM3EBB8Q6Q")).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.firma_revisado)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(texto(R.string.firma_codigo_etiqueta, LARGO_CODIGO)).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.firma_constancia)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.firma_firmar)).assertIsDisplayed()
    }

    @Test
    fun leerPideAbrirElDocumento() {
        var leido = 0
        mostrar(estado(), FirmaAcciones(leer = { leido++ }))

        composeTestRule.onNodeWithText(texto(R.string.firma_leer)).performClick()

        assertEquals(1, leido)
    }

    @Test
    fun marcarLaLecturaYEscribirElCodigoLlegaAlFlujo() {
        var revisado: Boolean? = null
        var codigo: String? = null
        mostrar(estado(), FirmaAcciones(marcarRevisado = { revisado = it }, cambiarCodigo = { codigo = it }))

        composeTestRule.onNodeWithText(texto(R.string.firma_revisado)).performClick()
        composeTestRule.onNodeWithTag("codigo").performTextInput("123456")

        assertEquals(true, revisado)
        assertEquals("123456", codigo)
    }

    @Test
    fun firmarPideLaFirmaAlFlujo() {
        var firmado = 0
        mostrar(estado { copy(revisado = true, codigo = "123456") }, FirmaAcciones(firmar = { firmado++ }))

        composeTestRule.onNodeWithText(texto(R.string.firma_firmar)).performClick()

        assertEquals(1, firmado)
    }

    @Test
    fun sinConfirmarLaLecturaOConUnCodigoIncompletoSeExplicaQueFalta() {
        mostrar(estado { copy(revisionPendiente = true, codigoIncompleto = true) })

        composeTestRule.onNodeWithText(texto(R.string.firma_revision_pendiente)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.firma_codigo_incompleto)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun unCodigoIncorrectoDiceCuantosIntentosQuedan() {
        mostrar(estado { copy(aviso = AvisoFirma.CODIGO_INCORRECTO, intentosRestantes = 2) })

        val esperado = contexto.resources.getQuantityString(R.plurals.firma_aviso_codigo_con_intentos, 2, 2)
        composeTestRule.onNodeWithText(esperado).assertIsDisplayed()
    }

    @Test
    fun unSoloIntentoRestanteUsaElSingular() {
        mostrar(estado { copy(aviso = AvisoFirma.CODIGO_INCORRECTO, intentosRestantes = 1) })

        val esperado = contexto.resources.getQuantityString(R.plurals.firma_aviso_codigo_con_intentos, 1, 1)
        composeTestRule.onNodeWithText(esperado).assertIsDisplayed()
    }

    @Test
    fun elReenvioEstaDeshabilitadoMientrasCorreLaCuentaAtras() {
        mostrar(estado { copy(segundosParaReenviar = 42) })

        composeTestRule.onNodeWithText(texto(R.string.firma_reenviar_en, "0:42")).assertIsNotEnabled()
    }

    @Test
    fun terminadaLaEsperaSePuedeReenviar() {
        var reenviado = 0
        mostrar(estado(), FirmaAcciones(reenviarCodigo = { reenviado++ }))

        composeTestRule.onNodeWithText(texto(R.string.firma_reenviar)).assertIsEnabled().performClick()

        assertEquals(1, reenviado)
    }

    private val polizaEmitida = PolizaResponse(
        id = "p1", suscripcionId = "s1", numeroPoliza = "36GBQKV2CWMM3EBB8Q6Q", producto = "microseguro_vida", estado = "VIGENTE",
        vigenciaDesde = "2026-10-30T20:00:00Z", fechaVencimiento = "2027-10-30T20:00:00Z", primaAnual = 175_000,
    )

    @Test
    fun laFirmaExitosaMuestraElCertificadoConLosDatosDeLaPoliza() {
        mostrar(estado(FaseFirma.FIRMADA) { copy(poliza = polizaEmitida) })

        composeTestRule.onNodeWithText(texto(R.string.certificado_titulo)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.certificado_mensaje)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.certificado_encabezado)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.certificado_activa)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.cotizacion_producto_microseguro_vida)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("36GBQKV2CWMM3EBB8Q6Q").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun siNoSePudoLeerElDetalleElCertificadoSeMuestraConElNumeroQueYaSeTiene() {
        mostrar(estado(FaseFirma.FIRMADA))

        composeTestRule.onNodeWithText(texto(R.string.certificado_titulo)).assertIsDisplayed()
        composeTestRule.onNodeWithText("36GBQKV2CWMM3EBB8Q6Q").assertIsDisplayed()
    }

    @Test
    fun descargarYTerminarLlegaAlFlujo() {
        var descargas = 0
        var terminado = 0
        mostrar(estado(FaseFirma.FIRMADA) { copy(poliza = polizaEmitida) }, FirmaAcciones(descargarCertificado = { descargas++ }, terminar = { terminado++ }))

        composeTestRule.onNodeWithTag("descargar_certificado").performClick()
        composeTestRule.onNodeWithText(texto(R.string.firma_ok_accion)).performClick()

        assertEquals(1, descargas)
        assertEquals(1, terminado)
    }

    @Test
    fun mientrasSeDescargaElBotonSeDeshabilitaYSeAvisa() {
        mostrar(estado(FaseFirma.FIRMADA) { copy(poliza = polizaEmitida, certificado = EstadoCertificado.DESCARGANDO) })

        composeTestRule.onNodeWithTag("descargar_certificado").assertIsNotEnabled()
        composeTestRule.onNodeWithText(texto(R.string.certificado_descargando)).assertIsDisplayed()
    }

    @Test
    fun guardadoElCertificadoSeOfreceAbrirlo() {
        var abierto = 0
        mostrar(
            estado(FaseFirma.FIRMADA) { copy(poliza = polizaEmitida, certificado = EstadoCertificado.GUARDADO) },
            FirmaAcciones(abrirCertificado = { abierto++ }),
        )

        composeTestRule.onNodeWithText(texto(R.string.certificado_guardado, texto(R.string.certificado_nombre_archivo, "36GBQKV2CWMM3EBB8Q6Q"))).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.certificado_abrir)).performClick()

        assertEquals(1, abierto)
    }

    @Test
    fun siFallaLaDescargaOElGuardadoSeExplicaYSePuedeReintentar() {
        mostrar(estado(FaseFirma.FIRMADA) { copy(poliza = polizaEmitida, certificado = EstadoCertificado.ERROR) })

        composeTestRule.onNodeWithText(texto(R.string.certificado_error)).assertIsDisplayed()
        composeTestRule.onNodeWithTag("descargar_certificado").assertIsEnabled()
    }

    @Test
    fun firmaFallidaYVencidaDicenQueSeDevuelveElCobroYOfrecenCotizar() {
        var cotizado = 0
        mostrar(estado(FaseFirma.FALLIDA), FirmaAcciones(cotizarDeNuevo = { cotizado++ }))
        composeTestRule.onNodeWithText(texto(R.string.firma_fallida_titulo)).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.firma_fallida_mensaje)).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.compra_cotizar_de_nuevo)).performClick()

        assertEquals(1, cotizado)
    }

    @Test
    fun laFirmaVencidaTieneSuPropioMensaje() {
        mostrar(estado(FaseFirma.VENCIDA))

        composeTestRule.onNodeWithText(texto(R.string.firma_vencida_titulo)).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.firma_vencida_mensaje)).assertIsDisplayed()
    }

    @Test
    fun cadaErrorTieneSuMensajeYSuAccion() {
        var reintentado = 0
        mostrar(estado(FaseFirma.ERROR) { copy(error = ErrorFirma.SIN_CONEXION) }, FirmaAcciones(reintentar = { reintentado++ }))

        composeTestRule.onNodeWithText(texto(R.string.compra_error_sin_conexion)).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.cotizacion_reintentar)).performClick()

        assertEquals(1, reintentado)
    }

    @Test
    fun laSesionVencidaLlevaAIniciarSesion() {
        var vencida = 0
        mostrar(estado(FaseFirma.ERROR) { copy(error = ErrorFirma.SESION_VENCIDA) }, FirmaAcciones(sesionVencida = { vencida++ }))

        composeTestRule.onNodeWithText(texto(R.string.compra_iniciar_sesion)).performClick()

        assertEquals(1, vencida)
    }

    @Test
    fun mientrasSePreparaOSeFirmaSoloSeMuestraLaEspera() {
        mostrar(estado(FaseFirma.CARGANDO))
        composeTestRule.onNodeWithText(texto(R.string.firma_cargando)).assertIsDisplayed()
    }

    @Test
    fun alFirmarSeMuestraElMensajeDeEspera() {
        mostrar(estado(FaseFirma.FIRMANDO))
        composeTestRule.onNodeWithText(texto(R.string.firma_firmando)).assertIsDisplayed()
    }
}
