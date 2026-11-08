package com.solventa.mobile.polizas

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.solventa.mobile.R
import com.solventa.mobile.cotizacion.Catalogo
import com.solventa.mobile.cotizacion.FormatoCotizacion
import com.solventa.mobile.firma.abrirPdf
import com.solventa.mobile.firma.guardarEnArchivo
import com.solventa.mobile.red.PolizaResponse
import com.solventa.mobile.ui.componentes.BarraSuperior
import com.solventa.mobile.ui.componentes.BotonPrimario
import com.solventa.mobile.ui.componentes.BotonSecundario
import com.solventa.mobile.ui.componentes.BotonTonal
import com.solventa.mobile.ui.componentes.ChipEstado
import com.solventa.mobile.ui.componentes.FilaDetalle
import com.solventa.mobile.ui.componentes.ListaDetalle
import com.solventa.mobile.ui.componentes.Tarjeta
import com.solventa.mobile.ui.componentes.TarjetaDestacada
import com.solventa.mobile.ui.iconos.Iconos
import kotlinx.coroutines.launch

/** Lo que el detalle puede pedirle al flujo; separado del ViewModel para probar la pantalla con estados fijos. */
data class PolizaDetalleAcciones(
    val volver: () -> Unit = {},
    val descargar: (DocumentoPoliza) -> Unit = {},
    val guardar: (Uri?) -> Unit = {},
    val abrirGuardado: () -> Unit = {},
    val reintentarQr: () -> Unit = {},
    val retomarFirma: (PolizaResponse) -> Unit = {},
)

@Composable
fun PolizaDetalleRoute(
    viewModel: PolizasViewModel,
    onVolver: () -> Unit,
    onRetomarFirma: (String) -> Unit,
    onSesionVencida: () -> Unit,
) {
    val estado by viewModel.uiState.collectAsState()
    val contexto = LocalContext.current
    val alcance = androidx.compose.runtime.rememberCoroutineScope()
    var guardadoEn by remember { mutableStateOf<Uri?>(null) }
    LaunchedEffect(estado.error) { if (estado.error == ErrorPolizas.SESION_VENCIDA) onSesionVencida() }
    // Desde una notificación el detalle se abre antes de que llegue la lista: se espera. Si ya llegó y la póliza no está
    // (o el sistema recreó el proceso sin ella), no hay nada que mostrar y se vuelve a la lista.
    val poliza = estado.seleccionada
    if (poliza == null) {
        if (estado.fase == FasePolizas.CARGANDO) {
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        } else {
            LaunchedEffect(Unit) { onVolver() }
        }
        return
    }
    PolizaDetallePantalla(
        estado = estado,
        poliza = poliza,
        acciones = PolizaDetalleAcciones(
            volver = { viewModel.cerrarDetalle(); onVolver() },
            descargar = { guardadoEn = null; viewModel.descargar(it) },
            guardar = { destino ->
                val pdf = estado.pdf
                if (destino == null || pdf == null) {
                    viewModel.descargaGuardada(null)
                } else {
                    alcance.launch {
                        val exito = guardarEnArchivo(contexto, destino, pdf.bytes)
                        if (exito) guardadoEn = destino
                        viewModel.descargaGuardada(exito)
                    }
                }
            },
            abrirGuardado = { guardadoEn?.let { abrirPdf(contexto, it) } },
            reintentarQr = viewModel::reintentarQr,
            retomarFirma = { onRetomarFirma(it.suscripcionId) },
        ),
    )
}

