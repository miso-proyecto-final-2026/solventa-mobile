package com.solventa.mobile.navegacion

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
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.solventa.mobile.R
import com.solventa.mobile.configuracion.ConfiguracionScreen
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Barra inferior y pantalla de configuración, en es-CO y en-US sea cual sea el idioma del dispositivo. */
@RunWith(Parameterized::class)
class NavegacionPrincipalTest(private val idioma: String) {

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

    private fun texto(id: Int) = contexto.getString(id)

    private fun mostrar(contenido: @androidx.compose.runtime.Composable () -> Unit) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalContext provides contexto, LocalConfiguration provides configuracion) {
                MaterialTheme { contenido() }
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun la_barra_muestra_seguros_y_configuracion_con_la_seleccionada_marcada() {
        mostrar { BarraPrincipal(seleccion = Seccion.SEGUROS, alElegir = {}) }

        composeTestRule.onNodeWithText(texto(R.string.nav_seguros)).assertIsDisplayed().assertIsSelected()
        composeTestRule.onNodeWithText(texto(R.string.nav_configuracion)).assertIsDisplayed().assertIsNotSelected()
    }

    @Test
    fun la_seleccion_sigue_a_la_seccion() {
        mostrar { BarraPrincipal(seleccion = Seccion.CONFIGURACION, alElegir = {}) }

        composeTestRule.onNodeWithText(texto(R.string.nav_configuracion)).assertIsSelected()
        composeTestRule.onNodeWithText(texto(R.string.nav_seguros)).assertIsNotSelected()
    }

    @Test
    fun tocar_una_pestana_avisa_cual_se_eligio() {
        val elegidas = mutableListOf<Seccion>()
        mostrar { BarraPrincipal(seleccion = Seccion.SEGUROS, alElegir = { elegidas += it }) }

        composeTestRule.onNodeWithText(texto(R.string.nav_configuracion)).performClick()
        composeTestRule.onNodeWithText(texto(R.string.nav_seguros)).performClick()

        assertEquals(listOf(Seccion.CONFIGURACION, Seccion.SEGUROS), elegidas)
    }

    @Test
    fun las_pestanas_miden_al_menos_48dp() {
        mostrar { BarraPrincipal(seleccion = Seccion.SEGUROS, alElegir = {}) }

        composeTestRule.onNodeWithText(texto(R.string.nav_seguros)).assertHeightIsAtLeast(48.dp)
        composeTestRule.onNodeWithText(texto(R.string.nav_configuracion)).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun configuracion_ofrece_privacidad_y_la_abre() {
        var abierta = 0
        mostrar { ConfiguracionScreen(alAbrirPrivacidad = { abierta++ }) }

        composeTestRule.onNode(isHeading() and hasText(texto(R.string.configuracion_titulo))).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.configuracion_privacidad_detalle)).assertIsDisplayed()
        composeTestRule.onNodeWithText(texto(R.string.privacidad_abrir)).assertHasClickAction().assertHeightIsAtLeast(48.dp)
        composeTestRule.onNodeWithText(texto(R.string.privacidad_abrir)).performClick()

        assertEquals(1, abierta)
    }
}
