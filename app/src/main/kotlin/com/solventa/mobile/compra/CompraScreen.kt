package com.solventa.mobile.compra

import android.app.Activity
import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.solventa.mobile.R
import com.solventa.mobile.cotizacion.Catalogo
import com.solventa.mobile.cotizacion.FormatoCotizacion
import com.solventa.mobile.cotizacion.nombreCobertura
import com.solventa.mobile.red.CotizacionResponse
import com.solventa.mobile.ui.componentes.Aviso
import com.solventa.mobile.ui.componentes.BarraSuperior
import com.solventa.mobile.ui.componentes.BloqueResultado
import com.solventa.mobile.ui.componentes.BotonPrimario
import com.solventa.mobile.ui.componentes.BotonSecundario
import com.solventa.mobile.ui.componentes.CampoTexto
import com.solventa.mobile.ui.componentes.IndicadorPasos
import com.solventa.mobile.ui.componentes.ListaDetalle
import com.solventa.mobile.ui.componentes.Tarjeta
import com.solventa.mobile.ui.iconos.Iconos
import com.solventa.mobile.ui.tema.SolventaColorScheme

/** Lo que la pantalla puede pedirle al flujo; separado del ViewModel para probarla con estados fijos. */
data class CompraAcciones(
    val aceptarTerminos: (Boolean) -> Unit = {},
    val continuarAPago: () -> Unit = {},
    val volverALaOferta: () -> Unit = {},
    val cambiarTarjeta: (String, String, String) -> Unit = { _, _, _ -> },
    val pagar: () -> Unit = {},
    val reintentar: () -> Unit = {},
    val cotizarDeNuevo: () -> Unit = {},
    val terminar: () -> Unit = {},
    val sesionVencida: () -> Unit = {},
    val volver: () -> Unit = {},
)

@Composable
fun CompraRoute(
    viewModel: CompraViewModel,
    onCotizarDeNuevo: () -> Unit,
    onTerminar: () -> Unit,
    onSesionVencida: () -> Unit,
    onVolver: () -> Unit = {},
) {
    val estado by viewModel.uiState.collectAsState()
    CompraScreen(
        estado = estado,
        acciones = CompraAcciones(
            aceptarTerminos = viewModel::aceptarTerminos,
            continuarAPago = viewModel::continuarAPago,
            volverALaOferta = viewModel::volverALaOferta,
            cambiarTarjeta = viewModel::cambiarTarjeta,
            pagar = viewModel::pagar,
            reintentar = viewModel::reintentar,
            cotizarDeNuevo = onCotizarDeNuevo,
            terminar = onTerminar,
            sesionVencida = onSesionVencida,
            volver = onVolver,
        ),
    )
}

/**
 * Compra de la oferta (HU17, E1 a E3 del diseño): paso 1 «Oferta», paso 2 «Pago» y el resultado. Solo hay pago con
 * tarjeta: los métodos guardados, PSE y la billetera del diseño no existen en HU17 (HU18 define el cobro).
 */