/** Detalle de una póliza (F2): credencial con QR, coberturas, vigencia y prima, y los documentos. */
@Composable
fun PolizaDetallePantalla(estado: PolizasUiState, poliza: PolizaResponse, acciones: PolizaDetalleAcciones) {
    val locale = LocalConfiguration.current.locales[0]
    val situacion = estado.estadoDe(poliza)
    val producto = Catalogo.porId(poliza.producto)?.let { stringResource(it.nombre) } ?: poliza.producto
    val nombreArchivo = nombreDeArchivo(estado.documento, poliza.numeroPoliza)
    val guardar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { destino ->
        acciones.guardar(destino)
    }
    // Cuando el PDF está descargado, el sistema le pregunta al cliente dónde guardarlo (mismo mecanismo que HU22).
    LaunchedEffect(estado.descarga) {
        if (estado.descarga == EstadoDescarga.LISTO_PARA_GUARDAR) guardar.launch(nombreArchivo)
    }
    Column(modifier = Modifier.fillMaxSize()) {
        BarraSuperior(titulo = producto, onAtras = acciones.volver)
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Credencial(estado, poliza, situacion, acciones)
            if (situacion == EstadoPoliza.PENDIENTE_FIRMA) FirmaPendiente(poliza, acciones)
            Coberturas(poliza, locale)
            ListaDetalle(
                titulo = stringResource(R.string.polizas_vigencia),
                filas = listOf(
                    stringResource(R.string.polizas_vigencia) to
                        "${FormatoPolizas.fecha(poliza.vigenciaDesde, locale)} – ${FormatoPolizas.fecha(poliza.fechaVencimiento, locale)}",
                    stringResource(R.string.polizas_prima_anual) to FormatoCotizacion.moneda(poliza.primaAnual, poliza.moneda, locale),
                    stringResource(R.string.polizas_prima_mensual) to FormatoCotizacion.moneda(poliza.primaMensual, poliza.moneda, locale),
                ),
            )
            if (situacion.tieneDocumentos) Documentos(estado, nombreArchivo, acciones)
        }
    }
}

private fun nombreDeArchivo(documento: DocumentoPoliza?, numero: String): String = when (documento) {
    DocumentoPoliza.POLIZA_FIRMADA -> "poliza-$numero-firmada.pdf"
    else -> "certificado-$numero.pdf"
}

