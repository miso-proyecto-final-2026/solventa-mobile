package com.solventa.mobile.polizas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.solventa.mobile.R
import com.solventa.mobile.cotizacion.Catalogo
import com.solventa.mobile.red.PolizaResponse
import com.solventa.mobile.ui.componentes.BotonPrimario
import com.solventa.mobile.ui.componentes.BotonSecundario
import com.solventa.mobile.ui.componentes.ChipEstado
import com.solventa.mobile.ui.componentes.FormaChipSeleccion
import com.solventa.mobile.ui.componentes.Tarjeta
import com.solventa.mobile.ui.componentes.TipoEstado
import com.solventa.mobile.ui.iconos.Iconos

/** Lo que la lista puede pedirle al flujo; separado del ViewModel para probar la pantalla con estados fijos. */
data class PolizasAcciones(
    val elegirFiltro: (FiltroPolizas) -> Unit = {},
    val abrir: (PolizaResponse) -> Unit = {},
    val reintentar: () -> Unit = {},
    val cotizar: () -> Unit = {},
    val retomarFirma: (PolizaResponse) -> Unit = {},
)

@Composable
fun PolizasRoute(
    viewModel: PolizasViewModel,
    onCotizar: () -> Unit,
    onAbrirDetalle: () -> Unit,
    onRetomarFirma: (String) -> Unit,
    onSesionVencida: () -> Unit,
) {
    val estado by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.iniciar() }
    LaunchedEffect(estado.error) { if (estado.error == ErrorPolizas.SESION_VENCIDA) onSesionVencida() }
    PolizasPantalla(
        estado = estado,
        acciones = PolizasAcciones(
            elegirFiltro = viewModel::elegirFiltro,
            abrir = { viewModel.abrir(it); onAbrirDetalle() },
            reintentar = viewModel::recargar,
            cotizar = onCotizar,
            retomarFirma = { onRetomarFirma(it.suscripcionId) },
        ),
    )
}

/** Lista «Mis pólizas» (F1): pestañas Activas / Vencidas y una tarjeta por póliza. */
@Composable
fun PolizasPantalla(estado: PolizasUiState, acciones: PolizasAcciones) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.polizas_titulo),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp).semantics { heading() },
        )
        when (estado.fase) {
            FasePolizas.CARGANDO -> Cargando()
            FasePolizas.ERROR -> Fallo(estado.error, acciones.reintentar)
            FasePolizas.LISTA -> if (!estado.hayPolizas) VacioTotal(acciones.cotizar) else Lista(estado, acciones)
        }
    }
}

@Composable
private fun Cargando() {
    val descripcion = stringResource(R.string.polizas_cargando)
    Column(
        modifier = Modifier.fillMaxSize().semantics { liveRegion = LiveRegionMode.Polite }.testTag("polizas_cargando"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(descripcion, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun Fallo(error: ErrorPolizas?, reintentar: () -> Unit) = Column(
    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).semantics { liveRegion = LiveRegionMode.Polite }.testTag("polizas_error"),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
) {
    Icon(Iconos.Advertencia, contentDescription = null, tint = MaterialTheme.colorScheme.error)
    Text(
        stringResource(if (error == ErrorPolizas.SIN_CONEXION) R.string.polizas_error_conexion else R.string.polizas_error_servicio),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
    BotonPrimario(stringResource(R.string.polizas_reintentar), onClick = reintentar, modifier = Modifier.testTag("polizas_reintentar"))
}

/** El cliente aún no tiene ninguna póliza (criterio 5): estado vacío con acceso a cotizar. */
@Composable
private fun VacioTotal(cotizar: () -> Unit) = Column(
    modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp).testTag("polizas_vacio"),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
) {
    Icon(Iconos.Escudo, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    Text(stringResource(R.string.polizas_vacio_titulo), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Text(
        stringResource(R.string.polizas_vacio_mensaje),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    BotonPrimario(stringResource(R.string.polizas_cotizar), onClick = cotizar, modifier = Modifier.testTag("polizas_cotizar"))
}

@Composable
private fun Lista(estado: PolizasUiState, acciones: PolizasAcciones) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 24.dp)) {
            FiltroPolizas.entries.forEach { filtro ->
                FilterChip(
                    selected = estado.filtro == filtro,
                    onClick = { acciones.elegirFiltro(filtro) },
                    shape = FormaChipSeleccion,
                    label = {
                        Text(
                            stringResource(
                                if (filtro == FiltroPolizas.ACTIVAS) R.string.polizas_filtro_activas else R.string.polizas_filtro_vencidas,
                            ),
                        )
                    },
                    modifier = Modifier.testTag("filtro_${filtro.name.lowercase()}"),
                )
            }
        }
        val visibles = estado.visibles
        if (visibles.isEmpty()) {
            Text(
                stringResource(if (estado.filtro == FiltroPolizas.ACTIVAS) R.string.polizas_vacio_activas else R.string.polizas_vacio_vencidas),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp).testTag("polizas_vacio_filtro"),
            )
        } else {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                visibles.forEach { poliza -> TarjetaPoliza(poliza, estado.estadoDe(poliza), acciones) }
            }
        }
    }
}

