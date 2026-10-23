package com.solventa.mobile.navegacion

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.solventa.mobile.R

/**
 * Barra inferior con las dos secciones de la app: seguros (cotización) y configuración. Cada pestaña lleva texto,
 * así que el icono es decorativo para el lector de pantalla, que anuncia el nombre y si está seleccionada.
 */
@Composable
fun BarraPrincipal(seleccion: Seccion, alElegir: (Seccion) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = seleccion == Seccion.SEGUROS,
            onClick = { alElegir(Seccion.SEGUROS) },
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_seguros)) },
        )
        NavigationBarItem(
            selected = seleccion == Seccion.CONFIGURACION,
            onClick = { alElegir(Seccion.CONFIGURACION) },
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_configuracion)) },
        )
    }
}
