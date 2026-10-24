package com.solventa.mobile.ui.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.solventa.mobile.R
import com.solventa.mobile.ui.tema.extra

/** Estados que la lista, la tarjeta y el consentimiento comparten: color + ícono + texto, nunca solo color. */
enum class TipoEstado { ACTIVO, NEUTRO }

/** Chip de estado de 32 dp y radio 8 («Activo», «Revocado»). */
@Composable
fun ChipEstado(texto: String, icono: ImageVector, tipo: TipoEstado, modifier: Modifier = Modifier) {
    val (contenedor, contenido) = when (tipo) {
        TipoEstado.ACTIVO -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        TipoEstado.NEUTRO -> MaterialTheme.extra.chipInactivo to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = MaterialTheme.shapes.small, color = contenedor, contentColor = contenido, modifier = modifier) {
        Row(
            modifier = Modifier.height(32.dp).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icono, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(texto, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Fracción que se pinta de la barra: paso N de T, acotada a [0, 1]. */
internal fun fraccionDePasos(paso: Int, total: Int): Float =
    if (total <= 0) 0f else (paso.toFloat() / total).coerceIn(0f, 1f)

/** «Paso N de T · nombre» más una barra lineal de 4 dp. */
@Composable
fun IndicadorPasos(paso: Int, total: Int, nombre: String, modifier: Modifier = Modifier, progreso: Float = fraccionDePasos(paso, total)) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.ui_paso_de, paso, total, nombre),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { progreso },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.extra.pistaProgreso,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            modifier = Modifier.fillMaxWidth().height(4.dp),
        )
    }
}

/** Barra superior de 64 dp: «Volver» de 48 dp (con descripción para el lector de pantalla) y título de 22 sp. */
@Composable
fun BarraSuperior(titulo: String?, onAtras: (() -> Unit)?, modifier: Modifier = Modifier) = Row(
    modifier = modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
) {
    if (onAtras != null) {
        IconButton(onClick = onAtras, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ui_volver))
        }
    } else {
        Spacer(Modifier.size(width = 20.dp, height = 48.dp))
    }
    titulo?.let { Text(it, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }) }
}

/**
 * Pantalla de resultado: círculo de 112 dp con ícono, título, mensaje y un bloque opcional (lista de chequeos,
 * consejos). Se usa en verificación aprobada o rechazada y en el pago confirmado.
 */
@Composable
fun BloqueResultado(
    icono: ImageVector,
    descripcionIcono: String,
    contenedor: Color,
    colorIcono: Color,
    titulo: String,
    mensaje: String,
    modifier: Modifier = Modifier,
    contenido: @Composable () -> Unit = {},
) = Column(
    modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(20.dp),
) {
    Surface(shape = CircleShape, color = contenedor, modifier = Modifier.size(112.dp)) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icono, contentDescription = descripcionIcono, tint = colorIcono, modifier = Modifier.size(56.dp))
        }
    }
    Text(titulo, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
    Text(
        mensaje,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    contenido()
}

/** Forma de píldora/redondeada para elementos seleccionables con 8 dp de radio (chips de filtro). */
val FormaChipSeleccion = RoundedCornerShape(8.dp)
