package com.solventa.mobile.ui.componentes

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * `FLAG_SECURE` mientras la pantalla está visible: no se puede capturar ni aparece en la vista de aplicaciones
 * recientes (HU05, criterio 8). Se usa en el bloqueo biométrico y en el inicio de sesión.
 */
@Composable
fun ProtegerPantalla() {
    val ventana = (LocalContext.current as? Activity)?.window
    DisposableEffect(ventana) {
        ventana?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { ventana?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}
