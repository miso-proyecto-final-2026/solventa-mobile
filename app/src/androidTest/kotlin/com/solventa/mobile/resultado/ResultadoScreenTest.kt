package com.solventa.mobile.resultado

import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import com.solventa.mobile.R
import com.solventa.mobile.red.ConsentimientoApi
import com.solventa.mobile.red.ConsentimientoRequest
import com.solventa.mobile.red.ConsentimientoResponse
import com.solventa.mobile.red.EstadoKycResponse
import com.solventa.mobile.red.VerificacionApi
import com.solventa.mobile.red.VerificacionResponse
import com.solventa.mobile.sesion.SesionActual
import java.time.Instant
import java.util.Locale
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import retrofit2.Response

/**
 * Los cuatro resultados de HU03 con MS Identidad simulado: contenido, una sola acción por pantalla,
 * navegación y semántica para el lector de pantalla (título como encabezado, acción pulsable con
 * objetivo táctil de 48 dp). Cada prueba corre en es-CO y en en-US, sea cual sea el idioma del
 * dispositivo (criterio 6).
 */
@RunWith(Parameterized::class)
class ResultadoScreenTest(private val idioma: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun idiomas() = listOf("es-CO", "en-US")
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val configuracion: Configuration by lazy {
        Configuration(composeTestRule.activity.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(idioma))
        }
    }

    /** Contexto en el idioma de la prueba: el de la pantalla y el de los textos esperados. */
    private val contexto: Context by lazy { composeTestRule.activity.createConfigurationContext(configuracion) }

    private fun texto(id: Int, vararg argumentos: Any) = contexto.getString(id, *argumentos)

    private class Servidor : VerificacionApi, ConsentimientoApi {
        var estado = EstadoKycResponse("c1", "VERIFICADO", null, 3, null, "v1")
        var consentimiento = "OTORGADO"
        var consultas = 0

        override suspend fun consultarEstado(clienteId: String, autorizacion: String): Response<EstadoKycResponse> {
            consultas++
            return Response.success(estado)
        }

        override suspend fun consultar(
            clienteId: String,
            tipo: String,
            autorizacion: String,
        ): Response<ConsentimientoResponse> = Response.success(ConsentimientoResponse(clienteId, tipo, consentimiento))

        override suspend fun enviar(
            clienteId: String,
            autorizacion: String,
            idempotencyKey: String,
            reto: RequestBody,
            anverso: MultipartBody.Part,
            reverso: MultipartBody.Part,
            selfie: MultipartBody.Part,
        ): Response<VerificacionResponse> = error("no se usa")

        override suspend fun otorgar(
            clienteId: String,
            autorizacion: String,
            solicitud: ConsentimientoRequest,
        ): Response<ConsentimientoResponse> = error("no se usa")
    }

    private val servidor = Servidor()
    private val destinos = mutableListOf<Destino>()
    private var salidas = 0

    @Before
    fun abrirSesion() = SesionActual.iniciar("c1", "token")

    private fun mostrar(estado: EstadoKycResponse) {
        servidor.estado = estado
        val vm = ResultadoViewModel(ResultadoRepository(servidor, servidor))
        composeTestRule.setContent {
            CompositionLocalProvider(LocalContext provides contexto, LocalConfiguration provides configuracion) {
                ResultadoRoute(viewModel = vm, onDestino = { destinos += it }, onSalir = { salidas++ })
            }
        }
        composeTestRule.waitUntil(5_000) { servidor.consultas > 0 }
        composeTestRule.waitForIdle()
    }

    private fun estado(estado: String, intentos: Int = 3, bloqueadoHasta: String? = null) =
        EstadoKycResponse("c1", estado, if (estado == "RECHAZADO") "VERIFICACION_NO_SUPERADA" else null, intentos, bloqueadoHasta, "v1")

    /** Criterio 6: título anunciado como encabezado y una acción pulsable de tamaño suficiente. */
    private fun verificarAccesibilidad(titulo: Int, accion: Int) {
        composeTestRule.onNode(hasText(texto(titulo)) and isHeading()).assertIsDisplayed()
        composeTestRule.onNode(hasText(texto(accion)) and hasClickAction())
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
        // Una sola acción por pantalla.
        check(composeTestRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size == 1)
    }

    // -- criterio 1 --------------------------------------------------------------------------

    @Test
    fun aprobadaConfirmaYContinuarLlevaAlConsentimientoSiFalta() {
        servidor.consentimiento = "NO_OTORGADO"
        mostrar(estado("VERIFICADO"))

        composeTestRule.onNodeWithText(texto(R.string.resultado_aprobada_mensaje)).assertIsDisplayed()
        verificarAccesibilidad(R.string.resultado_aprobada_titulo, R.string.resultado_aprobada_accion)

        composeTestRule.onNodeWithText(texto(R.string.resultado_aprobada_accion)).performClick()
        composeTestRule.waitUntil(5_000) { destinos.isNotEmpty() }
        check(destinos == listOf(Destino.CONSENTIMIENTO_TRAS_APROBAR)) { destinos.toString() }
    }

    @Test
    fun aprobadaConConsentimientoOtorgadoContinuaAlInicio() {
        mostrar(estado("VERIFICADO"))

        composeTestRule.onNodeWithText(texto(R.string.resultado_aprobada_accion)).performClick()

        composeTestRule.waitUntil(5_000) { destinos.isNotEmpty() }
        check(destinos == listOf(Destino.INICIO)) { destinos.toString() }
    }

    // -- criterio 2 --------------------------------------------------------------------------

    @Test
    fun rechazadaConIntentosIndicaQueCorregirYPermiteReintentar() {
        mostrar(estado("RECHAZADO", intentos = 2))

        composeTestRule.onNodeWithText(texto(R.string.resultado_rechazada_guia)).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            contexto.resources.getQuantityString(R.plurals.resultado_intentos_restantes, 2, 2),
        ).assertIsDisplayed()
        // Sin criterios internos del proveedor ni el código técnico.
        composeTestRule.onNodeWithText("VERIFICACION_NO_SUPERADA", substring = true).assertDoesNotExist()
        verificarAccesibilidad(R.string.resultado_rechazada_titulo, R.string.resultado_rechazada_accion)

        composeTestRule.onNodeWithText(texto(R.string.resultado_rechazada_accion)).performClick()
        composeTestRule.waitForIdle()
        check(destinos == listOf(Destino.VERIFICACION)) { destinos.toString() }
    }

    // -- criterio 3 --------------------------------------------------------------------------

    @Test
    fun sinIntentosInformaElCanalDeAtencionYNoOfreceReintento() {
        val hasta = "2026-10-16T12:30:00Z"
        mostrar(estado("RECHAZADO", intentos = 0, bloqueadoHasta = hasta))

        composeTestRule.onNodeWithText(texto(R.string.resultado_bloqueada_mensaje)).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            texto(R.string.resultado_bloqueada_canal, texto(R.string.resultado_canal_atencion)),
        ).assertIsDisplayed()
        val hora = formatearHora(Instant.parse(hasta), Locale.forLanguageTag(idioma))
        composeTestRule.onNodeWithText(texto(R.string.resultado_bloqueada_hora, hora)).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.resultado_rechazada_accion)).assertDoesNotExist()
        verificarAccesibilidad(R.string.resultado_bloqueada_titulo, R.string.resultado_entendido)

        composeTestRule.onNodeWithText(texto(R.string.resultado_entendido)).performClick()
        check(salidas == 1)
    }

    // -- criterio 4 --------------------------------------------------------------------------

    @Test
    fun enRevisionInformaYAlVolverALaAppMuestraElEstadoActualizado() {
        mostrar(estado("EN_REVISION"))

        composeTestRule.onNodeWithText(texto(R.string.resultado_revision_mensaje)).assertIsDisplayed()
        verificarAccesibilidad(R.string.resultado_revision_titulo, R.string.resultado_entendido)

        // El usuario sale de la app; mientras tanto el caso se resuelve.
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        servidor.estado = estado("VERIFICADO")
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodes(hasText(texto(R.string.resultado_aprobada_titulo))).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(texto(R.string.resultado_aprobada_titulo)).assertIsDisplayed()
    }

    @Test
    fun enRevisionEntendidoSaleDeLaApp() {
        mostrar(estado("EN_REVISION"))

        composeTestRule.onNodeWithText(texto(R.string.resultado_entendido)).performClick()

        check(salidas == 1)
    }

    // -- sin resultado -----------------------------------------------------------------------

    @Test
    fun pendienteNoMuestraResultadoYLlevaAVerificar() {
        mostrar(estado("PENDIENTE"))

        composeTestRule.waitUntil(5_000) { destinos.isNotEmpty() }
        check(destinos == listOf(Destino.VERIFICACION)) { destinos.toString() }
    }
}
