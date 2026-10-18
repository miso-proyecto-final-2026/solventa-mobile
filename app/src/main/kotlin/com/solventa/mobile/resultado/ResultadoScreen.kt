package com.solventa.mobile.resultado

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.solventa.mobile.R

private val ObjetivoTactil = 48.dp

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
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            // El lector de pantalla anuncia el resultado cuando cambia (por ejemplo, de revisión a aprobada).
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (val actual = estado) {
            ResultadoUiState.Cargando -> {
                CircularProgressIndicator()
                Text(stringResource(R.string.resultado_cargando))
            }
            is ResultadoUiState.Error -> {
                Titulo(stringResource(R.string.resultado_error_titulo))
                Text(stringResource(textoError(actual.error)))
                Accion(stringResource(R.string.resultado_reintentar_consulta), onClick = viewModel::actualizar)
            }
            is ResultadoUiState.Mostrando -> Resultado(actual, viewModel, onSalir)
        }
    }
}

@Composable
private fun Resultado(estado: ResultadoUiState.Mostrando, viewModel: ResultadoViewModel, onSalir: () -> Unit) {
    when (val pantalla = estado.pantalla) {
        PantallaResultado.Aprobada -> {
            Encabezado(Icons.Filled.CheckCircle, stringResource(R.string.resultado_aprobada_titulo))
            Text(stringResource(R.string.resultado_aprobada_mensaje))
            estado.errorAccion?.let {
                Text(stringResource(textoError(it)), color = MaterialTheme.colorScheme.error)
            }
            if (estado.procesando) {
                CircularProgressIndicator()
            } else {
                Accion(stringResource(R.string.resultado_aprobada_accion), onClick = viewModel::continuar)
            }
        }
        is PantallaResultado.RechazadaConReintentos -> {
            Encabezado(Icons.Filled.Warning, stringResource(R.string.resultado_rechazada_titulo))
            Text(stringResource(guiaDeCorreccion(pantalla.motivo)))
            Text(
                pluralStringResource(
                    R.plurals.resultado_intentos_restantes,
                    pantalla.intentosRestantes,
                    pantalla.intentosRestantes,
                ),
            )
            Accion(stringResource(R.string.resultado_rechazada_accion), onClick = viewModel::reintentar)
        }
        is PantallaResultado.RechazadaSinReintentos -> {
            Encabezado(Icons.Filled.Warning, stringResource(R.string.resultado_bloqueada_titulo))
            Text(stringResource(R.string.resultado_bloqueada_mensaje))
            Text(stringResource(R.string.resultado_bloqueada_canal, stringResource(R.string.resultado_canal_atencion)))
            pantalla.disponibleDesde?.let { instante ->
                val idioma = ConfigurationCompat.getLocales(LocalConfiguration.current)[0]
                    ?: java.util.Locale.getDefault()
                Text(stringResource(R.string.resultado_bloqueada_hora, formatearHora(instante, idioma)))
            }
            // Sin botón de reintento (criterio 3).
            Accion(stringResource(R.string.resultado_entendido), onClick = onSalir)
        }
        PantallaResultado.EnRevision -> {
            Encabezado(Icons.Filled.Info, stringResource(R.string.resultado_revision_titulo))
            Text(stringResource(R.string.resultado_revision_mensaje))
            Accion(stringResource(R.string.resultado_entendido), onClick = onSalir)
        }
        // La navegación sale de esta pantalla antes de mostrarla.
        PantallaResultado.Pendiente -> CircularProgressIndicator()
    }
}

@Composable
private fun Encabezado(icono: ImageVector, titulo: String) {
    // Icono decorativo: el título ya dice el resultado.
    Icon(icono, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
    Titulo(titulo)
}

@Composable
private fun Titulo(texto: String) {
    Text(
        text = texto,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun Accion(texto: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = ObjetivoTactil)) { Text(texto) }
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
