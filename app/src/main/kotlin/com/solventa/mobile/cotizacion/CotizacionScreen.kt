package com.solventa.mobile.cotizacion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.solventa.mobile.R
import com.solventa.mobile.red.CoberturaResponse
import com.solventa.mobile.red.CotizacionResponse
import java.text.NumberFormat
import java.util.Locale

/** Lo que la pantalla puede pedirle al flujo; separado del ViewModel para poder probarla con estados fijos. */
data class CotizacionAcciones(
    val elegirProducto: (String) -> Unit = {},
    val cambiarValor: (String, String) -> Unit = { _, _ -> },
    val solicitar: () -> Unit = {},
    val reintentar: () -> Unit = {},
    val volverAProductos: () -> Unit = {},
    val nuevaCotizacion: () -> Unit = {},
    val compartirDatos: () -> Unit = {},
    val sesionVencida: () -> Unit = {},
)

@Composable
fun CotizacionRoute(
    viewModel: CotizacionViewModel,
    onCompartirDatos: () -> Unit,
    onSesionVencida: () -> Unit,
) {
    val estado by viewModel.uiState.collectAsState()
    CotizacionScreen(
        estado = estado,
        acciones = CotizacionAcciones(
            elegirProducto = viewModel::elegirProducto,
            cambiarValor = viewModel::cambiarValor,
            solicitar = viewModel::solicitar,
            reintentar = viewModel::reintentar,
            volverAProductos = viewModel::volverAProductos,
            nuevaCotizacion = viewModel::nuevaCotizacion,
            compartirDatos = onCompartirDatos,
            sesionVencida = onSesionVencida,
        ),
    )
}

@Composable
fun CotizacionScreen(estado: CotizacionUiState, acciones: CotizacionAcciones) {
    when (estado.fase) {
        FaseCotizacion.ELEGIR_PRODUCTO -> ElegirProducto(acciones)
        FaseCotizacion.FORMULARIO -> Formulario(estado, acciones)
        FaseCotizacion.CARGANDO -> Cargando()
        FaseCotizacion.RESULTADO -> estado.cotizacion?.let { Resultado(estado, it, acciones) }
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
private fun Titulo(texto: String) =
    Text(text = texto, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })

@Composable
private fun ElegirProducto(acciones: CotizacionAcciones) = Pantalla {
    Titulo(stringResource(R.string.cotizacion_elegir_titulo))
    Text(stringResource(R.string.cotizacion_elegir_ayuda))
    Catalogo.productos.forEach { producto ->
        Button(
            onClick = { acciones.elegirProducto(producto.id) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text(stringResource(R.string.cotizacion_producto_accion, stringResource(producto.nombre))) }
    }
}

@Composable
private fun Formulario(estado: CotizacionUiState, acciones: CotizacionAcciones) = Pantalla {
    val producto = estado.producto ?: return@Pantalla
    Titulo(stringResource(R.string.cotizacion_datos_titulo))
    Text(stringResource(producto.nombre), style = MaterialTheme.typography.titleMedium)
    estado.error?.let { Falla(it, acciones) }
    producto.campos.forEach { campo ->
        val valor = estado.valores[campo.clave].orEmpty()
        val error = mensajeDeError(campo, estado.errores[campo.clave], estado.erroresServidor.containsKey(campo.clave))
        when (campo) {
            is Campo.Entero -> OutlinedTextField(
                value = valor,
                onValueChange = { acciones.cambiarValor(campo.clave, it) },
                label = { Text(stringResource(campo.etiqueta)) },
                isError = error != null,
                supportingText = { error?.let { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            is Campo.Seleccion -> Seleccion(campo, valor, error) { acciones.cambiarValor(campo.clave, it) }
        }
    }
    Button(onClick = acciones.solicitar, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(stringResource(R.string.cotizacion_solicitar))
    }
    OutlinedButton(onClick = acciones.volverAProductos, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.cotizacion_cambiar_producto))
    }
}

@Composable
private fun Seleccion(campo: Campo.Seleccion, valor: String, error: String?, alElegir: (String) -> Unit) {
    Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(campo.etiqueta), style = MaterialTheme.typography.titleSmall)
        campo.opciones.forEach { opcion ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(selected = valor == opcion.valor, role = Role.RadioButton, onClick = { alElegir(opcion.valor) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = valor == opcion.valor, onClick = null)
                Text(stringResource(opcion.etiqueta), modifier = Modifier.padding(start = 12.dp))
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun mensajeDeError(campo: Campo, error: ErrorCampo?, rechazadoPorServidor: Boolean): String? {
    if (error == null) {
        return if (rechazadoPorServidor) stringResource(R.string.cotizacion_error_campo_servidor) else null
    }
    val numero = NumberFormat.getIntegerInstance(configuracionLocale())
    return when (error) {
        ErrorCampo.REQUERIDO -> stringResource(R.string.cotizacion_error_requerido)
        ErrorCampo.NO_ENTERO -> stringResource(R.string.cotizacion_error_entero)
        ErrorCampo.OPCION_INVALIDA -> stringResource(R.string.cotizacion_error_opcion)
        ErrorCampo.MENOR_AL_MINIMO ->
            stringResource(R.string.cotizacion_error_minimo, numero.format((campo as? Campo.Entero)?.min ?: 0))
        ErrorCampo.MAYOR_AL_MAXIMO ->
            stringResource(R.string.cotizacion_error_maximo, numero.format((campo as? Campo.Entero)?.max ?: 0))
    }
}

@Composable
private fun Falla(error: ErrorCotizacion, acciones: CotizacionAcciones) {
    val mensaje = when (error) {
        ErrorCotizacion.TIEMPO_AGOTADO -> R.string.cotizacion_error_tiempo
        ErrorCotizacion.SIN_CONEXION -> R.string.cotizacion_error_sin_conexion
        ErrorCotizacion.SERVICIO -> R.string.cotizacion_error_servicio
        ErrorCotizacion.SESION_VENCIDA -> R.string.cotizacion_error_sesion
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive },
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(mensaje), color = MaterialTheme.colorScheme.onErrorContainer)
            if (error == ErrorCotizacion.SESION_VENCIDA) {
                Button(onClick = acciones.sesionVencida, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.cotizacion_iniciar_sesion))
                }
            } else {
                Button(onClick = acciones.reintentar, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.cotizacion_reintentar))
                }
            }
        }
    }
}

