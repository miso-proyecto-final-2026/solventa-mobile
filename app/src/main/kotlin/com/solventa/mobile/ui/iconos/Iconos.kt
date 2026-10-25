package com.solventa.mobile.ui.iconos

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color

/**
 * Íconos del diseño: 24 dp, trazo de 2, sin relleno y con extremos redondeados. Se dibujan aquí con los mismos
 * trazos del handoff en vez de sumar la librería `material-icons-extended` (varios MB) para un puñado de íconos.
 * Se pintan con el color del contenido (`LocalContentColor` / `tint`).
 */
object Iconos {

    private fun trazo(nombre: String, vararg trazos: String): ImageVector =
        ImageVector.Builder(nombre, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                trazos.forEach {
                    addPath(
                        pathData = addPathNodes(it),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 2f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
            .build()

    private const val OJO = "M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z"
    private const val PUPILA = "M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0z"

    /** «Mostrar contraseña». */
    val Ojo: ImageVector by lazy { trazo("Ojo", OJO, PUPILA) }

    /** «Ocultar contraseña»: el mismo ojo tachado. */
    val OjoTachado: ImageVector by lazy { trazo("OjoTachado", OJO, PUPILA, "M4 4l16 16") }
}
