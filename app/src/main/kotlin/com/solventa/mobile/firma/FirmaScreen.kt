package com.solventa.mobile.firma

import androidx.activity.compose.BackHandler
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.solventa.mobile.cotizacion.Catalogo
import com.solventa.mobile.cotizacion.FormatoCotizacion
import com.solventa.mobile.ui.componentes.BloqueResultado
import com.solventa.mobile.ui.componentes.BotonSecundario
import com.solventa.mobile.ui.componentes.ChipEstado
import com.solventa.mobile.ui.componentes.TarjetaDestacada
import com.solventa.mobile.ui.componentes.TipoEstado
import kotlinx.coroutines.launch
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.solventa.mobile.R
import com.solventa.mobile.compra.Centrado
import com.solventa.mobile.compra.Espera
import com.solventa.mobile.compra.Resultado
import com.solventa.mobile.compra.Tono
import com.solventa.mobile.red.DocumentoFirmaResponse
import com.solventa.mobile.ui.componentes.Aviso
import com.solventa.mobile.ui.componentes.BarraSuperior
import com.solventa.mobile.ui.componentes.BotonPrimario
import com.solventa.mobile.ui.componentes.IconoCircular
import com.solventa.mobile.ui.componentes.IndicadorPasos
import com.solventa.mobile.ui.componentes.Tarjeta
import com.solventa.mobile.ui.iconos.Iconos
import com.solventa.mobile.ui.tema.SolventaColorScheme

/** Lo que la pantalla puede pedirle al flujo; separado del ViewModel para probarla con estados fijos. */
data class FirmaAcciones(
    val marcarRevisado: (Boolean) -> Unit = {},
    val cambiarCodigo: (String) -> Unit = {},
    val reenviarCodigo: () -> Unit = {},
    val firmar: () -> Unit = {},
    val leer: () -> Unit = {},
    val cerrarLector: () -> Unit = {},
    val reintentar: () -> Unit = {},
    val terminar: () -> Unit = {},
    val cotizarDeNuevo: () -> Unit = {},
    val sesionVencida: () -> Unit = {},
    val volver: () -> Unit = {},
    /** El cliente pidió el certificado (HU22). */
    val descargarCertificado: () -> Unit = {},
    /** El selector de archivos devolvió dónde guardarlo, o `null` si el cliente canceló. */
    val guardarCertificado: (Uri?) -> Unit = {},
    val abrirCertificado: () -> Unit = {},
)

@Composable
fun FirmaRoute(
    viewModel: FirmaViewModel,
    onTerminar: () -> Unit,
    onCotizarDeNuevo: () -> Unit,
    onSesionVencida: () -> Unit,
    onVolver: () -> Unit,
) {
    val estado by viewModel.uiState.collectAsState()
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    var guardadoEn by remember { mutableStateOf<Uri?>(null) }
    // Atrás cierra primero el lector del PDF; con el lector cerrado, deja la firma.
    BackHandler(enabled = estado.pdf != null) { viewModel.cerrarLector() }
    FirmaScreen(
        estado = estado,
        acciones = FirmaAcciones(
            marcarRevisado = viewModel::marcarRevisado,
            cambiarCodigo = viewModel::cambiarCodigo,
            reenviarCodigo = viewModel::reenviarCodigo,
            firmar = viewModel::firmar,
            leer = viewModel::leer,
            cerrarLector = viewModel::cerrarLector,
            reintentar = viewModel::reintentar,
            terminar = onTerminar,
            cotizarDeNuevo = onCotizarDeNuevo,
            sesionVencida = onSesionVencida,
            volver = onVolver,
            descargarCertificado = viewModel::descargarCertificado,
            guardarCertificado = { destino ->
                val pdf = estado.certificadoPdf
                if (destino == null || pdf == null) {
                    viewModel.certificadoGuardado(null)
                } else {
                    alcance.launch {
                        val exito = guardarEnArchivo(contexto, destino, pdf.bytes)
                        if (exito) guardadoEn = destino
                        viewModel.certificadoGuardado(exito)
                    }
                }
            },
            abrirCertificado = { guardadoEn?.let { abrirPdf(contexto, it) } },
        ),
    )
}

