package com.solventa.mobile.resultado

import androidx.annotation.StringRes
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.solventa.mobile.R
import com.solventa.mobile.ui.componentes.BloqueResultado
import com.solventa.mobile.ui.componentes.BotonPrimario
import com.solventa.mobile.ui.componentes.Tarjeta
import com.solventa.mobile.ui.iconos.Iconos
import com.solventa.mobile.ui.tema.SolventaColorScheme
import com.solventa.mobile.ui.tema.extra

/** Consulta el estado cada vez que la pantalla vuelve a primer plano (criterios 4 y 5). */
@Composable
fun ResultadoRoute(viewModel: ResultadoViewModel, onDestino: (Destino) -> Unit, onSalir: () -> Unit) {
    val ciclo = LocalLifecycleOwner.current
    DisposableEffect(ciclo) {
        val observador = LifecycleEventObserver { _, evento ->
            if (evento == Lifecycle.Event.ON_RESUME) viewModel.actualizar()
        }
        ciclo.lifecycle.addObserver(observador)
        onDispose { ciclo.lifecycle.removeObserver(observador) }
    }
    ResultadoScreen(viewModel = viewModel, onDestino = onDestino, onSalir = onSalir)
}

/**
 * Resultado de la verificación (HU03, A4 y A5 del diseño): círculo con ícono, título, mensaje y una sola acción al
 * pie. Aprobada, rechazada, sin intentos y en revisión comparten la misma estructura.
 */
