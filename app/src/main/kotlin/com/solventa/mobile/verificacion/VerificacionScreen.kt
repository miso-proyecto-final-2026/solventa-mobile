package com.solventa.mobile.verificacion

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.solventa.mobile.R

private val ObjetivoTactil = 48.dp

/** Cámara real de la app; las pruebas de UI la sustituyen por una simulada. */
typealias ContenidoCamara = @Composable (frontal: Boolean, descripcion: String, onCaptura: (String) -> Unit) -> Unit

private val CamaraReal: ContenidoCamara = { frontal, descripcion, onCaptura ->
    CamaraX(frontal = frontal, descripcion = descripcion, onCaptura = onCaptura)
}

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
            )
            PasoVerificacion.REVERSO -> PasoCaptura(
                estado, viewModel, camara,
                titulo = R.string.verificacion_reverso_titulo,
                instruccion = R.string.verificacion_reverso_instruccion,
                descripcionCamara = R.string.verificacion_reverso_camara,
                frontal = false,
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

@Composable
private fun Progreso(paso: PasoVerificacion) {
    val numero = paso.numero ?: return
    val texto = stringResource(R.string.verificacion_progreso, numero, TOTAL_PASOS)
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = texto },
    ) {
        Text(texto, style = MaterialTheme.typography.labelLarge)
        LinearProgressIndicator(progress = { numero / TOTAL_PASOS.toFloat() }, modifier = Modifier.fillMaxWidth())
    }
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
private fun PasoPermiso(estado: VerificacionUiState, onSolicitarPermiso: () -> Unit, onAbrirAjustes: () -> Unit) {
    Titulo(stringResource(R.string.verificacion_titulo))
    Text(stringResource(R.string.verificacion_permiso_explicacion))
    if (estado.permisoDenegado) {
        // Criterio 1: si lo niega se explica cómo habilitarlo y no se avanza.
        Text(stringResource(R.string.verificacion_permiso_denegado), color = MaterialTheme.colorScheme.error)
        OutlinedButton(
            onClick = onAbrirAjustes,
            modifier = Modifier.fillMaxWidth().heightIn(min = ObjetivoTactil),
        ) { Text(stringResource(R.string.verificacion_abrir_ajustes)) }
    }
    Button(
        onClick = onSolicitarPermiso,
        modifier = Modifier.fillMaxWidth().heightIn(min = ObjetivoTactil),
    ) { Text(stringResource(R.string.verificacion_permitir_camara)) }
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
) {
    Titulo(stringResource(titulo))
    Text(stringResource(instruccion))
    if (reto) {
        // Reto de vida del proveedor simulado (HU02): girar a la izquierda.
        Text(
            stringResource(R.string.verificacion_reto_vida),
            style = MaterialTheme.typography.titleMedium,
        )
    }
    estado.error?.let { Text(stringResource(textoError(it)), color = MaterialTheme.colorScheme.error) }
    estado.problemaCalidad?.let {
        Text(stringResource(textoProblema(it)), color = MaterialTheme.colorScheme.error)
    }

    val pendiente = estado.capturaPendiente
    when {
        estado.analizando -> {
            CircularProgressIndicator()
            Text(stringResource(R.string.verificacion_analizando))
        }
        pendiente != null -> {
            Miniatura(pendiente, stringResource(R.string.verificacion_vista_previa), alto = 320)
            Button(
                onClick = viewModel::usarCaptura,
                modifier = Modifier.fillMaxWidth().heightIn(min = ObjetivoTactil),
            ) { Text(stringResource(R.string.verificacion_usar_foto)) }
            OutlinedButton(
                onClick = viewModel::repetirCaptura,
                modifier = Modifier.fillMaxWidth().heightIn(min = ObjetivoTactil),
            ) { Text(stringResource(R.string.verificacion_repetir)) }
        }
        else -> camara(frontal, stringResource(descripcionCamara), viewModel::onCapturada)
    }
}

@Composable
private fun PasoRevision(estado: VerificacionUiState, viewModel: VerificacionViewModel) {
    Titulo(stringResource(R.string.verificacion_revision_titulo))
    Text(stringResource(R.string.verificacion_revision_instruccion))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        estado.anverso?.let {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Miniatura(it, stringResource(R.string.verificacion_anverso_revision), alto = 160)
                OutlinedButton(
                    onClick = { viewModel.repetirParte(PasoVerificacion.ANVERSO) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = ObjetivoTactil),
                ) { Text(stringResource(R.string.verificacion_repetir_anverso)) }
            }
        }
        estado.reverso?.let {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Miniatura(it, stringResource(R.string.verificacion_reverso_revision), alto = 160)
                OutlinedButton(
                    onClick = { viewModel.repetirParte(PasoVerificacion.REVERSO) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = ObjetivoTactil),
                ) { Text(stringResource(R.string.verificacion_repetir_reverso)) }
            }
        }
    }
    Button(
        onClick = viewModel::continuarDesdeRevision,
        modifier = Modifier.fillMaxWidth().heightIn(min = ObjetivoTactil),
    ) { Text(stringResource(R.string.verificacion_continuar)) }
}

@Composable
private fun PasoEnviando(estado: VerificacionUiState, viewModel: VerificacionViewModel) {
    val error = estado.error
    if (error == null) {
        CircularProgressIndicator()
        Text(stringResource(R.string.verificacion_enviando))
    } else {
        Titulo(stringResource(R.string.verificacion_error_titulo))
        Text(stringResource(textoError(error)), color = MaterialTheme.colorScheme.error)
        // Las imágenes siguen en el teléfono: el reintento no obliga a capturarlas de nuevo.
        Button(
            onClick = viewModel::enviar,
            modifier = Modifier.fillMaxWidth().heightIn(min = ObjetivoTactil),
        ) { Text(stringResource(R.string.verificacion_reintentar_envio)) }
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
    Titulo(stringResource(R.string.verificacion_error_titulo))
    Text(stringResource(textoError(error)))
    OutlinedButton(
        onClick = viewModel::finalizar,
        modifier = Modifier.fillMaxWidth().heightIn(min = ObjetivoTactil),
    ) { Text(stringResource(R.string.verificacion_continuar)) }
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
