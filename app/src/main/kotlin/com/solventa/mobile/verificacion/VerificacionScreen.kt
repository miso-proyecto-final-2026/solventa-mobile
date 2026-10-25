package com.solventa.mobile.verificacion

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.solventa.mobile.R
import com.solventa.mobile.ui.componentes.Aviso
import com.solventa.mobile.ui.componentes.BarraSuperior
import com.solventa.mobile.ui.componentes.BotonPrimario
import com.solventa.mobile.ui.componentes.BotonSecundario
import com.solventa.mobile.ui.componentes.IndicadorPasos
import com.solventa.mobile.ui.componentes.Tarjeta
import com.solventa.mobile.ui.iconos.Iconos

/** Cámara real de la app; las pruebas de UI la sustituyen por una simulada. */
typealias ContenidoCamara = @Composable (frontal: Boolean, descripcion: String, onCaptura: (String) -> Unit) -> Unit

private val CamaraReal: ContenidoCamara = { frontal, descripcion, onCaptura ->
    CamaraX(frontal = frontal, descripcion = descripcion, onCaptura = onCaptura)
}

/**
 * Verificación de identidad (HU02, A2 y A3 del diseño): paso 2 de 3 del alta, con «Documento» (frente, reverso y
 * revisión) y «Prueba de vida» (selfie). El indicador interno de la historia (n de 4) se traduce a la barra del diseño.
 */
@Composable
fun VerificacionScreen(
    viewModel: VerificacionViewModel,
    permisoCamaraConcedido: Boolean,
    onSolicitarPermiso: () -> Unit,
    onAbrirAjustes: () -> Unit,
    onFinalizar: () -> Unit,
    camara: ContenidoCamara = CamaraReal,
) {
    val estado by viewModel.uiState.collectAsState()

    LaunchedEffect(permisoCamaraConcedido) {
        if (permisoCamaraConcedido) viewModel.onPermiso(true)
    }
    LaunchedEffect(estado.finalizado, estado.terminado) {
        if (estado.finalizado || estado.terminado) onFinalizar()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // El alta no tiene paso atrás: la barra solo lleva el título.
        BarraSuperior(titulo = stringResource(R.string.verificacion_titulo), onAtras = null)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Progreso(estado.paso)
            when (estado.paso) {
                PasoVerificacion.PERMISO -> PasoPermiso(estado, onSolicitarPermiso, onAbrirAjustes)
                PasoVerificacion.ANVERSO -> PasoCaptura(
                    estado, viewModel, camara,
                    titulo = R.string.verificacion_anverso_titulo,
                    instruccion = R.string.verificacion_anverso_instruccion,
                    descripcionCamara = R.string.verificacion_anverso_camara,
                    frontal = false,
                    parte = 1,
                )
                PasoVerificacion.REVERSO -> PasoCaptura(
                    estado, viewModel, camara,
                    titulo = R.string.verificacion_reverso_titulo,
                    instruccion = R.string.verificacion_reverso_instruccion,
                    descripcionCamara = R.string.verificacion_reverso_camara,
                    frontal = false,
                    parte = 2,
                )
                PasoVerificacion.REVISION -> PasoRevision(estado, viewModel)
                PasoVerificacion.SELFIE -> PasoCaptura(
                    estado, viewModel, camara,
                    titulo = R.string.verificacion_selfie_titulo,
                    instruccion = R.string.verificacion_selfie_instruccion,
                    descripcionCamara = R.string.verificacion_selfie_camara,
                    frontal = true,
                    reto = true,
                )
                PasoVerificacion.ENVIANDO -> PasoEnviando(estado, viewModel)
                PasoVerificacion.RESULTADO -> PasoResultado(estado, viewModel)
            }
        }
    }
}

/** Posición de la barra: el documento va de ≈ 55 % y la prueba de vida llega a 80 %, como en el diseño. */
internal fun progresoDeVerificacion(numero: Int): Float = 0.45f + 0.0875f * numero

@Composable
private fun Progreso(paso: PasoVerificacion) {
    val numero = paso.numero ?: return
    val nombre = if (paso == PasoVerificacion.SELFIE) {
        stringResource(R.string.verificacion_paso_vida)
    } else {
        stringResource(R.string.verificacion_paso_documento)
    }
    IndicadorPasos(paso = 2, total = 3, nombre = nombre, progreso = progresoDeVerificacion(numero))
}

