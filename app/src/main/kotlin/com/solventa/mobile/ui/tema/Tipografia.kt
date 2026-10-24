package com.solventa.mobile.ui.tema

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Roboto es la fuente del sistema en Android: `FontFamily.Default` evita empaquetar archivos de fuente. */
private val Roboto = FontFamily.Default

private fun estilo(tamano: Int, linea: Int, peso: FontWeight = FontWeight.Normal, espaciado: Double = 0.0) =
    TextStyle(fontFamily = Roboto, fontSize = tamano.sp, lineHeight = linea.sp, fontWeight = peso, letterSpacing = espaciado.sp)

/** headline 28/36 (400), title 22/28, body 16/24, body-small 14/20, label 12 (500, .5). */
val SolventaTipografia = Typography(
    headlineMedium = estilo(28, 36),
    headlineSmall = estilo(24, 32),
    titleLarge = estilo(22, 28),
    titleMedium = estilo(16, 24, FontWeight.Medium, 0.15),
    titleSmall = estilo(14, 20, FontWeight.Medium, 0.1),
    bodyLarge = estilo(16, 24, espaciado = 0.5),
    bodyMedium = estilo(14, 20, espaciado = 0.25),
    bodySmall = estilo(12, 16, espaciado = 0.4),
    labelLarge = estilo(14, 20, FontWeight.Medium, 0.1),
    labelMedium = estilo(12, 16, FontWeight.Medium, 0.5),
    labelSmall = estilo(12, 16, FontWeight.Medium, 0.5),
)