/** Tarjeta de la credencial (F2): QR a la izquierda, titular y número a la derecha. Sin QR cuando la póliza ya no cubre. */
@Composable
private fun Credencial(estado: PolizasUiState, poliza: PolizaResponse, situacion: EstadoPoliza, acciones: PolizaDetalleAcciones) {
    TarjetaDestacada(modifier = Modifier.testTag("credencial")) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.polizas_credencial), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            ChipEstado(stringResource(situacion.etiqueta), situacion.icono, situacion.tipoChip)
        }
        Text(stringResource(R.string.polizas_numero, poliza.numeroPoliza), style = MaterialTheme.typography.titleLarge)
        if (situacion.conQr) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                when (val qr = estado.qr) {
                    is EnlaceQr.Listo -> {
                        QrCredencial(qr.url, stringResource(R.string.polizas_qr_descripcion, poliza.numeroPoliza))
                        Text(
                            stringResource(R.string.polizas_qr_ayuda),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    EnlaceQr.Fallido -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.testTag("qr_error")) {
                        Text(stringResource(R.string.polizas_qr_error), style = MaterialTheme.typography.bodyMedium)
                        BotonTonal(stringResource(R.string.polizas_reintentar), onClick = acciones.reintentarQr)
                    }

                    else -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("qr_cargando"),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Text(stringResource(R.string.polizas_qr_cargando), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}

/** El QR sobre fondo blanco con borde de 12 dp (zona de silencio): se lee igual en tema claro y oscuro. */
@Composable
private fun QrCredencial(contenido: String, descripcion: String) {
    val matriz = remember(contenido) { CodigoQr.matriz(contenido) }
    Box(
        modifier = Modifier
            .size(188.dp)
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(12.dp)
            .semantics { contentDescription = descripcion }
            .testTag("qr"),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val modulos = matriz.size
            val lado = size.minDimension / modulos
            matriz.forEachIndexed { y, fila ->
                fila.forEachIndexed { x, oscuro ->
                    if (oscuro) drawRect(Color.Black, Offset(x * lado, y * lado), Size(lado + 0.5f, lado + 0.5f))
                }
            }
        }
    }
}

@Composable
private fun FirmaPendiente(poliza: PolizaResponse, acciones: PolizaDetalleAcciones) = Tarjeta(modifier = Modifier.testTag("firma_pendiente")) {
    Text(stringResource(R.string.polizas_firma_pendiente_titulo), style = MaterialTheme.typography.titleMedium)
    Text(
        stringResource(R.string.polizas_firma_pendiente_mensaje),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    BotonPrimario(stringResource(R.string.polizas_retomar_firma), onClick = { acciones.retomarFirma(poliza) }, modifier = Modifier.testTag("retomar_firma"))
}

@Composable
private fun Coberturas(poliza: PolizaResponse, locale: java.util.Locale) {
    if (poliza.coberturas.isEmpty()) {
        Text(
            stringResource(R.string.polizas_sin_coberturas),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("sin_coberturas"),
        )
        return
    }
    TarjetaCoberturas(poliza, locale)
}

/**
 * Coberturas (F2). Cada una es una fila `tipo … monto`; si tiene deducible, va en su propia fila debajo, con sangría, para
 * que ni el nombre de la cobertura ni el importe se compriman en una sola línea.
 */
@Composable
private fun TarjetaCoberturas(poliza: PolizaResponse, locale: java.util.Locale) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().testTag("coberturas"),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(
                stringResource(R.string.polizas_coberturas),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            poliza.coberturas.forEachIndexed { i, cobertura ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                FilaDetalle(FormatoPolizas.tipoCobertura(cobertura.tipo), FormatoCotizacion.moneda(cobertura.monto, poliza.moneda, locale))
                if (cobertura.deducible > 0) {
                    FilaDetalle(
                        etiqueta = stringResource(R.string.polizas_deducible),
                        valor = FormatoCotizacion.moneda(cobertura.deducible, poliza.moneda, locale),
                        modifier = Modifier.padding(start = 16.dp).semantics(mergeDescendants = true) { }.testTag("deducible_${i}"),
                    )
                }
            }
        }
    }
}

@Composable
private fun Documentos(estado: PolizasUiState, nombreArchivo: String, acciones: PolizaDetalleAcciones) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("documentos")) {
        Text(
            stringResource(R.string.polizas_documentos),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        BotonTonal(
            stringResource(R.string.polizas_descargar_firmada),
            onClick = { acciones.descargar(DocumentoPoliza.POLIZA_FIRMADA) },
            modifier = Modifier.testTag("descargar_firmada"),
        )
        BotonTonal(
            stringResource(R.string.polizas_descargar_certificado),
            onClick = { acciones.descargar(DocumentoPoliza.CERTIFICADO) },
            modifier = Modifier.testTag("descargar_certificado"),
        )
        Estado(estado.descarga, nombreArchivo, acciones)
    }
}

/** El avance de la descarga y dónde quedó el archivo, anunciados al lector de pantalla. */
@Composable
private fun Estado(descarga: EstadoDescarga, nombreArchivo: String, acciones: PolizaDetalleAcciones) {
    Column(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("estado_descarga")) {
        when (descarga) {
            EstadoDescarga.DESCARGANDO -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.polizas_descargando), style = MaterialTheme.typography.bodyMedium)
            }

            EstadoDescarga.GUARDADO -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Iconos.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.polizas_guardado, nombreArchivo), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                BotonSecundario(stringResource(R.string.polizas_abrir_pdf), onClick = acciones.abrirGuardado, modifier = Modifier.fillMaxWidth(0.3f))
            }

            EstadoDescarga.ERROR -> Text(stringResource(R.string.polizas_descarga_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            EstadoDescarga.NO_DISPONIBLE -> Text(stringResource(R.string.polizas_descarga_no_disponible), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            else -> Unit
        }
    }
}