@Composable
fun CompraScreen(estado: CompraUiState, acciones: CompraAcciones) {
    // Mientras se captura la tarjeta la pantalla no se puede grabar ni aparece en la vista de apps recientes.
    if (estado.fase == FaseCompra.PAGO || estado.fase == FaseCompra.ENVIANDO) PantallaProtegida()
    val cotizacion = estado.cotizacion ?: return
    when (estado.fase) {
        FaseCompra.OFERTA -> Oferta(estado, cotizacion, acciones)
        FaseCompra.PAGO -> Pago(estado, cotizacion, acciones)
        FaseCompra.ENVIANDO -> Espera(R.string.compra_enviando)
        FaseCompra.PROGRESO -> Progreso(estado.paso)
        FaseCompra.EXITOSA -> Resultado(
            Iconos.Check, Tono.EXITO,
            R.string.compra_exitosa_titulo,
            R.string.compra_exitosa_mensaje,
            R.string.compra_exitosa_accion to acciones.terminar,
            detalle = { DatosDelPago(estado) },
        )
        FaseCompra.DECISION_RECHAZADA -> Resultado(
            Iconos.Exclamacion, Tono.ERROR,
            R.string.compra_rechazada_titulo,
            R.string.compra_rechazada_mensaje,
            R.string.compra_cotizar_de_nuevo to acciones.cotizarDeNuevo,
            apoyo = R.string.compra_rechazada_motivo_generico,
        )
        FaseCompra.EN_REVISION -> Resultado(
            Iconos.Informacion, Tono.NEUTRO,
            R.string.compra_revision_titulo,
            if (estado.ajustada) R.string.compra_revision_ajuste else R.string.compra_revision_mensaje,
            R.string.compra_exitosa_accion to acciones.terminar,
        )
        FaseCompra.COBRO_RECHAZADO -> Resultado(
            Iconos.Exclamacion, Tono.ERROR,
            R.string.compra_cobro_rechazado_titulo,
            R.string.compra_cobro_rechazado_mensaje,
            R.string.resultado_entendido to acciones.terminar,
            detalle = { DatosDelCobroRechazado(estado) },
        )
        FaseCompra.OFERTA_VENCIDA -> Resultado(
            Iconos.Informacion, Tono.NEUTRO,
            R.string.compra_vencida_titulo,
            R.string.compra_vencida_mensaje,
            R.string.compra_cotizar_de_nuevo to acciones.cotizarDeNuevo,
        )
        FaseCompra.ERROR -> Falla(estado.error ?: ErrorCompra.SERVICIO, acciones)
    }
}

/** Zona desplazable con barra superior opcional y los botones de decisión fijos al pie. */
@Composable
private fun Estructura(
    titulo: String?,
    onAtras: (() -> Unit)?,
    pie: (@Composable ColumnScope.() -> Unit)?,
    cuerpo: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (titulo != null) BarraSuperior(titulo = titulo, onAtras = onAtras)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = cuerpo,
        )
        if (pie != null) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = pie,
            )
        }
    }
}

@Composable
private fun Titular(@StringRes texto: Int) =
    Text(
        stringResource(texto),
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.semantics { heading() },
    )