@Composable
private fun Encabezado(titulo: String, apoyo: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = titulo,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        apoyo?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PasoPermiso(estado: VerificacionUiState, onSolicitarPermiso: () -> Unit, onAbrirAjustes: () -> Unit) {
    Encabezado(stringResource(R.string.verificacion_permiso_titulo), stringResource(R.string.verificacion_permiso_explicacion))
    if (estado.permisoDenegado) {
        // Criterio 1: si lo niega se explica cómo habilitarlo y no se avanza.
        Text(
            stringResource(R.string.verificacion_permiso_denegado),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        BotonSecundario(stringResource(R.string.verificacion_abrir_ajustes), onClick = onAbrirAjustes)
    }
    BotonPrimario(stringResource(R.string.verificacion_permitir_camara), onClick = onSolicitarPermiso)
}

@Composable
private fun PasoCaptura(
    estado: VerificacionUiState,
    viewModel: VerificacionViewModel,
    camara: ContenidoCamara,
    titulo: Int,
    instruccion: Int,
    descripcionCamara: Int,
    frontal: Boolean,
    reto: Boolean = false,
    parte: Int = 0,
) {
    Encabezado(stringResource(titulo), stringResource(instruccion))
    estado.error?.let { Text(stringResource(textoError(it)), color = MaterialTheme.colorScheme.error) }
    estado.problemaCalidad?.let {
        Text(
            stringResource(textoProblema(it)),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }

    val pendiente = estado.capturaPendiente
    when {
        estado.analizando -> {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.verificacion_analizando))
            }
        }
        pendiente != null -> {
            Miniatura(pendiente, stringResource(R.string.verificacion_vista_previa), alto = 320)
            BotonPrimario(stringResource(R.string.verificacion_usar_foto), onClick = viewModel::usarCaptura)
            BotonSecundario(stringResource(R.string.verificacion_repetir), onClick = viewModel::repetirCaptura)
        }
        else -> {
            camara(frontal, stringResource(descripcionCamara), viewModel::onCapturada)
            if (reto) {
                // Reto de vida del proveedor simulado (HU02): girar a la izquierda.
                PistaReto(stringResource(R.string.verificacion_reto_vida))
                Aviso(stringResource(R.string.verificacion_privacidad), Iconos.Candado)
            } else {
                if (parte == 1) Aviso(stringResource(R.string.verificacion_aviso_documento), Iconos.Informacion)
                ListaPartes(actual = parte)
            }
        }
    }
}

/** Instrucción activa del reto de vida, en una píldora con flecha (A3). */
@Composable
private fun PistaReto(texto: String) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(texto, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** «1 Frente del documento / 2 Reverso del documento», con la parte actual resaltada (A2). */
@Composable
private fun ListaPartes(actual: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(1 to R.string.verificacion_parte_frente, 2 to R.string.verificacion_parte_reverso).forEach { (numero, texto) ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val activa = numero == actual
                Surface(
                    shape = CircleShape,
                    color = if (activa) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (activa) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    border = if (activa) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.size(20.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Text("$numero", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Text(
                    stringResource(texto),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (activa) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PasoRevision(estado: VerificacionUiState, viewModel: VerificacionViewModel) {
    Encabezado(stringResource(R.string.verificacion_revision_titulo), stringResource(R.string.verificacion_revision_instruccion))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        estado.anverso?.let {
            Tarjeta(modifier = Modifier.weight(1f)) {
                Miniatura(it, stringResource(R.string.verificacion_anverso_revision), alto = 160)
                BotonSecundario(
                    stringResource(R.string.verificacion_repetir_anverso),
                    onClick = { viewModel.repetirParte(PasoVerificacion.ANVERSO) },
                )
            }
        }
        estado.reverso?.let {
            Tarjeta(modifier = Modifier.weight(1f)) {
                Miniatura(it, stringResource(R.string.verificacion_reverso_revision), alto = 160)
                BotonSecundario(
                    stringResource(R.string.verificacion_repetir_reverso),
                    onClick = { viewModel.repetirParte(PasoVerificacion.REVERSO) },
                )
            }
        }
    }
    BotonPrimario(stringResource(R.string.verificacion_continuar), onClick = viewModel::continuarDesdeRevision)
}

@Composable
private fun PasoEnviando(estado: VerificacionUiState, viewModel: VerificacionViewModel) {
    val error = estado.error
    if (error == null) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            CircularProgressIndicator()
            Text(stringResource(R.string.verificacion_enviando), style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        Encabezado(stringResource(R.string.verificacion_error_titulo))
        Text(
            stringResource(textoError(error)),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        // Las imágenes siguen en el teléfono: el reintento no obliga a capturarlas de nuevo.
        BotonPrimario(stringResource(R.string.verificacion_reintentar_envio), onClick = viewModel::enviar)
    }
}

@Composable
private fun PasoResultado(estado: VerificacionUiState, viewModel: VerificacionViewModel) {
    val error = estado.error
    // Con resultado la navegación pasa a la pantalla de HU03; aquí solo quedan los errores sin resultado.
    if (estado.terminado || error == null) {
        CircularProgressIndicator()
        return
    }
    Encabezado(stringResource(R.string.verificacion_error_titulo))
    Text(stringResource(textoError(error)))
    BotonSecundario(stringResource(R.string.verificacion_continuar), onClick = viewModel::finalizar)
}

@Composable
private fun Miniatura(ruta: String, descripcion: String, alto: Int) {
    val imagen = remember(ruta) { ImagenArchivo.cargar(ruta) }
    if (imagen != null) {
        Image(
            bitmap = imagen,
            contentDescription = descripcion,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().heightIn(max = alto.dp),
        )
    }
}

private fun textoProblema(problema: ProblemaImagen): Int = when (problema) {
    ProblemaImagen.OSCURA -> R.string.verificacion_problema_oscura
    ProblemaImagen.SOBREEXPUESTA -> R.string.verificacion_problema_sobreexpuesta
    ProblemaImagen.BORROSA -> R.string.verificacion_problema_borrosa
    ProblemaImagen.ILEGIBLE -> R.string.verificacion_problema_ilegible
}

private fun textoError(error: ErrorEnvio): Int = when (error) {
    ErrorEnvio.SIN_CONEXION -> R.string.verificacion_error_conexion
    ErrorEnvio.SIN_SESION -> R.string.verificacion_error_sesion
    ErrorEnvio.IMAGEN_INVALIDA -> R.string.verificacion_error_imagen
    ErrorEnvio.BLOQUEADO -> R.string.verificacion_bloqueado_titulo
    ErrorEnvio.YA_PROCESADA -> R.string.verificacion_error_ya_procesada
    ErrorEnvio.INESPERADO -> R.string.verificacion_error_inesperado
}
