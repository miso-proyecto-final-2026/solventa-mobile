package com.solventa.mobile.cotizacion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.clickable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.solventa.mobile.R
import com.solventa.mobile.red.CoberturaResponse
import com.solventa.mobile.red.CotizacionResponse
import com.solventa.mobile.sesion.SesionActual
import com.solventa.mobile.sesion.primerNombre
import com.solventa.mobile.ui.componentes.BarraSuperior
import com.solventa.mobile.ui.componentes.Aviso
import com.solventa.mobile.ui.componentes.BotonPrimario
import com.solventa.mobile.ui.componentes.BotonSecundario
import com.solventa.mobile.ui.componentes.CampoTexto
import com.solventa.mobile.ui.componentes.IconoCircular
import com.solventa.mobile.ui.componentes.Tarjeta
import com.solventa.mobile.ui.componentes.TarjetaDestacada
import com.solventa.mobile.ui.iconos.Iconos
import com.solventa.mobile.ui.tema.extra
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
    val aceptarOferta: (CotizacionResponse) -> Unit = {},
    val modificarDatos: () -> Unit = {},
    val compartirDatos: () -> Unit = {},
    val sesionVencida: () -> Unit = {},
)

@Composable
fun CotizacionRoute(
    viewModel: CotizacionViewModel,
    onCompartirDatos: () -> Unit,
    onSesionVencida: () -> Unit,
    onAceptarOferta: (CotizacionResponse) -> Unit = {},
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
            aceptarOferta = onAceptarOferta,
            modificarDatos = viewModel::modificarDatos,
            compartirDatos = onCompartirDatos,
            sesionVencida = onSesionVencida,
        ),
    )
}

@Composable
fun CotizacionScreen(estado: CotizacionUiState, acciones: CotizacionAcciones, nombre: String? = SesionActual.nombre) {
    when (estado.fase) {
        FaseCotizacion.ELEGIR_PRODUCTO -> ElegirProducto(acciones, primerNombre(nombre))
        FaseCotizacion.FORMULARIO -> Formulario(estado, acciones)
        FaseCotizacion.CARGANDO -> Cargando()
        FaseCotizacion.RESULTADO -> estado.cotizacion?.let { Resultado(estado, it, acciones) }
    }
}

/** Zona desplazable con su barra superior opcional y los botones de decisión fijos al pie. */
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
private fun Titular(texto: String, apoyo: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(texto, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        apoyo?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Inicio (D1 del diseño): saludo por nombre y «Seguros disponibles»; sin póliza activa ni notificaciones aún. */
@Composable
private fun ElegirProducto(acciones: CotizacionAcciones, nombre: String?) =
    Estructura(titulo = null, onAtras = null, pie = null) {
        Titular(
            texto = nombre?.let { stringResource(R.string.cotizacion_saludo, it) } ?: stringResource(R.string.cotizacion_elegir_titulo),
            apoyo = stringResource(R.string.cotizacion_elegir_ayuda),
        )
        Text(
            stringResource(R.string.cotizacion_seguros_disponibles),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Catalogo.productos.forEach { producto ->
            val nombreProducto = stringResource(producto.nombre)
            val accion = stringResource(R.string.cotizacion_producto_accion, nombreProducto)
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .clickable { acciones.elegirProducto(producto.id) }
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        contentDescription = accion
                    },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconoCircular(
                        Iconos.Escudo,
                        descripcion = null,
                        contenedor = MaterialTheme.colorScheme.primaryContainer,
                        contenido = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(nombreProducto, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
            }
        }
    }

/** Datos de la cotización (D2): los campos de cada producto con el estilo del diseño. */
@Composable
private fun Formulario(estado: CotizacionUiState, acciones: CotizacionAcciones) {
    val producto = estado.producto ?: return
    Estructura(
        titulo = stringResource(R.string.cotizacion_barra_titulo, stringResource(producto.nombre)),
        onAtras = acciones.volverAProductos,
        pie = { BotonPrimario(stringResource(R.string.cotizacion_solicitar), onClick = acciones.solicitar) },
    ) {
        Titular(stringResource(R.string.cotizacion_datos_titulo), stringResource(producto.nombre))
        estado.error?.let { Falla(it, acciones) }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            producto.campos.forEach { campo ->
                val valor = estado.valores[campo.clave].orEmpty()
                val error = mensajeDeError(campo, estado.errores[campo.clave], estado.erroresServidor.containsKey(campo.clave))
                when (campo) {
                    is Campo.Entero -> CampoTexto(
                        valor = valor,
                        onValorChange = { acciones.cambiarValor(campo.clave, it) },
                        etiqueta = stringResource(campo.etiqueta),
                        error = error,
                        teclado = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )

                    is Campo.Seleccion -> Seleccion(campo, valor, error) { acciones.cambiarValor(campo.clave, it) }
                }
            }
        }
        BotonSecundario(stringResource(R.string.cotizacion_cambiar_producto), onClick = acciones.volverAProductos)
    }
}

@Composable
private fun Seleccion(campo: Campo.Seleccion, valor: String, error: String?, alElegir: (String) -> Unit) {
    Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(campo.etiqueta),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        campo.opciones.forEach { opcion ->
            val elegida = valor == opcion.valor
            Surface(
                shape = MaterialTheme.shapes.large,
                color = if (elegida) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .selectable(selected = elegida, role = Role.RadioButton, onClick = { alElegir(opcion.valor) }),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RadioButton(selected = elegida, onClick = null)
                    Text(stringResource(opcion.etiqueta), style = MaterialTheme.typography.bodyLarge)
                }
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
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive },
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(mensaje))
            if (error == ErrorCotizacion.SESION_VENCIDA) {
                BotonPrimario(stringResource(R.string.cotizacion_iniciar_sesion), onClick = acciones.sesionVencida)
            } else {
                BotonPrimario(stringResource(R.string.cotizacion_reintentar), onClick = acciones.reintentar)
            }
        }
    }
}

