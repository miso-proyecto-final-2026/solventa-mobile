package com.solventa.mobile.compra

import android.app.Activity
import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.dp
import com.solventa.mobile.R
import com.solventa.mobile.cotizacion.FormatoCotizacion
import com.solventa.mobile.red.CotizacionResponse
import java.util.Locale

/** Lo que la pantalla puede pedirle al flujo; separado del ViewModel para probarla con estados fijos. */
data class CompraAcciones(
    val aceptarTerminos: (Boolean) -> Unit = {},
    val continuarAPago: () -> Unit = {},
    val volverALaOferta: () -> Unit = {},
    val cambiarTarjeta: (String, String, String) -> Unit = { _, _, _ -> },
    val pagar: () -> Unit = {},
    val reintentar: () -> Unit = {},
    val otroMedioDePago: () -> Unit = {},
    val cotizarDeNuevo: () -> Unit = {},
    val terminar: () -> Unit = {},
    val sesionVencida: () -> Unit = {},
)

@Composable
fun CompraRoute(viewModel: CompraViewModel, onCotizarDeNuevo: () -> Unit, onTerminar: () -> Unit, onSesionVencida: () -> Unit) {
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
            otroMedioDePago = viewModel::usarOtroMedioDePago,
            cotizarDeNuevo = onCotizarDeNuevo,
            terminar = onTerminar,
            sesionVencida = onSesionVencida,
        ),
    )
}

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
            R.string.compra_exitosa_titulo,
            listOf(R.string.compra_exitosa_mensaje),
            R.string.compra_exitosa_accion to acciones.terminar,
        )
        FaseCompra.DECISION_RECHAZADA -> Resultado(
            R.string.compra_rechazada_titulo,
            listOf(R.string.compra_rechazada_mensaje, R.string.compra_rechazada_motivo_generico),
            R.string.compra_cotizar_de_nuevo to acciones.cotizarDeNuevo,
        )
        FaseCompra.EN_REVISION -> Resultado(
            R.string.compra_revision_titulo,
            listOf(if (estado.ajustada) R.string.compra_revision_ajuste else R.string.compra_revision_mensaje),
            R.string.compra_exitosa_accion to acciones.terminar,
        )
        FaseCompra.COBRO_RECHAZADO -> Resultado(
            R.string.compra_cobro_rechazado_titulo,
            listOf(R.string.compra_cobro_rechazado_mensaje),
            R.string.compra_otro_medio to acciones.otroMedioDePago,
        )
        FaseCompra.OFERTA_VENCIDA -> Resultado(
            R.string.compra_vencida_titulo,
            listOf(R.string.compra_vencida_mensaje),
            R.string.compra_cotizar_de_nuevo to acciones.cotizarDeNuevo,
        )
        FaseCompra.ERROR -> Falla(estado.error ?: ErrorCompra.SERVICIO, acciones)
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

@Composable
private fun Pantalla(contenido: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { contenido() }
}

@Composable
private fun Titulo(@StringRes texto: Int) =
    Text(stringResource(texto), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })

@Composable
private fun Boton(@StringRes texto: Int, onClick: () -> Unit) =
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(texto)) }

@Composable
private fun BotonSecundario(@StringRes texto: Int, onClick: () -> Unit) =
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(texto)) }

@Composable
private fun Oferta(estado: CompraUiState, cotizacion: CotizacionResponse, acciones: CompraAcciones) = Pantalla {
    val locale = LocalConfiguration.current.locales[0]
    Titulo(R.string.compra_oferta_titulo)
    ResumenOferta(cotizacion, locale)
    Text(stringResource(R.string.compra_terminos_titulo), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.compra_terminos_texto))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = estado.aceptaTerminos, role = Role.Checkbox, onValueChange = acciones.aceptarTerminos),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = estado.aceptaTerminos, onCheckedChange = null)
        Text(stringResource(R.string.compra_terminos_acepto), modifier = Modifier.padding(start = 8.dp))
    }
    if (estado.terminosPendientes) {
        Text(
            stringResource(R.string.compra_terminos_pendiente),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    Boton(R.string.compra_continuar, acciones.continuarAPago)
}

@Composable
private fun ResumenOferta(cotizacion: CotizacionResponse, locale: Locale) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            com.solventa.mobile.cotizacion.Catalogo.porId(cotizacion.producto)?.let {
                Text(stringResource(R.string.compra_producto) + ": " + stringResource(it.nombre), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                stringResource(R.string.cotizacion_prima_anual) + ": " + FormatoCotizacion.moneda(cotizacion.primaAnual, cotizacion.moneda, locale),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(R.string.cotizacion_prima_mensual) + ": " +
                    FormatoCotizacion.moneda(cotizacion.primaMensual, cotizacion.moneda, locale),
            )
            Text(stringResource(R.string.cotizacion_vigencia, FormatoCotizacion.vigencia(cotizacion.vigenteHasta, locale)))
            cotizacion.coberturas.forEach {
                Text(
                    stringResource(R.string.cotizacion_cobertura_monto, FormatoCotizacion.moneda(it.monto, cotizacion.moneda, locale)) +
                        " · " + it.tipo.replace('_', ' ').replaceFirstChar { c -> c.uppercase() },
                )
            }
        }
    }
}

