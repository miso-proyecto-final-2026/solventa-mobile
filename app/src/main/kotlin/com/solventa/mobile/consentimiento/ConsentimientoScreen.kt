package com.solventa.mobile.consentimiento

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.solventa.mobile.R

/** Tamaño mínimo de objetivo táctil recomendado por Material/Android. */
private val ObjetivoTactil = 48.dp

@Composable
fun ConsentimientoScreen(
    viewModel: ConsentimientoViewModel,
    onContinuar: () -> Unit,
) {
    val estado by viewModel.uiState.collectAsState()

    LaunchedEffect(estado.continuar) {
        if (estado.continuar) onContinuar()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (val decision = estado.decision) {
            null -> Informacion(estado, viewModel)
            is DecisionConsentimiento.Otorgado -> Confirmacion(
                titulo = stringResource(R.string.consentimiento_otorgado_titulo),
                mensaje = stringResource(R.string.consentimiento_otorgado_mensaje),
                detalle = stringResource(
                    R.string.consentimiento_otorgado_detalle,
                    decision.versionTexto,
                    decision.fechaOtorgamiento,
                ),
                onContinuar = viewModel::continuar,
            )

            DecisionConsentimiento.Rechazado -> Confirmacion(
                titulo = stringResource(R.string.consentimiento_rechazado_titulo),
                mensaje = stringResource(R.string.consentimiento_rechazado_mensaje),
                detalle = null,
                onContinuar = viewModel::continuar,
            )
        }
    }
}

@Composable
private fun Informacion(estado: ConsentimientoUiState, viewModel: ConsentimientoViewModel) {
    Text(
        text = stringResource(R.string.consentimiento_titulo),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.semantics { heading() },
    )
    // Información previa a la acción (criterio 1): finalidad, datos, fuentes, vigencia y revocación.
    Seccion(R.string.consentimiento_finalidad_titulo, R.string.consentimiento_finalidad_v1)
    Seccion(R.string.consentimiento_datos_titulo, R.string.consentimiento_datos_v1)
    Seccion(R.string.consentimiento_fuentes_titulo, R.string.consentimiento_fuentes_v1)
    Seccion(R.string.consentimiento_vigencia_titulo, R.string.consentimiento_vigencia_v1)
    Seccion(R.string.consentimiento_revocar_titulo, R.string.consentimiento_revocar_v1)

    TextButton(
        onClick = viewModel::alternarDetalle,
        modifier = Modifier.heightIn(min = ObjetivoTactil),
    ) {
        Text(
            stringResource(
                if (estado.detalleExpandido) R.string.consentimiento_ocultar_detalle
                else R.string.consentimiento_ver_detalle,
            ),
        )
    }
    if (estado.detalleExpandido) {
        // El detalle legal se muestra completo; el scroll de la pantalla no acepta nada por sí solo.
        Text(
            text = stringResource(R.string.consentimiento_detalle_legal_v1),
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    estado.mensajeError?.let {
        Text(text = it, color = MaterialTheme.colorScheme.error)
    }

    if (estado.cargando) {
        CircularProgressIndicator()
        Text(stringResource(R.string.consentimiento_cargando))
    }

    val aceptarDescripcion = stringResource(R.string.consentimiento_aceptar_descripcion)
    Button(
        onClick = viewModel::aceptar,
        enabled = !estado.cargando,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ObjetivoTactil)
            .semantics { contentDescription = aceptarDescripcion },
    ) {
        Text(stringResource(R.string.consentimiento_aceptar))
    }
    OutlinedButton(
        onClick = viewModel::rechazar,
        enabled = !estado.cargando,
        modifier = Modifier.fillMaxWidth().heightIn(min = ObjetivoTactil),
    ) {
        Text(stringResource(R.string.consentimiento_rechazar))
    }
}

@Composable
private fun Seccion(titulo: Int, texto: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(titulo),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(text = stringResource(texto), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Confirmacion(
    titulo: String,
    mensaje: String,
    detalle: String?,
    onContinuar: () -> Unit,
) {
    Text(
        text = titulo,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.semantics { heading() },
    )
    Text(text = mensaje)
    detalle?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }
    Button(
        onClick = onContinuar,
        modifier = Modifier.fillMaxWidth().heightIn(min = ObjetivoTactil),
    ) {
        Text(stringResource(R.string.consentimiento_continuar))
    }
}
