package com.solventa.mobile.ui.tema

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/** Campos 4 dp, chips 8, tarjetas 16, tarjeta destacada y diálogos 28. Los botones son píldoras de 24 dp. */
val SolventaFormas = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val LocalColoresExtra = staticCompositionLocalOf { ColoresExtra() }

/** Colores del diseño que no están en `MaterialTheme.colorScheme`. */
val MaterialTheme.extra: ColoresExtra
    @Composable get() = LocalColoresExtra.current

/** Un solo tema claro; aunque el sistema esté en modo oscuro la app mantiene el diseño aprobado. */
@Composable
fun SolventaTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalColoresExtra provides ColoresExtra()) {
        MaterialTheme(
            colorScheme = SolventaColorScheme,
            typography = SolventaTipografia,
            shapes = SolventaFormas,
            content = content,
        )
    }
}