@Composable
private fun Pago(estado: CompraUiState, cotizacion: CotizacionResponse, acciones: CompraAcciones) = Pantalla {
    val locale = LocalConfiguration.current.locales[0]
    val tarjeta = estado.tarjeta
    Titulo(R.string.compra_pago_titulo)
    Text(stringResource(R.string.compra_pago_seguro))
    // NumberPassword: el teclado no sugiere ni aprende lo que se escribe.
    val teclado = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
    OutlinedTextField(
        value = tarjeta.numero,
        onValueChange = { acciones.cambiarTarjeta(it, tarjeta.vencimiento, tarjeta.cvv) },
        label = { Text(stringResource(R.string.compra_tarjeta_numero)) },
        isError = ErrorTarjeta.NUMERO in estado.erroresTarjeta,
        supportingText = { if (ErrorTarjeta.NUMERO in estado.erroresTarjeta) Text(stringResource(R.string.compra_error_numero)) },
        keyboardOptions = teclado,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = tarjeta.vencimiento,
        onValueChange = { acciones.cambiarTarjeta(tarjeta.numero, formatearVencimiento(it), tarjeta.cvv) },
        label = { Text(stringResource(R.string.compra_tarjeta_vencimiento)) },
        isError = ErrorTarjeta.VENCIMIENTO in estado.erroresTarjeta,
        supportingText = { if (ErrorTarjeta.VENCIMIENTO in estado.erroresTarjeta) Text(stringResource(R.string.compra_error_vencimiento)) },
        keyboardOptions = teclado,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = tarjeta.cvv,
        onValueChange = { acciones.cambiarTarjeta(tarjeta.numero, tarjeta.vencimiento, it.take(4)) },
        label = { Text(stringResource(R.string.compra_tarjeta_cvv)) },
        isError = ErrorTarjeta.CVV in estado.erroresTarjeta,
        supportingText = { if (ErrorTarjeta.CVV in estado.erroresTarjeta) Text(stringResource(R.string.compra_error_cvv)) },
        keyboardOptions = teclado,
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = acciones.pagar, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.compra_pagar, FormatoCotizacion.moneda(cotizacion.primaAnual, cotizacion.moneda, locale)))
    }
    BotonSecundario(R.string.compra_volver, acciones.volverALaOferta)
}

/** `MM/AA`: inserta la barra sola después del mes. */
internal fun formatearVencimiento(entrada: String): String {
    val digitos = entrada.filter(Char::isDigit).take(4)
    return if (digitos.length <= 2) digitos else digitos.substring(0, 2) + "/" + digitos.substring(2)
}

@Composable
private fun Espera(@StringRes mensaje: Int) = Pantalla {
    Column(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator()
        Text(stringResource(mensaje))
    }
}

@Composable
private fun Progreso(paso: String) = Pantalla {
    Titulo(R.string.compra_progreso_titulo)
    Column(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator()
        Text(stringResource(R.string.compra_progreso_paso, stringResource(textoPaso(paso))))
    }
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
private fun Resultado(@StringRes titulo: Int, mensajes: List<Int>, accion: Pair<Int, () -> Unit>) = Pantalla {
    Column(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Titulo(titulo)
        mensajes.forEach { Text(stringResource(it)) }
    }
    Boton(accion.first, accion.second)
}

@Composable
private fun Falla(error: ErrorCompra, acciones: CompraAcciones) = Pantalla {
    Titulo(R.string.compra_error_titulo)
    Text(
        stringResource(
            when (error) {
                ErrorCompra.SIN_CONEXION -> R.string.compra_error_sin_conexion
                ErrorCompra.SERVICIO -> R.string.compra_error_servicio
                ErrorCompra.SESION_VENCIDA -> R.string.compra_error_sesion
                ErrorCompra.RESULTADO_DESCONOCIDO -> R.string.compra_error_desconocido
                ErrorCompra.OFERTA_YA_USADA -> R.string.compra_error_ya_usada
                ErrorCompra.SEGUIMIENTO_LENTO -> R.string.compra_error_lento
            },
        ),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    when (error) {
        ErrorCompra.SESION_VENCIDA -> Boton(R.string.compra_iniciar_sesion, acciones.sesionVencida)
        ErrorCompra.OFERTA_YA_USADA -> Boton(R.string.compra_cotizar_de_nuevo, acciones.cotizarDeNuevo)
        ErrorCompra.RESULTADO_DESCONOCIDO -> Boton(R.string.compra_verificar, acciones.reintentar)
        ErrorCompra.SEGUIMIENTO_LENTO -> Boton(R.string.compra_consultar, acciones.reintentar)
        else -> Boton(R.string.cotizacion_reintentar, acciones.reintentar)
    }
}