@Composable
private fun Cargando() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            stringResource(R.string.cotizacion_cargando),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun Resultado(estado: CotizacionUiState, cotizacion: CotizacionResponse, acciones: CotizacionAcciones) = Pantalla {
    val locale = configuracionLocale()
    Titulo(stringResource(R.string.cotizacion_resultado_titulo))
    Catalogo.porId(cotizacion.producto)?.let { Text(stringResource(it.nombre), style = MaterialTheme.typography.titleMedium) }
    if (estado.esEstimacion) Aviso(cotizacion, estado.ofreceCompartirDatos, acciones)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.cotizacion_prima_anual), style = MaterialTheme.typography.labelLarge)
            Text(
                FormatoCotizacion.moneda(cotizacion.primaAnual, cotizacion.moneda, locale),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                stringResource(R.string.cotizacion_prima_mensual) + ": " +
                    FormatoCotizacion.moneda(cotizacion.primaMensual, cotizacion.moneda, locale),
            )
            Text(stringResource(R.string.cotizacion_vigencia, FormatoCotizacion.vigencia(cotizacion.vigenteHasta, locale)))
        }
    }
    Text(stringResource(R.string.cotizacion_coberturas), style = MaterialTheme.typography.titleMedium)
    cotizacion.coberturas.forEach { Cobertura(it, cotizacion.moneda, locale) }
    OutlinedButton(onClick = acciones.nuevaCotizacion, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.cotizacion_nueva))
    }
}

@Composable
private fun Aviso(cotizacion: CotizacionResponse, ofreceCompartir: Boolean, acciones: CotizacionAcciones) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.cotizacion_aviso_titulo), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(
                    if (cotizacion.degradado) R.string.cotizacion_aviso_degradado else R.string.cotizacion_aviso_sin_consentimiento,
                ),
            )
            if (ofreceCompartir) {
                Button(onClick = acciones.compartirDatos, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.cotizacion_compartir_datos))
                }
            }
        }
    }
}

@Composable
private fun Cobertura(cobertura: CoberturaResponse, moneda: String, locale: Locale) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(nombreCobertura(cobertura.tipo), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.cotizacion_cobertura_monto, FormatoCotizacion.moneda(cobertura.monto, moneda, locale)))
            Text(stringResource(R.string.cotizacion_cobertura_deducible, FormatoCotizacion.moneda(cobertura.deducible, moneda, locale)))
        }
    }
}

@Composable
private fun nombreCobertura(tipo: String): String =
    when (tipo) {
        "muerte" -> stringResource(R.string.cotizacion_cobertura_muerte)
        "incapacidad_total" -> stringResource(R.string.cotizacion_cobertura_incapacidad_total)
        "dano_accidental" -> stringResource(R.string.cotizacion_cobertura_dano_accidental)
        "robo" -> stringResource(R.string.cotizacion_cobertura_robo)
        else -> tipo.replace('_', ' ').replaceFirstChar { it.uppercase() } // cobertura nueva sin traducir
    }

@Composable
private fun configuracionLocale(): Locale = LocalConfiguration.current.locales[0]
