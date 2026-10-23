package com.solventa.mobile

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import java.util.Locale

/**
 * Monta el contenido en es-CO sea cual sea el idioma del dispositivo. Las pruebas que buscan textos en español
 * (literales) lo necesitan: el emulador del CI, y a veces el local, está en en-US.
 */
fun ComposeContentTestRule.setContentEnEspanol(contenido: @Composable () -> Unit) {
    setContent {
        val base = LocalContext.current
        val configuracion = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag("es-CO")) }
        CompositionLocalProvider(
            LocalContext provides base.createConfigurationContext(configuracion),
            LocalConfiguration provides configuracion,
        ) { contenido() }
    }
}