/** «Total a pagar» con el importe en 28 sp (prima anual: el cobro exacto lo define HU18). */
@Composable
private fun Total(cotizacion: CotizacionResponse) {
    val locale = LocalConfiguration.current.locales[0]
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(stringResource(R.string.compra_total), style = MaterialTheme.typography.bodyLarge)
        Text(
            FormatoCotizacion.moneda(cotizacion.primaAnual, cotizacion.moneda, locale),
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}

@Composable
private fun Oferta(estado: CompraUiState, cotizacion: CotizacionResponse, acciones: CompraAcciones) =
    Estructura(
        titulo = stringResource(R.string.compra_oferta_barra),
        onAtras = acciones.volver,
        pie = { BotonPrimario(stringResource(R.string.compra_continuar), onClick = acciones.continuarAPago) },
    ) {
        val locale = LocalConfiguration.current.locales[0]
        IndicadorPasos(paso = 1, total = 3, nombre = stringResource(R.string.compra_paso_oferta))
        Titular(R.string.compra_oferta_titulo)
        val filas = buildList {
            Catalogo.porId(cotizacion.producto)?.let { add(stringResource(R.string.compra_producto) to stringResource(it.nombre)) }
            add(
                stringResource(R.string.compra_vigencia) to FormatoCotizacion.vigencia(cotizacion.vigenteHasta, locale),
            )
            cotizacion.coberturas.forEach {
                add(nombreCobertura(it.tipo) to FormatoCotizacion.moneda(it.monto, cotizacion.moneda, locale))
            }
        }
        ListaDetalle(filas)
        Total(cotizacion)
        Tarjeta {
            Text(stringResource(R.string.compra_terminos_titulo), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.compra_terminos_texto),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(value = estado.aceptaTerminos, role = Role.Checkbox, onValueChange = acciones.aceptarTerminos),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = estado.aceptaTerminos, onCheckedChange = null)
            Text(stringResource(R.string.compra_terminos_acepto), style = MaterialTheme.typography.bodyMedium)
        }
        if (estado.terminosPendientes) {
            Text(
                stringResource(R.string.compra_terminos_pendiente),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }

@Composable
private fun Pago(estado: CompraUiState, cotizacion: CotizacionResponse, acciones: CompraAcciones) {
    val locale = LocalConfiguration.current.locales[0]
    val tarjeta = estado.tarjeta
    // NumberPassword: el teclado no sugiere ni aprende lo que se escribe.
    val teclado = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
    Estructura(
        titulo = stringResource(R.string.compra_pago_barra),
        onAtras = acciones.volverALaOferta,
        pie = {
            BotonPrimario(
                stringResource(R.string.compra_pagar, FormatoCotizacion.moneda(cotizacion.primaAnual, cotizacion.moneda, locale)),
                onClick = acciones.pagar,
            )
        },
    ) {
        IndicadorPasos(paso = 2, total = 3, nombre = stringResource(R.string.compra_paso_pago))
        Titular(R.string.compra_pago_titulo)
        // Único método disponible: tarjeta, con el aspecto de «elegido» del diseño.
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .selectable(selected = true, role = Role.RadioButton, onClick = {}),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = true, onClick = null)
                Icon(Iconos.Tarjeta, contentDescription = null, modifier = Modifier.size(24.dp))
                Text(stringResource(R.string.compra_metodo_tarjeta), style = MaterialTheme.typography.bodyLarge)
            }
        }
        CampoTexto(
            valor = tarjeta.numero,
            onValorChange = { acciones.cambiarTarjeta(it, tarjeta.vencimiento, tarjeta.cvv) },
            etiqueta = stringResource(R.string.compra_tarjeta_numero),
            error = if (ErrorTarjeta.NUMERO in estado.erroresTarjeta) stringResource(R.string.compra_error_numero) else null,
            teclado = teclado,
        )
        CampoTexto(
            valor = tarjeta.vencimiento,
            onValorChange = { acciones.cambiarTarjeta(tarjeta.numero, formatearVencimiento(it), tarjeta.cvv) },
            etiqueta = stringResource(R.string.compra_tarjeta_vencimiento),
            error = if (ErrorTarjeta.VENCIMIENTO in estado.erroresTarjeta) stringResource(R.string.compra_error_vencimiento) else null,
            teclado = teclado,
        )
        CampoTexto(
            valor = tarjeta.cvv,
            onValorChange = { acciones.cambiarTarjeta(tarjeta.numero, tarjeta.vencimiento, it.take(4)) },
            etiqueta = stringResource(R.string.compra_tarjeta_cvv),
            error = if (ErrorTarjeta.CVV in estado.erroresTarjeta) stringResource(R.string.compra_error_cvv) else null,
            teclado = teclado,
            transformacion = PasswordVisualTransformation(),
        )
        Total(cotizacion)
        Aviso(stringResource(R.string.compra_pago_seguro), Iconos.Candado)
    }
}