@Composable
private fun TarjetaPoliza(poliza: PolizaResponse, estado: EstadoPoliza, acciones: PolizasAcciones) {
    val locale = LocalConfiguration.current.locales[0]
    val producto = Catalogo.porId(poliza.producto)?.let { stringResource(it.nombre) } ?: poliza.producto
    val estadoTexto = stringResource(estado.etiqueta)
    val descripcion = stringResource(R.string.polizas_abrir_detalle, producto, estadoTexto)
    Tarjeta(
        modifier = Modifier
            .testTag("poliza_${poliza.suscripcionId}")
            .clickable(onClickLabel = descripcion, role = Role.Button) { acciones.abrir(poliza) }
            .semantics(mergeDescendants = true) { },
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.polizas_numero, poliza.numeroPoliza), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            ChipEstado(estadoTexto, estado.icono, estado.tipoChip)
        }
        Text(producto, style = MaterialTheme.typography.titleLarge)
        Text(
            fechasDe(poliza, estado, locale),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (estado == EstadoPoliza.PENDIENTE_FIRMA) {
            BotonSecundario(
                stringResource(R.string.polizas_retomar_firma),
                onClick = { acciones.retomarFirma(poliza) },
                modifier = Modifier.testTag("retomar_${poliza.suscripcionId}"),
            )
        }
    }
}

/** La línea de la tarjeta: hasta cuándo vale, cuándo venció, o qué falta (firma pendiente, cobro devuelto). */
@Composable
internal fun fechasDe(poliza: PolizaResponse, estado: EstadoPoliza, locale: java.util.Locale): String = when (estado) {
    EstadoPoliza.VENCIDA -> stringResource(R.string.polizas_vencio, FormatoPolizas.fecha(poliza.fechaVencimiento, locale))
    EstadoPoliza.PENDIENTE_FIRMA -> stringResource(R.string.polizas_firma_pendiente_mensaje)
    EstadoPoliza.ANULADA -> stringResource(R.string.polizas_anulada_mensaje)
    else -> listOfNotNull(
        poliza.fechaEmision?.let { stringResource(R.string.polizas_emitida, FormatoPolizas.fecha(it, locale)) },
        stringResource(R.string.polizas_vigente_hasta, FormatoPolizas.fecha(poliza.fechaVencimiento, locale)),
    ).joinToString(" · ")
}

internal val EstadoPoliza.etiqueta: Int
    get() = when (this) {
        EstadoPoliza.VIGENTE -> R.string.polizas_estado_vigente
        EstadoPoliza.PROXIMA_A_VENCER -> R.string.polizas_estado_proxima
        EstadoPoliza.VENCIDA -> R.string.polizas_estado_vencida
        EstadoPoliza.PENDIENTE_FIRMA -> R.string.polizas_estado_pendiente_firma
        EstadoPoliza.ANULADA -> R.string.polizas_estado_anulada
    }

/** Ícono del chip: el color no es el único indicador. */
internal val EstadoPoliza.icono
    get() = when (this) {
        EstadoPoliza.VIGENTE -> Iconos.Check
        EstadoPoliza.PROXIMA_A_VENCER -> Iconos.Informacion
        EstadoPoliza.PENDIENTE_FIRMA -> Iconos.Advertencia
        EstadoPoliza.VENCIDA -> Iconos.Exclamacion
        EstadoPoliza.ANULADA -> Iconos.Menos
    }

/** Color del chip con el patrón de la app: verde vigente, azul próxima a vencer, ámbar firma pendiente, rojo vencida, gris anulada. */
internal val EstadoPoliza.tipoChip
    get() = when (this) {
        EstadoPoliza.VIGENTE -> TipoEstado.ACTIVO
        EstadoPoliza.PROXIMA_A_VENCER -> TipoEstado.INFO
        EstadoPoliza.PENDIENTE_FIRMA -> TipoEstado.ADVERTENCIA
        EstadoPoliza.VENCIDA -> TipoEstado.ERROR
        EstadoPoliza.ANULADA -> TipoEstado.NEUTRO
    }