/**
 * Firma electrónica de la póliza (HU21, E4 del diseño): «Paso 3 de 3 · Firma», el documento con «Leer», la casilla de
 * lectura, el código de un solo uso y «Firmar póliza». El número de celular enmascarado del diseño no se muestra: la API
 * no lo entrega.
 */
@Composable
fun FirmaScreen(estado: FirmaUiState, acciones: FirmaAcciones) {
    estado.pdf?.let { return VisorPdf(it, acciones.cerrarLector) }
    when (estado.fase) {
        FaseFirma.CARGANDO -> Espera(R.string.firma_cargando)
        FaseFirma.FIRMANDO -> Espera(R.string.firma_firmando)
        FaseFirma.LISTA -> estado.documento?.let { Formulario(estado, it, acciones) }
        FaseFirma.FIRMADA -> Certificado(estado, acciones)
        FaseFirma.FALLIDA -> Resultado(
            Iconos.Exclamacion, Tono.ERROR,
            R.string.firma_fallida_titulo,
            R.string.firma_fallida_mensaje,
            R.string.compra_cotizar_de_nuevo to acciones.cotizarDeNuevo,
        )
        FaseFirma.VENCIDA -> Resultado(
            Iconos.Informacion, Tono.NEUTRO,
            R.string.firma_vencida_titulo,
            R.string.firma_vencida_mensaje,
            R.string.compra_cotizar_de_nuevo to acciones.cotizarDeNuevo,
        )
        FaseFirma.ERROR -> Falla(estado.error ?: ErrorFirma.SERVICIO, acciones)
    }
}

/**
 * Póliza firmada y emitida (HU21) con su certificado (HU22, E5 del diseño): la tarjeta del certificado, el aviso de las
 * notificaciones y «Descargar certificado». El diseño dice «Ya estás cubierta»; aquí el texto no marca género.
 * Si no se pudo leer el detalle de la póliza, la tarjeta se muestra con el número que ya se tiene.
 */
@Composable
private fun Certificado(estado: FirmaUiState, acciones: FirmaAcciones) {
    val locale = LocalConfiguration.current.locales[0]
    val poliza = estado.poliza
    val numero = poliza?.numeroPoliza ?: estado.documento?.numeroPoliza
    val nombreArchivo = stringResource(R.string.certificado_nombre_archivo, numero.orEmpty())
    val guardar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { destino ->
        acciones.guardarCertificado(destino)
    }
    // Cuando el PDF está descargado, el sistema le pregunta al cliente dónde guardarlo.
    LaunchedEffect(estado.certificado) {
        if (estado.certificado == EstadoCertificado.LISTO_PARA_GUARDAR) guardar.launch(nombreArchivo)
    }
    Column(modifier = Modifier.fillMaxSize().semantics { liveRegion = LiveRegionMode.Polite }) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            BloqueResultado(
                icono = Iconos.Check,
                descripcionIcono = stringResource(R.string.resultado_icono_exito),
                contenedor = SolventaColorScheme.tertiaryContainer,
                colorIcono = SolventaColorScheme.onTertiaryContainer,
                titulo = stringResource(R.string.certificado_titulo),
                mensaje = stringResource(R.string.certificado_mensaje),
            )
            TarjetaDestacada(modifier = Modifier.padding(horizontal = 24.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.certificado_encabezado), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    ChipEstado(stringResource(R.string.certificado_activa), Iconos.Check, TipoEstado.ACTIVO)
                }
                poliza?.let { Catalogo.porId(it.producto) }?.let {
                    Text(stringResource(it.nombre), style = MaterialTheme.typography.titleLarge)
                }
                numero?.let { FilaCertificado(stringResource(R.string.certificado_poliza), it) }
                poliza?.let {
                    FilaCertificado(
                        stringResource(R.string.certificado_vigencia),
                        "${FormatoCotizacion.vigencia(it.vigenciaDesde, locale)} – ${FormatoCotizacion.vigencia(it.fechaVencimiento, locale)}",
                    )
                    FilaCertificado(stringResource(R.string.certificado_prima), FormatoCotizacion.moneda(it.primaAnual, it.moneda, locale))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth()) {
                Icon(Iconos.Informacion, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Text(
                    stringResource(R.string.certificado_aviso),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            EstadoDeLaDescarga(estado.certificado, acciones)
        }
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonPrimario(
                stringResource(R.string.certificado_descargar),
                onClick = acciones.descargarCertificado,
                habilitado = estado.certificado != EstadoCertificado.DESCARGANDO,
                modifier = Modifier.testTag("descargar_certificado"),
            )
            BotonSecundario(stringResource(R.string.firma_ok_accion), onClick = acciones.terminar)
        }
    }
}

