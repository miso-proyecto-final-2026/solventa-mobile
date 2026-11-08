package com.solventa.mobile.polizas

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.solventa.mobile.R
import com.solventa.mobile.ui.iconos.Iconos

/** La etiqueta en mayúsculas con ícono que encabeza la tarjeta de la póliza (diseño F1: «VIDA», «PARAMÉTRICO»…). */
internal data class CategoriaPoliza(@StringRes val etiqueta: Int, val icono: ImageVector)

internal fun categoriaDe(producto: String): CategoriaPoliza = when (producto) {
    "microseguro_vida" -> CategoriaPoliza(R.string.polizas_categoria_vida, Iconos.Casa)
    "proteccion_dispositivos" -> CategoriaPoliza(R.string.polizas_categoria_dispositivos, Iconos.Escudo)
    else -> CategoriaPoliza(R.string.polizas_categoria_otro, Iconos.Escudo)
}
