package com.solventa.mobile.ui.componentes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.solventa.mobile.ui.tema.extra

/** Tarjeta de contenido: fondo `surfaceContainer`, radio 16 dp y 16 dp de relleno. */
@Composable
fun Tarjeta(modifier: Modifier = Modifier, contenido: @Composable ColumnScope.() -> Unit) = Card(
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    modifier = modifier.fillMaxWidth(),
) { Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = contenido) }

/** Tarjeta con contorno, sin relleno (por ejemplo, la póliza activa). */
@Composable
fun TarjetaContorno(modifier: Modifier = Modifier, contenido: @Composable ColumnScope.() -> Unit) = Card(
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = modifier.fillMaxWidth(),
) { Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = contenido) }

/** Tarjeta destacada (precio, oferta): `primaryContainer` y radio de 28 dp. */
@Composable
fun TarjetaDestacada(modifier: Modifier = Modifier, contenido: @Composable ColumnScope.() -> Unit) = Card(
    shape = MaterialTheme.shapes.extraLarge,
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ),
    modifier = modifier.fillMaxWidth(),
) { Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = contenido) }

/** Una fila `etiqueta … valor` de 52 dp; las filas de una [ListaDetalle] se separan con un divisor. */
@Composable
fun FilaDetalle(etiqueta: String, valor: String, modifier: Modifier = Modifier) = Row(
    modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(etiqueta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
        valor,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        modifier = Modifier.weight(1f),
        textAlign = androidx.compose.ui.text.style.TextAlign.End,
    )
}

/** Tarjeta de filas [FilaDetalle] con divisores `outlineVariant`. */
@Composable
fun ListaDetalle(filas: List<Pair<String, String>>, modifier: Modifier = Modifier, titulo: String? = null) = Card(
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    modifier = modifier.fillMaxWidth(),
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        titulo?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
        }
        filas.forEachIndexed { i, (etiqueta, valor) ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            FilaDetalle(etiqueta, valor)
        }
    }
}

/** Círculo de color con un ícono de 24 dp (producto, resultado). */
@Composable
fun IconoCircular(icono: ImageVector, descripcion: String?, contenedor: Color, contenido: Color, tamano: Int = 48) = Surface(
    shape = CircleShape,
    color = contenedor,
    contentColor = contenido,
    modifier = Modifier.size(tamano.dp),
) {
    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(icono, contentDescription = descripcion, modifier = Modifier.size((tamano / 2).dp))
    }
}

/** Fila de aviso con ícono (información, éxito, advertencia). El color no es el único indicador: lleva ícono y texto. */
@Composable
fun Aviso(
    texto: String,
    icono: ImageVector,
    modifier: Modifier = Modifier,
    contenedor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contenido: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    colorIcono: Color = MaterialTheme.colorScheme.primary,
) = Surface(shape = MaterialTheme.shapes.medium, color = contenedor, contentColor = contenido, modifier = modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icono, contentDescription = null, tint = colorIcono, modifier = Modifier.size(24.dp))
        Text(texto, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun AvisoExito(texto: String, icono: ImageVector, modifier: Modifier = Modifier) = Aviso(
    texto, icono, modifier,
    contenedor = MaterialTheme.colorScheme.tertiaryContainer,
    contenido = MaterialTheme.colorScheme.onTertiaryContainer,
    colorIcono = MaterialTheme.colorScheme.onTertiaryContainer,
)

@Composable
fun AvisoAdvertencia(texto: String, icono: ImageVector, modifier: Modifier = Modifier) = Aviso(
    texto, icono, modifier,
    contenedor = MaterialTheme.extra.advertenciaContenedor,
    contenido = MaterialTheme.extra.onAdvertencia,
    colorIcono = MaterialTheme.extra.onAdvertencia,
)
