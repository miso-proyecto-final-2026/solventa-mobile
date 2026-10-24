package com.solventa.mobile.ui.tema

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Tokens del diseño Material 3 de Solventa (un solo tema claro). No cambiar sin revisar el contraste (ContrasteTest). */
internal val Primary = Color(0xFF00607A)
internal val OnPrimary = Color(0xFFFFFFFF)
internal val PrimaryContainer = Color(0xFFBEE9FF)
internal val OnPrimaryContainer = Color(0xFF001F2A)
internal val SecondaryContainer = Color(0xFFD2E6EF)
internal val OnSecondaryContainer = Color(0xFF0B2A36)
internal val Fondo = Color(0xFFF6FAFC)
internal val SurfaceContainer = Color(0xFFEBF1F4)
internal val OnSurface = Color(0xFF171C1F)
internal val OnSurfaceVariant = Color(0xFF40484C)
internal val Outline = Color(0xFF70787D)
internal val OutlineVariant = Color(0xFFC7D3D9)
internal val Error = Color(0xFFBA1A1A)
internal val ErrorContainer = Color(0xFFFFDAD6)
internal val OnErrorContainer = Color(0xFF7A0F0B)
internal val Exito = Color(0xFFB1F1BC)
internal val OnExito = Color(0xFF0B3A1B)
internal val Scrim = Color(0x6B171C1F) // rgba(23,28,31,.42)

val SolventaColorScheme: ColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Primary,
    onSecondary = OnPrimary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = OnExito,
    onTertiary = OnPrimary,
    tertiaryContainer = Exito,
    onTertiaryContainer = OnExito,
    background = Fondo,
    onBackground = OnSurface,
    surface = Fondo,
    onSurface = OnSurface,
    surfaceVariant = SurfaceContainer,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceContainer = SurfaceContainer,
    surfaceContainerLow = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainer,
    outline = Outline,
    outlineVariant = OutlineVariant,
    error = Error,
    onError = OnPrimary,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    scrim = Scrim,
)

/** Colores del diseño que Material 3 no define: advertencia, pista del progreso e ícono de éxito. */
data class ColoresExtra(
    val advertenciaContenedor: Color = Color(0xFFFFDDB3),
    val onAdvertencia: Color = Color(0xFF2B1700),
    val exitoIcono: Color = Color(0xFF2E6A3E),
    val pistaProgreso: Color = Color(0xFFDDE4E8),
    val chipInactivo: Color = Color(0xFFE3E9EC),
)