@Composable
fun ResultadoScreen(viewModel: ResultadoViewModel, onDestino: (Destino) -> Unit, onSalir: () -> Unit) {
    val estado by viewModel.uiState.collectAsState()
    val destino by viewModel.destino.collectAsState()

    LaunchedEffect(destino) {
        destino?.let {
            viewModel.destinoAtendido()
            onDestino(it)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // El lector de pantalla anuncia el resultado cuando cambia (por ejemplo, de revisión a aprobada).
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        when (val actual = estado) {
            ResultadoUiState.Cargando -> Centrado {
                CircularProgressIndicator()
                Text(stringResource(R.string.resultado_cargando))
            }
            is ResultadoUiState.Error -> {
                Centrado {
                    Text(
                        stringResource(R.string.resultado_error_titulo),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(stringResource(textoError(actual.error)), textAlign = TextAlign.Center)
                }
                Pie { BotonPrimario(stringResource(R.string.resultado_reintentar_consulta), onClick = viewModel::actualizar) }
            }
            is ResultadoUiState.Mostrando -> Resultado(actual, viewModel, onSalir)
        }
    }
}

/** Zona central, con el contenido centrado y desplazable si la pantalla es pequeña. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Centrado(contenido: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 24.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) { contenido() }
}

@Composable
private fun Pie(contenido: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { contenido() }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Resultado(
    estado: ResultadoUiState.Mostrando,
    viewModel: ResultadoViewModel,
    onSalir: () -> Unit,
) {
    when (val pantalla = estado.pantalla) {
        PantallaResultado.Aprobada -> {
            Centrado {
                Bloque(
                    icono = Iconos.Check,
                    exito = true,
                    titulo = R.string.resultado_aprobada_titulo,
                    mensaje = stringResource(R.string.resultado_aprobada_mensaje),
                ) { ListaChequeo() }
            }
            Pie {
                estado.errorAccion?.let {
                    Text(stringResource(textoError(it)), color = MaterialTheme.colorScheme.error)
                }
                Text(
                    stringResource(R.string.resultado_aprobada_paso),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (estado.procesando) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    BotonPrimario(stringResource(R.string.resultado_aprobada_accion), onClick = viewModel::continuar)
                }
            }
        }
        is PantallaResultado.RechazadaConReintentos -> {
            Centrado {
                Bloque(
                    icono = Iconos.Exclamacion,
                    exito = false,
                    titulo = R.string.resultado_rechazada_titulo,
                    mensaje = stringResource(guiaDeCorreccion(pantalla.motivo)),
                ) {
                    Consejos()
                    Text(
                        pluralStringResource(
                            R.plurals.resultado_intentos_restantes,
                            pantalla.intentosRestantes,
                            pantalla.intentosRestantes,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Pie { BotonPrimario(stringResource(R.string.resultado_rechazada_accion), onClick = viewModel::reintentar) }
        }
        is PantallaResultado.RechazadaSinReintentos -> {
            Centrado {
                Bloque(
                    icono = Iconos.Exclamacion,
                    exito = false,
                    titulo = R.string.resultado_bloqueada_titulo,
                    mensaje = stringResource(R.string.resultado_bloqueada_mensaje),
                ) {
                    Text(
                        stringResource(R.string.resultado_bloqueada_canal, stringResource(R.string.resultado_canal_atencion)),
                        textAlign = TextAlign.Center,
                    )
                    pantalla.disponibleDesde?.let { instante ->
                        val idioma = ConfigurationCompat.getLocales(LocalConfiguration.current)[0]
                            ?: java.util.Locale.getDefault()
                        Text(
                            stringResource(R.string.resultado_bloqueada_hora, formatearHora(instante, idioma)),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            // Sin botón de reintento (criterio 3).
            Pie { BotonPrimario(stringResource(R.string.resultado_entendido), onClick = onSalir) }
        }
        PantallaResultado.EnRevision -> {
            Centrado {
                Bloque(
                    icono = Iconos.Informacion,
                    exito = null,
                    titulo = R.string.resultado_revision_titulo,
                    mensaje = stringResource(R.string.resultado_revision_mensaje),
                )
            }
            Pie { BotonPrimario(stringResource(R.string.resultado_entendido), onClick = onSalir) }
        }
        // La navegación sale de esta pantalla antes de mostrarla.
        PantallaResultado.Pendiente -> Centrado { CircularProgressIndicator() }
    }
}

/** `exito`: verde; `false`: rojo del diseño; `null`: neutro (revisión). */
@Composable
private fun Bloque(
    icono: ImageVector,
    exito: Boolean?,
    @StringRes titulo: Int,
    mensaje: String,
    contenido: @Composable () -> Unit = {},
) {
    val (fondo, color) = when (exito) {
        true -> SolventaColorScheme.tertiaryContainer to SolventaColorScheme.onTertiaryContainer
        false -> SolventaColorScheme.errorContainer to SolventaColorScheme.onErrorContainer
        null -> SolventaColorScheme.primaryContainer to SolventaColorScheme.onPrimaryContainer
    }
    BloqueResultado(
        icono = icono,
        descripcionIcono = stringResource(
            when (exito) {
                true -> R.string.resultado_icono_exito
                false -> R.string.resultado_icono_aviso
                null -> R.string.resultado_icono_info
            },
        ),
        contenedor = fondo,
        colorIcono = color,
        titulo = stringResource(titulo),
        mensaje = mensaje,
        contenido = contenido,
    )
}

/**
 * Qué se comprobó (A4). MS Identidad aprueba solo si el rostro coincide con el documento y se supera la prueba de
 * vida, así que un resultado `VERIFICADO` implica los tres puntos.
 */
@Composable
private fun ListaChequeo() {
    val filas = listOf(
        R.string.resultado_chequeo_documento,
        R.string.resultado_chequeo_vida,
        R.string.resultado_chequeo_rostro,
    )
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            filas.forEachIndexed { i, texto ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Decorativo: cada fila ya dice qué se comprobó.
                    Icon(Iconos.Check, contentDescription = null, tint = MaterialTheme.extra.exitoIcono, modifier = Modifier.size(20.dp))
                    Text(stringResource(texto), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** «Para el próximo intento»: consejos generales de captura, no un diagnóstico de este rechazo. */
@Composable
private fun Consejos() {
    Tarjeta {
        Text(
            stringResource(R.string.resultado_consejos_titulo),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        listOf(Iconos.Luz to R.string.resultado_consejo_luz, Iconos.Documento to R.string.resultado_consejo_reflejos)
            .forEach { (icono, texto) ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icono, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(stringResource(texto), style = MaterialTheme.typography.bodyMedium)
                }
            }
    }
}

/**
 * Texto de qué corregir según el código genérico de HU02. Hoy MS Identidad solo emite
 * `VERIFICACION_NO_SUPERADA`, que a propósito no distingue documento de prueba de vida, así que
 * todo rechazo recibe la misma guía en lenguaje simple.
 */
@StringRes
fun guiaDeCorreccion(@Suppress("UNUSED_PARAMETER") motivo: String?): Int = R.string.resultado_rechazada_guia

@StringRes
private fun textoError(error: ErrorConsulta): Int = when (error) {
    ErrorConsulta.SIN_CONEXION -> R.string.resultado_error_conexion
    ErrorConsulta.INESPERADO -> R.string.resultado_error_inesperado
}
