package com.solventa.mobile.ui.componentes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Altura 48 dp (también el área táctil mínima) y forma de píldora de 24 dp. */
private val AlturaBoton = 48.dp
private val FormaBoton = RoundedCornerShape(24.dp)

@Composable
private fun Contenido(texto: String, icono: ImageVector?) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        // Decorativo: el texto del botón ya dice qué hace.
        icono?.let { Icon(it, contentDescription = null, modifier = Modifier.size(20.dp)) }
        Text(texto, style = MaterialTheme.typography.labelLarge)
    }
}

/** Acción principal de la pantalla: relleno. */
@Composable
fun BotonPrimario(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    icono: ImageVector? = null,
) = Button(
    onClick = onClick,
    enabled = habilitado,
    shape = FormaBoton,
    modifier = modifier.fillMaxWidth().heightIn(min = AlturaBoton),
) { Contenido(texto, icono) }

/** Acción secundaria: solo texto. */
@Composable
fun BotonSecundario(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, habilitado: Boolean = true) =
    TextButton(
        onClick = onClick,
        enabled = habilitado,
        shape = FormaBoton,
        modifier = modifier.fillMaxWidth().heightIn(min = AlturaBoton),
    ) { Contenido(texto, null) }

/** Acción destructiva confirmada (por ejemplo, revocar): relleno rojo. */
@Composable
fun BotonDestructivo(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, habilitado: Boolean = true) =
    Button(
        onClick = onClick,
        enabled = habilitado,
        shape = FormaBoton,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        modifier = modifier.heightIn(min = AlturaBoton),
    ) { Contenido(texto, null) }

/** Acción destructiva aún sin confirmar: contorno y texto rojos. */
@Composable
fun BotonDestructivoContorno(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier) =
    OutlinedButton(
        onClick = onClick,
        shape = FormaBoton,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = modifier.heightIn(min = AlturaBoton),
    ) { Contenido(texto, null) }

/** Acción suave sobre un tono del tema (por ejemplo, «Volver a autorizar»). */
@Composable
fun BotonTonal(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier) = Button(
    onClick = onClick,
    shape = FormaBoton,
    colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ),
    modifier = modifier.heightIn(min = AlturaBoton),
) { Contenido(texto, null) }
