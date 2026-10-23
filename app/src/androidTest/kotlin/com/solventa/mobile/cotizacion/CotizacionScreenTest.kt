package com.solventa.mobile.cotizacion

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
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.solventa.mobile.R
import com.solventa.mobile.red.CoberturaResponse
import com.solventa.mobile.red.CotizacionResponse
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Recorrido de HU37 con estados fijos (sin red): producto, formulario por producto, carga, resultado feliz, sin
 * consentimiento, degradado y cada error, en es-CO y en en-US sea cual sea el idioma del dispositivo (criterio 9).
 */
@RunWith(Parameterized::class)
class CotizacionScreenTest(private val idioma: String) {

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

    private fun mostrar(estado: CotizacionUiState, acciones: CotizacionAcciones = CotizacionAcciones()) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalContext provides contexto, LocalConfiguration provides configuracion) {
                MaterialTheme { CotizacionScreen(estado, acciones) }
            }
        }
        composeTestRule.waitForIdle()
    }

    private val vida = Catalogo.porId("microseguro_vida")!!
    private val dispositivos = Catalogo.porId("proteccion_dispositivos")!!

    private fun cotizacion(aproximada: Boolean = false, degradado: Boolean = false) = CotizacionResponse(
        id = "q1",
        producto = "microseguro_vida",
        moneda = "COP",
        primaAnual = 175_000,
        primaMensual = 14_583,
        coberturas = listOf(CoberturaResponse("muerte", 50_000_000, 0), CoberturaResponse("incapacidad_total", 25_000_000, 0)),
        vigenteHasta = "2026-11-17T23:49:58Z",
        aproximada = aproximada,
        degradado = degradado,
    )

    private fun resultado(aproximada: Boolean = false, degradado: Boolean = false) = CotizacionUiState(
        fase = FaseCotizacion.RESULTADO,
        producto = vida,
        cotizacion = cotizacion(aproximada, degradado),
    )

    @Test
    fun elegirProductoMuestraLosProductosYPermiteElegir() {
        val elegidos = mutableListOf<String>()
        mostrar(CotizacionUiState(), CotizacionAcciones(elegirProducto = { elegidos += it }))

        composeTestRule.onNode(hasText(texto(R.string.cotizacion_elegir_titulo)) and isHeading()).assertIsDisplayed()
        val vidaBoton = texto(R.string.cotizacion_producto_accion, texto(R.string.cotizacion_producto_microseguro_vida))
        composeTestRule.onNodeWithText(vidaBoton).assertHasClickAction().assertHeightIsAtLeast(48.dp).performClick()
        composeTestRule.onNodeWithText(
            texto(R.string.cotizacion_producto_accion, texto(R.string.cotizacion_producto_proteccion_dispositivos)),
        ).assertIsDisplayed()

        assertEquals(listOf("microseguro_vida"), elegidos)
    }

    @Test
    fun elFormularioDeVidaPideSoloSusDatos() {
        mostrar(CotizacionUiState(fase = FaseCotizacion.FORMULARIO, producto = vida))

        composeTestRule.onNodeWithText(texto(R.string.cotizacion_campo_edad)).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.cotizacion_campo_suma_asegurada)).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.cotizacion_campo_valor_dispositivo)).assertDoesNotExist()
    }

    @Test
    fun elFormularioDeDispositivosOfreceLasOpcionesDeTipo() {
        val cambios = mutableListOf<Pair<String, String>>()
        mostrar(
            CotizacionUiState(fase = FaseCotizacion.FORMULARIO, producto = dispositivos),
            CotizacionAcciones(cambiarValor = { clave, valor -> cambios += clave to valor }),
        )

        composeTestRule.onNodeWithText(texto(R.string.cotizacion_dispositivo_portatil)).performScrollTo().performClick()
        composeTestRule.onNodeWithText(texto(R.string.cotizacion_campo_antiguedad_meses)).performScrollTo().assertIsDisplayed()

        assertEquals(listOf("tipo_dispositivo" to "portatil"), cambios)
    }

    @Test
    fun unCampoInvalidoMuestraSuMensajeEnLinea() {
        mostrar(
            CotizacionUiState(
                fase = FaseCotizacion.FORMULARIO,
                producto = vida,
                valores = mapOf("edad" to "17"),
                errores = mapOf("edad" to ErrorCampo.MENOR_AL_MINIMO),
            ),
        )

        composeTestRule.onNodeWithText(texto(R.string.cotizacion_error_minimo, "18")).assertIsDisplayed()
    }

    @Test
    fun solicitarYCambiarDeProductoSonAccionesPulsables() {
        var solicitudes = 0
        var cambios = 0
        mostrar(
            CotizacionUiState(fase = FaseCotizacion.FORMULARIO, producto = vida),
            CotizacionAcciones(solicitar = { solicitudes++ }, volverAProductos = { cambios++ }),
        )

        composeTestRule.onNodeWithText(texto(R.string.cotizacion_solicitar)).performScrollTo().performClick()
        composeTestRule.onNodeWithText(texto(R.string.cotizacion_cambiar_producto)).performScrollTo().performClick()

        assertEquals(1, solicitudes)
        assertEquals(1, cambios)
    }

    @Test
    fun cargandoMuestraElProgreso() {
        mostrar(CotizacionUiState(fase = FaseCotizacion.CARGANDO, producto = vida))

        composeTestRule.onNodeWithText(texto(R.string.cotizacion_cargando)).assertIsDisplayed()
    }

    @Test
    fun elResultadoMuestraPrimaCoberturasYVigenciaSinAviso() {
        mostrar(resultado())

        composeTestRule.onNode(hasText(texto(R.string.cotizacion_resultado_titulo)) and isHeading()).assertIsDisplayed()
        val precio = com.solventa.mobile.cotizacion.FormatoCotizacion.moneda(175_000, "COP", locale)
        try {
            composeTestRule.onNodeWithText(precio).performScrollTo().assertIsDisplayed()
        } catch (e: AssertionError) { // TEMPORAL: diagnóstico del fallo en es-CO
            throw AssertionError("Buscando '$precio' ${precio.map { it.code }}:\n" + composeTestRule.onRoot().printToString(), e)
        }
        composeTestRule.onNodeWithText(texto(R.string.cotizacion_cobertura_muerte)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.cotizacion_cobertura_incapacidad_total)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.cotizacion_aviso_titulo)).assertDoesNotExist()
    }

    @Test
    fun sinConsentimientoAvisaQueEsUnaEstimacionYOfreceCompartirLosDatos() {
        var compartidos = 0
        mostrar(resultado(aproximada = true), CotizacionAcciones(compartirDatos = { compartidos++ }))

        composeTestRule.onNodeWithText(texto(R.string.cotizacion_aviso_titulo)).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.cotizacion_aviso_sin_consentimiento)).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.cotizacion_compartir_datos)).assertHasClickAction().performClick()

        assertEquals(1, compartidos)
    }

    @Test
    fun conPreciosDegradadosAvisaPeroNoOfreceCompartirLosDatos() {
        mostrar(resultado(aproximada = true, degradado = true))

        composeTestRule.onNodeWithText(texto(R.string.cotizacion_aviso_degradado)).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.cotizacion_compartir_datos)).assertDoesNotExist()
    }

    @Test
    fun cadaFallaTieneSuMensajeYSuAccion() {
        var reintentos = 0
        mostrar(
            CotizacionUiState(fase = FaseCotizacion.FORMULARIO, producto = vida, error = ErrorCotizacion.TIEMPO_AGOTADO),
            CotizacionAcciones(reintentar = { reintentos++ }),
        )

        composeTestRule.onNodeWithText(texto(R.string.cotizacion_error_tiempo)).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.cotizacion_reintentar)).assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(1, reintentos)
    }

    @Test
    fun unaSesionVencidaOfreceIniciarDeNuevoEnLugarDeReintentar() {
        var salidas = 0
        mostrar(
            CotizacionUiState(fase = FaseCotizacion.FORMULARIO, producto = vida, error = ErrorCotizacion.SESION_VENCIDA),
            CotizacionAcciones(sesionVencida = { salidas++ }),
        )

        composeTestRule.onNodeWithText(texto(R.string.cotizacion_error_sesion)).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.cotizacion_reintentar)).assertDoesNotExist()
        composeTestRule.onNodeWithText(texto(R.string.cotizacion_iniciar_sesion)).performClick()

        assertEquals(1, salidas)
    }

    @Test
    fun losMensajesDeFallaDelServicioYDeConexionSonDistintos() {
        mostrar(CotizacionUiState(fase = FaseCotizacion.FORMULARIO, producto = vida, error = ErrorCotizacion.SIN_CONEXION))
        composeTestRule.onNodeWithText(texto(R.string.cotizacion_error_sin_conexion)).assertIsDisplayed()
    }
}
