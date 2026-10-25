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

    /** Cámara: botón de captura. */
    val Camara: ImageVector by lazy {
        trazo("Camara", "M4 8h3l2-3h6l2 3h3v11H4z", "M15.5 13a3.5 3.5 0 1 1-7 0 3.5 3.5 0 0 1 7 0z")
    }

    /** Check de «verificado». */
    val Check: ImageVector by lazy { trazo("Check", "M5 12.5l4.5 4.5L19 7.5") }

    /** Signo de exclamación de «no se pudo». */
    val Exclamacion: ImageVector by lazy { trazo("Exclamacion", "M12 7v6M12 17h.01") }

    /** Círculo con una «i»: información. */
    val Informacion: ImageVector by lazy {
        trazo("Informacion", "M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0z", "M12 8v5M12 16h.01")
    }

    /** Sol: buena luz. */
    val Luz: ImageVector by lazy {
        trazo(
            "Luz",
            "M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0z",
            "M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6L7 7M17 17l1.4 1.4M5.6 18.4L7 17M17 7l1.4-1.4",
        )
    }

    /** Documento con renglones: sin reflejos ni sombras. */
    val Documento: ImageVector by lazy { trazo("Documento", "M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z", "M7 9h4M7 13h10") }

    /** Candado: privacidad de la imagen. */
    val Candado: ImageVector by lazy {
        trazo("Candado", "M7 11h10a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2z", "M8 11V8a4 4 0 0 1 8 0v3")
    }

    /** Tarjeta de crédito o débito. */
    val Tarjeta: ImageVector by lazy {
        trazo("Tarjeta", "M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z", "M3 10h18")
    }

    /** Inicio. */
    val Casa: ImageVector by lazy { trazo("Casa", "M4 11l8-7 8 7v9H4z") }

    /** Pólizas: escudo. */
    val Escudo: ImageVector by lazy { trazo("Escudo", "M12 3l8 3v6c0 4.5-3.4 8-8 9-4.6-1-8-4.5-8-9V6z") }

    /** Perfil. */
    val Persona: ImageVector by lazy { trazo("Persona", "M16 8a4 4 0 1 1-8 0 4 4 0 0 1 8 0z", "M4 21c1-4 4-6 8-6s7 2 8 6") }

    /** Advertencia de acciones destructivas. */
    val Advertencia: ImageVector by lazy { trazo("Advertencia", "M12 3l10 18H2z", "M12 10v5M12 18h.01") }

    /** Guion: estado «revocado». */
    val Menos: ImageVector by lazy { trazo("Menos", "M6 12h12") }

    /** «Ocultar contraseña»: el mismo ojo tachado. */
    val OjoTachado: ImageVector by lazy { trazo("OjoTachado", OJO, PUPILA, "M4 4l16 16") }
}