@Composable
private fun FilaCertificado(etiqueta: String, valor: String) = Row(
    horizontalArrangement = Arrangement.SpaceBetween,
    modifier = Modifier.fillMaxWidth(),
) {
    Text(etiqueta, style = MaterialTheme.typography.bodyMedium)
    Text(valor, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), textAlign = TextAlign.End, modifier = Modifier.weight(1f).padding(start = 12.dp))
}

@Composable
private fun EstadoDeLaDescarga(estado: EstadoCertificado, acciones: FirmaAcciones) {
    when (estado) {
        EstadoCertificado.DESCARGANDO -> Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.certificado_descargando), style = MaterialTheme.typography.bodyMedium)
        }
        EstadoCertificado.GUARDADO -> Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            Text(stringResource(R.string.certificado_guardado), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = acciones.abrirCertificado, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.certificado_abrir))
            }
        }
        EstadoCertificado.ERROR -> Text(
            stringResource(R.string.certificado_error),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        EstadoCertificado.INACTIVO, EstadoCertificado.LISTO_PARA_GUARDAR -> Unit
    }
}

@Composable
private fun Formulario(estado: FirmaUiState, documento: DocumentoFirmaResponse, acciones: FirmaAcciones) {
    Column(modifier = Modifier.fillMaxSize()) {
        BarraSuperior(titulo = stringResource(R.string.firma_barra), onAtras = acciones.volver)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // El diseño la deja en 90 %: queda un paso, el de firmar.
            IndicadorPasos(paso = 3, total = 3, nombre = stringResource(R.string.compra_paso_firma), progreso = 0.9f)
            estado.aviso?.let { AvisoDeFirma(it, estado.intentosRestantes) }
            TarjetaDocumento(documento, estado, acciones)
            CasillaRevision(estado, acciones)
            SeccionCodigo(estado, acciones)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                Icon(Iconos.Escudo, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(
                    stringResource(R.string.firma_constancia),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            BotonPrimario(stringResource(R.string.firma_firmar), onClick = acciones.firmar)
        }
    }
}

@Composable
private fun AvisoDeFirma(aviso: AvisoFirma, intentos: Int?) {
    val texto = when (aviso) {
        AvisoFirma.CODIGO_INCORRECTO ->
            if (intentos != null) pluralStringResource(R.plurals.firma_aviso_codigo_con_intentos, intentos, intentos)
            else stringResource(R.string.firma_aviso_codigo)
        AvisoFirma.DOCUMENTO_CAMBIO -> stringResource(R.string.firma_aviso_documento)
        AvisoFirma.SIN_CONEXION -> stringResource(R.string.firma_aviso_sin_conexion)
        AvisoFirma.SERVICIO -> stringResource(R.string.firma_aviso_servicio)
    }
    Aviso(
        texto = texto,
        icono = Iconos.Exclamacion,
        contenedor = MaterialTheme.colorScheme.errorContainer,
        contenido = MaterialTheme.colorScheme.onErrorContainer,
        colorIcono = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
    )
}

@Composable
private fun TarjetaDocumento(documento: DocumentoFirmaResponse, estado: FirmaUiState, acciones: FirmaAcciones) =
    Tarjeta {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconoCircular(
                Iconos.Documento, null,
                contenedor = MaterialTheme.colorScheme.primaryContainer,
                contenido = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.firma_poliza, documento.numeroPoliza), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.firma_pdf),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = acciones.leer,
                enabled = !estado.cargandoPdf,
                modifier = Modifier.heightIn(min = 48.dp).testTag("leer"),
            ) { Text(stringResource(R.string.firma_leer)) }
        }
        if (estado.errorPdf) {
            Text(
                stringResource(R.string.firma_lector_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }

@Composable
private fun CasillaRevision(estado: FirmaUiState, acciones: FirmaAcciones) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = estado.revisado, role = Role.Checkbox, onValueChange = acciones.marcarRevisado)
            .testTag("revisado"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = estado.revisado, onCheckedChange = null)
        Text(stringResource(R.string.firma_revisado), style = MaterialTheme.typography.bodyMedium)
    }
    if (estado.revisionPendiente) {
        Text(
            stringResource(R.string.firma_revision_pendiente),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun SeccionCodigo(estado: FirmaUiState, acciones: FirmaAcciones) =
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.firma_codigo_instruccion), style = MaterialTheme.typography.bodyLarge)
        CampoCodigo(estado.codigo, estado.codigoIncompleto, acciones.cambiarCodigo)
        if (estado.codigoIncompleto) {
            Text(
                stringResource(R.string.firma_codigo_incompleto),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        val esperando = estado.segundosParaReenviar > 0
        TextButton(onClick = acciones.reenviarCodigo, enabled = !esperando, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(
                if (esperando) stringResource(R.string.firma_reenviar_en, FormatoFirma.cuentaAtras(estado.segundosParaReenviar))
                else stringResource(R.string.firma_reenviar),
            )
        }
        if (estado.codigoReenviado) {
            Text(
                stringResource(R.string.firma_codigo_reenviado),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }

/**
 * Seis casillas sobre un solo campo de texto: pegar el código, el autocompletado del teclado y el lector de pantalla
 * funcionan como en cualquier campo, y cada casilla es solo dibujo. La casilla activa lleva el borde grueso del diseño.
 */
@Composable
internal fun CampoCodigo(codigo: String, conError: Boolean, onCambio: (String) -> Unit) {
    val etiqueta = stringResource(R.string.firma_codigo_etiqueta, LARGO_CODIGO)
    BasicTextField(
        value = codigo,
        onValueChange = onCambio,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("codigo")
            .semantics {
                contentDescription = etiqueta
                stateDescription = codigo.length.toString()
            },
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormatoFirma.casillas(codigo).forEachIndexed { indice, digito ->
                    val activa = indice == codigo.length
                    val borde = when {
                        conError -> BorderStroke(2.dp, MaterialTheme.colorScheme.error)
                        activa -> BorderStroke(2.dp, SolventaColorScheme.primary)
                        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .border(borde, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Los dígitos no se repiten al lector: la descripción del campo ya los resume.
                        Text(
                            digito?.toString().orEmpty(),
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { contentDescription = "" },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun Falla(error: ErrorFirma, acciones: FirmaAcciones) {
    @StringRes val mensaje = when (error) {
        ErrorFirma.SIN_CONEXION -> R.string.compra_error_sin_conexion
        ErrorFirma.SERVICIO -> R.string.compra_error_servicio
        ErrorFirma.SESION_VENCIDA -> R.string.compra_error_sesion
        ErrorFirma.DOCUMENTO_NO_LISTO -> R.string.firma_error_documento
        ErrorFirma.NO_DISPONIBLE -> R.string.firma_error_no_disponible
        ErrorFirma.RESULTADO_DESCONOCIDO -> R.string.firma_error_desconocido
    }
    val accion: Pair<Int, () -> Unit> = when (error) {
        ErrorFirma.SESION_VENCIDA -> R.string.compra_iniciar_sesion to acciones.sesionVencida
        ErrorFirma.NO_DISPONIBLE -> R.string.firma_ok_accion to acciones.terminar
        ErrorFirma.RESULTADO_DESCONOCIDO -> R.string.compra_verificar to acciones.reintentar
        else -> R.string.cotizacion_reintentar to acciones.reintentar
    }
    Resultado(Iconos.Exclamacion, Tono.ERROR, R.string.firma_error_titulo, mensaje, accion)
}