/** `FLAG_SECURE` mientras la pantalla está visible (HU17, definición de terminado). */
@Composable
private fun PantallaProtegida() {
    val ventana = (LocalContext.current as? Activity)?.window
    DisposableEffect(ventana) {
        ventana?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { ventana?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

/** `MM/AA`: inserta la barra sola después del mes. */
internal fun formatearVencimiento(entrada: String): String {
    val digitos = entrada.filter(Char::isDigit).take(4)
    return if (digitos.length <= 2) digitos else digitos.substring(0, 2) + "/" + digitos.substring(2)
}

@StringRes
internal fun textoPaso(paso: String): Int = when (paso) {
    "DECISION" -> R.string.compra_paso_decision
    "COBRO" -> R.string.compra_paso_cobro
    "FIRMA" -> R.string.compra_paso_firma
    "EMISION" -> R.string.compra_paso_emision
    else -> R.string.compra_paso_otro // un paso nuevo del servidor no rompe la pantalla
}

@Composable
private fun Espera(@StringRes mensaje: Int) = Centrado {
    CircularProgressIndicator()
    Text(
        stringResource(mensaje),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun Progreso(paso: String) = Centrado {
    CircularProgressIndicator()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            stringResource(R.string.compra_progreso_titulo),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.compra_progreso_paso, stringResource(textoPaso(paso))),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Centrado(contenido: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        content = contenido,
    )
}

private enum class Tono { EXITO, ERROR, NEUTRO }

/** Resultado de la compra: círculo con ícono, título, mensaje y una sola acción al pie (E3 y variantes). */
@Composable
private fun Resultado(
    icono: ImageVector,
    tono: Tono,
    @StringRes titulo: Int,
    @StringRes mensaje: Int,
    accion: Pair<Int, () -> Unit>,
    @StringRes apoyo: Int? = null,
    detalle: @Composable () -> Unit = {},
) {
    val (fondo, color, descripcion) = when (tono) {
        Tono.EXITO -> Triple(SolventaColorScheme.tertiaryContainer, SolventaColorScheme.onTertiaryContainer, R.string.resultado_icono_exito)
        Tono.ERROR -> Triple(SolventaColorScheme.errorContainer, SolventaColorScheme.onErrorContainer, R.string.resultado_icono_aviso)
        Tono.NEUTRO -> Triple(SolventaColorScheme.primaryContainer, SolventaColorScheme.onPrimaryContainer, R.string.resultado_icono_info)
    }
    Column(
        modifier = Modifier.fillMaxSize().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BloqueResultado(
                icono = icono,
                descripcionIcono = stringResource(descripcion),
                contenedor = fondo,
                colorIcono = color,
                titulo = stringResource(titulo),
                mensaje = stringResource(mensaje),
            ) {
                apoyo?.let {
                    Text(
                        stringResource(it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                detalle()
            }
        }
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            BotonPrimario(stringResource(accion.first), onClick = accion.second)
        }
    }
}

/** Referencia y fecha del pago, tal como las entrega la pasarela (E3). Si no llegan, no se muestra nada. */
@Composable
private fun DatosDelPago(estado: CompraUiState) {
    val locale = LocalConfiguration.current.locales[0]
    val filas = buildList {
        estado.referenciaPago?.let { add(stringResource(R.string.compra_referencia) to it) }
        estado.fechaPago?.let { add(stringResource(R.string.compra_fecha_pago) to FormatoCotizacion.vigencia(it, locale)) }
    }
    if (filas.isNotEmpty()) ListaDetalle(filas)
}

/** Motivo que da la pasarela y fecha límite del período de gracia; qué hacer después lo define HU19. */
@Composable
private fun DatosDelCobroRechazado(estado: CompraUiState) {
    val locale = LocalConfiguration.current.locales[0]
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        estado.motivoCobro?.let {
            Text(stringResource(R.string.compra_cobro_motivo, it), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }
        estado.graciaHasta?.let {
            Text(
                stringResource(R.string.compra_cobro_plazo, FormatoCotizacion.vigencia(it, locale)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Falla(error: ErrorCompra, acciones: CompraAcciones) {
    val mensaje = when (error) {
        ErrorCompra.SIN_CONEXION -> R.string.compra_error_sin_conexion
        ErrorCompra.SERVICIO -> R.string.compra_error_servicio
        ErrorCompra.SESION_VENCIDA -> R.string.compra_error_sesion
        ErrorCompra.RESULTADO_DESCONOCIDO -> R.string.compra_error_desconocido
        ErrorCompra.OFERTA_YA_USADA -> R.string.compra_error_ya_usada
        ErrorCompra.SEGUIMIENTO_LENTO -> R.string.compra_error_lento
    }
    val accion: Pair<Int, () -> Unit> = when (error) {
        ErrorCompra.SESION_VENCIDA -> R.string.compra_iniciar_sesion to acciones.sesionVencida
        ErrorCompra.OFERTA_YA_USADA -> R.string.compra_cotizar_de_nuevo to acciones.cotizarDeNuevo
        ErrorCompra.RESULTADO_DESCONOCIDO -> R.string.compra_verificar to acciones.reintentar
        ErrorCompra.SEGUIMIENTO_LENTO -> R.string.compra_consultar to acciones.reintentar
        else -> R.string.cotizacion_reintentar to acciones.reintentar
    }
    Resultado(Iconos.Exclamacion, Tono.ERROR, R.string.compra_error_titulo, mensaje, accion)
}
