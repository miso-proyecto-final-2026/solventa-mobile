package com.solventa.mobile.consentimiento

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.solventa.mobile.R
import com.solventa.mobile.ui.componentes.BarraSuperior
import com.solventa.mobile.ui.componentes.BloqueResultado
import com.solventa.mobile.ui.componentes.BotonPrimario
import com.solventa.mobile.ui.componentes.BotonSecundario
import com.solventa.mobile.ui.componentes.Tarjeta
import com.solventa.mobile.ui.iconos.Iconos
import com.solventa.mobile.ui.tema.SolventaColorScheme

/**
 * Otorgar consentimiento (HU06, C1 del diseño). Open Finance y Open Data se autorizan **juntos**: el backend tiene
 * un solo consentimiento (`OPEN_FINANCE_OPEN_DATA`), así que las dos tarjetas informan qué cubre y no son
 * interruptores independientes. Los textos legales siguen siendo los de la versión `v1`.
 */
@Composable
fun ConsentimientoScreen(
    viewModel: ConsentimientoViewModel,
    onContinuar: () -> Unit,
) {
    val estado by viewModel.uiState.collectAsState()

    LaunchedEffect(estado.continuar) {
        if (estado.continuar) onContinuar()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        when (val decision = estado.decision) {
            null -> Informacion(estado, viewModel)
            is DecisionConsentimiento.Otorgado -> Confirmacion(
                exito = true,
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
                exito = false,
                titulo = stringResource(R.string.consentimiento_rechazado_titulo),
                mensaje = stringResource(R.string.consentimiento_rechazado_mensaje),
                detalle = null,
                onContinuar = viewModel::continuar,
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Informacion(
    estado: ConsentimientoUiState,
    viewModel: ConsentimientoViewModel,
) {
    // Es un paso del alta: no hay pantalla anterior a la que volver.
    BarraSuperior(titulo = stringResource(R.string.consentimiento_barra), onAtras = null)

    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.consentimiento_titulo),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.consentimiento_apoyo),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Qué cubre la autorización: se da junta, no por separado.
        FuenteDeDatos(R.string.consentimiento_open_finance, R.string.consentimiento_open_finance_detalle)
        FuenteDeDatos(R.string.consentimiento_open_data, R.string.consentimiento_open_data_detalle)

        // Información previa a la acción (criterio 1): finalidad, datos, fuentes, vigencia y revocación (texto v1).
        Tarjeta {
            Seccion(R.string.consentimiento_finalidad_titulo, R.string.consentimiento_finalidad_v1)
            Seccion(R.string.consentimiento_datos_titulo, R.string.consentimiento_datos_v1)
            Seccion(R.string.consentimiento_fuentes_titulo, R.string.consentimiento_fuentes_v1)
            Seccion(R.string.consentimiento_vigencia_titulo, R.string.consentimiento_vigencia_v1)
            Seccion(R.string.consentimiento_revocar_titulo, R.string.consentimiento_revocar_v1)
        }

        BotonSecundario(
            texto = stringResource(
                if (estado.detalleExpandido) R.string.consentimiento_ocultar_detalle
                else R.string.consentimiento_ver_detalle,
            ),
            onClick = viewModel::alternarDetalle,
        )
        if (estado.detalleExpandido) {
            // El detalle legal se muestra completo; el scroll de la pantalla no acepta nada por sí solo.
            Text(
                text = stringResource(R.string.consentimiento_detalle_legal_v1),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        estado.mensajeError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        if (estado.cargando) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.consentimiento_cargando))
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val aceptarDescripcion = stringResource(R.string.consentimiento_aceptar_descripcion)
        BotonPrimario(
            texto = stringResource(R.string.consentimiento_aceptar),
            onClick = viewModel::aceptar,
            habilitado = !estado.cargando,
            modifier = Modifier.semantics { contentDescription = aceptarDescripcion },
        )
        BotonSecundario(
            texto = stringResource(R.string.consentimiento_rechazar),
            onClick = viewModel::rechazar,
            habilitado = !estado.cargando,
        )
    }
}

@Composable
private fun FuenteDeDatos(titulo: Int, detalle: Int) {
    Tarjeta {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(titulo), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(detalle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Seccion(titulo: Int, texto: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(titulo),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(texto),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Confirmacion(
    exito: Boolean,
    titulo: String,
    mensaje: String,
    detalle: String?,
    onContinuar: () -> Unit,
) {
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BloqueResultado(
            icono = if (exito) Iconos.Check else Iconos.Informacion,
            descripcionIcono = stringResource(if (exito) R.string.resultado_icono_exito else R.string.resultado_icono_info),
            contenedor = if (exito) SolventaColorScheme.tertiaryContainer else SolventaColorScheme.primaryContainer,
            colorIcono = if (exito) SolventaColorScheme.onTertiaryContainer else SolventaColorScheme.onPrimaryContainer,
            titulo = titulo,
            mensaje = mensaje,
        ) {
            detalle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
        BotonPrimario(stringResource(R.string.consentimiento_continuar), onClick = onContinuar)
    }
}