/** Calculando precio (D3): el servicio no informa avances, así que solo hay progreso indeterminado. */
@Composable
private fun Cargando() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(
                stringResource(R.string.cotizacion_cargando),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.cotizacion_cargando_apoyo),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Cotización lista (D4): precio destacado, coberturas, vigencia y «Contratar» fijo al pie. */
@Composable
private fun Resultado(estado: CotizacionUiState, cotizacion: CotizacionResponse, acciones: CotizacionAcciones) =
    Estructura(
        titulo = stringResource(R.string.cotizacion_resultado_titulo),
        onAtras = acciones.modificarDatos,
        pie = {
            BotonPrimario(stringResource(R.string.cotizacion_aceptar), onClick = { acciones.aceptarOferta(cotizacion) })
            BotonSecundario(stringResource(R.string.cotizacion_modificar), onClick = acciones.modificarDatos)
        },
    ) {
        val locale = configuracionLocale()
        if (estado.esEstimacion) AvisoEstimacion(cotizacion, estado.ofreceCompartirDatos, acciones)
        TarjetaDestacada {
            Catalogo.porId(cotizacion.producto)?.let {
                Text(stringResource(it.nombre), style = MaterialTheme.typography.titleSmall)
            }
            Text(stringResource(R.string.cotizacion_prima_anual), style = MaterialTheme.typography.bodyMedium)
            Text(
                FormatoCotizacion.moneda(cotizacion.primaAnual, cotizacion.moneda, locale),
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 48.sp, lineHeight = 56.sp),
            )
            Text(
                stringResource(
                    if (estado.esEstimacion) R.string.cotizacion_precio_estimado else R.string.cotizacion_precio_personalizado,
                    cotizacion.moneda,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.cotizacion_prima_mensual) + ": " +
                    FormatoCotizacion.moneda(cotizacion.primaMensual, cotizacion.moneda, locale),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Tarjeta {
            Text(
                stringResource(R.string.cotizacion_coberturas).uppercase(locale),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            cotizacion.coberturas.forEachIndexed { i, cobertura ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Cobertura(cobertura, cotizacion.moneda, locale)
            }
        }
        Aviso(
            texto = stringResource(R.string.cotizacion_vigencia, FormatoCotizacion.vigencia(cotizacion.vigenteHasta, locale)),
            icono = Iconos.Informacion,
        )
        BotonSecundario(stringResource(R.string.cotizacion_nueva), onClick = acciones.nuevaCotizacion)
    }

/** Aviso de estimación (sin consentimiento o con proveedores caídos), con la acción de compartir los datos. */
@Composable
private fun AvisoEstimacion(cotizacion: CotizacionResponse, ofreceCompartir: Boolean, acciones: CotizacionAcciones) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.extra.advertenciaContenedor,
        contentColor = MaterialTheme.extra.onAdvertencia,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.cotizacion_aviso_titulo), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(
                    if (cotizacion.degradado) R.string.cotizacion_aviso_degradado else R.string.cotizacion_aviso_sin_consentimiento,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (ofreceCompartir) {
                BotonPrimario(stringResource(R.string.cotizacion_compartir_datos), onClick = acciones.compartirDatos)
            }
        }
    }
}

@Composable
private fun Cobertura(cobertura: CoberturaResponse, moneda: String, locale: Locale) {
    Column(modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(nombreCobertura(cobertura.tipo), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                FormatoCotizacion.moneda(cobertura.monto, moneda, locale),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            )
        }
        Text(
            stringResource(R.string.cotizacion_cobertura_deducible, FormatoCotizacion.moneda(cobertura.deducible, moneda, locale)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
