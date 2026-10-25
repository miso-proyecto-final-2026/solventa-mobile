package com.solventa.mobile.privacidad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.solventa.mobile.ui.componentes.AvisoExito
import com.solventa.mobile.ui.componentes.BarraSuperior
import com.solventa.mobile.ui.componentes.BotonDestructivo
import com.solventa.mobile.ui.componentes.BotonDestructivoContorno
import com.solventa.mobile.ui.componentes.BotonPrimario
import com.solventa.mobile.ui.componentes.BotonTonal
import com.solventa.mobile.ui.componentes.ChipEstado
import com.solventa.mobile.ui.componentes.Tarjeta
import com.solventa.mobile.ui.componentes.TipoEstado
import com.solventa.mobile.ui.iconos.Iconos
import java.util.Locale

/** Lo que la pantalla puede pedirle al flujo; separado del ViewModel para poder probarla con estados fijos. */
data class PrivacidadAcciones(
    val pedirRevocar: () -> Unit = {},
    val cancelar: () -> Unit = {},
    val confirmarRevocar: () -> Unit = {},
    val reintentar: () -> Unit = {},
    val compartirDatos: () -> Unit = {},
    val sesionVencida: () -> Unit = {},
)

@Composable
fun PrivacidadRoute(
    viewModel: PrivacidadViewModel,
    onCompartirDatos: () -> Unit,
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
            sesionVencida = onSesionVencida,
        ),
    )
}

/**
 * Mis consentimientos (HU06/HU07, C2 del diseño): es la raíz de la pestaña «Perfil». Un solo consentimiento
 * (`OPEN_FINANCE_OPEN_DATA`), con su estado, la fecha y la acción de revocar o volver a autorizar.
 */
@Composable
fun PrivacidadScreen(estado: PrivacidadUiState, acciones: PrivacidadAcciones) {
    Column(modifier = Modifier.fillMaxSize()) {
        BarraSuperior(titulo = stringResource(R.string.privacidad_titulo), onAtras = null)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.privacidad_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (estado.fase) {
                FasePrivacidad.CARGANDO -> Progreso(R.string.privacidad_cargando)
                FasePrivacidad.REVOCANDO -> Progreso(R.string.privacidad_revocando)
                FasePrivacidad.ESTADO, FasePrivacidad.CONFIRMANDO -> Contenido(estado, acciones)
            }
        }
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
        AvisoExito(
            texto = stringResource(R.string.privacidad_revocado_ok),
            icono = Iconos.Check,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    val consentimiento = estado.consentimiento ?: return
    Tarjeta {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.privacidad_consentimiento_titulo),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.privacidad_finalidad),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Estado(consentimiento)
        }
        Text(
            text = textoDeEstado(consentimiento),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (estado.puedeRevocar) {
            BotonDestructivoContorno(stringResource(R.string.privacidad_revocar), onClick = acciones.pedirRevocar)
        }
        if (estado.puedeCompartir) {
            val revocado = consentimiento.estado == ESTADO_REVOCADO
            BotonTonal(
                stringResource(if (revocado) R.string.privacidad_compartir else R.string.privacidad_autorizar),
                onClick = acciones.compartirDatos,
            )
        }
    }
}

/** Estado en un chip: color, ícono y texto, nunca solo color. */
@Composable
private fun Estado(consentimiento: ConsentimientoResponse) {
    when (consentimiento.estado) {
        ESTADO_OTORGADO -> ChipEstado(stringResource(R.string.privacidad_chip_activo), Iconos.Check, TipoEstado.ACTIVO)
        ESTADO_REVOCADO -> ChipEstado(stringResource(R.string.privacidad_chip_revocado), Iconos.Menos, TipoEstado.NEUTRO)
        else -> ChipEstado(stringResource(R.string.privacidad_chip_sin_otorgar), Iconos.Menos, TipoEstado.NEUTRO)
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
    androidx.compose.material3.Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive },
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(mensaje))
            // Si la revocación no se confirmó, el consentimiento se sigue mostrando vigente y se avisa.
            if (pendiente == AccionPrivacidad.REVOCAR) {
                Text(stringResource(R.string.privacidad_sigue_vigente))
            }
            if (error == ErrorPrivacidad.SESION_VENCIDA) {
                BotonPrimario(stringResource(R.string.privacidad_iniciar_sesion), onClick = acciones.sesionVencida)
            } else {
                BotonPrimario(stringResource(R.string.privacidad_reintentar), onClick = acciones.reintentar)
            }
        }
    }
}

/** Diálogo de confirmación (C3): advertencia en rojo, consecuencias explícitas y «Revocar» como única acción roja. */
@Composable
private fun Confirmacion(acciones: PrivacidadAcciones) {
    AlertDialog(
        onDismissRequest = acciones.cancelar,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        icon = {
            Icon(
                Iconos.Advertencia,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp),
            )
        },
        title = { Text(stringResource(R.string.privacidad_confirmar_titulo)) },
        // Desplazable: en una pantalla pequeña o con fuente grande el texto largo no debe tapar el título ni los botones.
        text = {
            Text(
                stringResource(R.string.privacidad_confirmar_texto),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { BotonDestructivo(stringResource(R.string.privacidad_confirmar_si), onClick = acciones.confirmarRevocar) },
        dismissButton = {
            TextButton(onClick = acciones.cancelar, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.privacidad_confirmar_no), style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}
