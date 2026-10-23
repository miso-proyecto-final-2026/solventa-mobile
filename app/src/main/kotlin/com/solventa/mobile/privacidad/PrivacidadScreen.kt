package com.solventa.mobile.privacidad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.solventa.mobile.R
import com.solventa.mobile.red.ConsentimientoResponse
import java.util.Locale

/** Lo que la pantalla puede pedirle al flujo; separado del ViewModel para poder probarla con estados fijos. */
data class PrivacidadAcciones(
    val pedirRevocar: () -> Unit = {},
    val cancelar: () -> Unit = {},
    val confirmarRevocar: () -> Unit = {},
    val reintentar: () -> Unit = {},
    val compartirDatos: () -> Unit = {},
    val volver: () -> Unit = {},
    val sesionVencida: () -> Unit = {},
)

@Composable
fun PrivacidadRoute(
    viewModel: PrivacidadViewModel,
    onCompartirDatos: () -> Unit,
    onVolver: () -> Unit,
    onSesionVencida: () -> Unit,
) {
    val estado by viewModel.uiState.collectAsState()
    PrivacidadScreen(
        estado = estado,
        acciones = PrivacidadAcciones(
            pedirRevocar = viewModel::pedirRevocar,
            cancelar = viewModel::cancelar,
            confirmarRevocar = viewModel::confirmarRevocar,
            reintentar = viewModel::reintentar,
            compartirDatos = onCompartirDatos,
            volver = onVolver,
            sesionVencida = onSesionVencida,
        ),
    )
}

@Composable
fun PrivacidadScreen(estado: PrivacidadUiState, acciones: PrivacidadAcciones) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.privacidad_titulo),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        when (estado.fase) {
            FasePrivacidad.CARGANDO -> Progreso(R.string.privacidad_cargando)
            FasePrivacidad.REVOCANDO -> Progreso(R.string.privacidad_revocando)
            FasePrivacidad.ESTADO, FasePrivacidad.CONFIRMANDO -> Contenido(estado, acciones)
        }
        OutlinedButton(
            onClick = acciones.volver,
            enabled = estado.fase != FasePrivacidad.REVOCANDO,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.privacidad_volver)) }
    }
    if (estado.fase == FasePrivacidad.CONFIRMANDO) Confirmacion(acciones)
}

@Composable
private fun Progreso(texto: Int) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(texto),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun Contenido(estado: PrivacidadUiState, acciones: PrivacidadAcciones) {
    estado.error?.let { Falla(it, estado.pendiente, acciones) }
    if (estado.recienRevocado) {
        Text(
            text = stringResource(R.string.privacidad_revocado_ok),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    val consentimiento = estado.consentimiento ?: return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.privacidad_consentimiento_titulo),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(textoDeEstado(consentimiento))
            Text(stringResource(R.string.privacidad_finalidad), style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (estado.puedeRevocar) {
        Button(onClick = acciones.pedirRevocar, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.privacidad_revocar))
        }
    }
    if (estado.puedeCompartir) {
        Button(onClick = acciones.compartirDatos, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.privacidad_compartir))
        }
    }
}

@Composable
private fun textoDeEstado(consentimiento: ConsentimientoResponse): String {
    val locale: Locale = LocalConfiguration.current.locales[0]
    return when (consentimiento.estado) {
        ESTADO_OTORGADO -> stringResource(
            R.string.privacidad_estado_otorgado,
            consentimiento.fechaOtorgamiento?.let { FormatoFecha.fechaHora(it, locale) }.orEmpty(),
        )
        ESTADO_REVOCADO -> stringResource(
            R.string.privacidad_estado_revocado,
            consentimiento.fechaRevocacion?.let { FormatoFecha.fechaHora(it, locale) }.orEmpty(),
        )
        else -> stringResource(R.string.privacidad_estado_no_otorgado)
    }
}

@Composable
private fun Falla(error: ErrorPrivacidad, pendiente: AccionPrivacidad?, acciones: PrivacidadAcciones) {
    val mensaje = when (error) {
        ErrorPrivacidad.TIEMPO_AGOTADO -> R.string.privacidad_error_tiempo
        ErrorPrivacidad.SIN_CONEXION -> R.string.privacidad_error_sin_conexion
        ErrorPrivacidad.SERVICIO -> R.string.privacidad_error_servicio
        ErrorPrivacidad.SESION_VENCIDA -> R.string.privacidad_error_sesion
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive },
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(mensaje), color = MaterialTheme.colorScheme.onErrorContainer)
            // Si la revocación no se confirmó, el consentimiento se sigue mostrando vigente y se avisa.
            if (pendiente == AccionPrivacidad.REVOCAR) {
                Text(stringResource(R.string.privacidad_sigue_vigente), color = MaterialTheme.colorScheme.onErrorContainer)
            }
            if (error == ErrorPrivacidad.SESION_VENCIDA) {
                Button(onClick = acciones.sesionVencida, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.privacidad_iniciar_sesion))
                }
            } else {
                Button(onClick = acciones.reintentar, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.privacidad_reintentar))
                }
            }
        }
    }
}

@Composable
private fun Confirmacion(acciones: PrivacidadAcciones) {
    AlertDialog(
        onDismissRequest = acciones.cancelar,
        title = { Text(stringResource(R.string.privacidad_confirmar_titulo)) },
        // Desplazable: en una pantalla pequeña o con fuente grande el texto largo no debe tapar el título ni los botones.
        text = { Text(stringResource(R.string.privacidad_confirmar_texto), modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = {
            TextButton(onClick = acciones.confirmarRevocar, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.privacidad_confirmar_si))
            }
        },
        dismissButton = {
            TextButton(onClick = acciones.cancelar, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.privacidad_confirmar_no))
            }
        },
    )
}
