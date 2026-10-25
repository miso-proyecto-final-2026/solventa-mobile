package com.solventa.mobile.navegacion

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.solventa.mobile.R
import com.solventa.mobile.ui.iconos.Iconos

/**
 * Barra inferior con los tres destinos del diseño: Inicio (seguros), Pólizas y Perfil. «Pólizas» (HU35) aún no
 * existe y va deshabilitada, para que el diseño final ya esté en su sitio. Cada pestaña lleva texto, así que el icono
 * es decorativo para el lector de pantalla, que anuncia el nombre, si está seleccionada y si no está disponible.
 */
@Composable
fun BarraPrincipal(seleccion: Seccion, alElegir: (Seccion) -> Unit) {
    val colores = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedTextColor = MaterialTheme.colorScheme.onSurface,
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        NavigationBarItem(
            selected = seleccion == Seccion.SEGUROS,
            onClick = { alElegir(Seccion.SEGUROS) },
            icon = { Icon(Iconos.Casa, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_seguros)) },
            colors = colores,
        )
        NavigationBarItem(
            selected = false,
            onClick = {},
            enabled = false,
            icon = { Icon(Iconos.Escudo, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_polizas)) },
            colors = colores,
        )
        NavigationBarItem(
            selected = seleccion == Seccion.PERFIL,
            onClick = { alElegir(Seccion.PERFIL) },
            icon = { Icon(Iconos.Persona, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_perfil)) },
            colors = colores,
        )
    }
}
