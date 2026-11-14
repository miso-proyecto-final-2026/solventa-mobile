package com.solventa.mobile.biometria

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.solventa.mobile.R
import com.solventa.mobile.sesion.SesionActual
import com.solventa.mobile.ui.componentes.BotonPrimario
import com.solventa.mobile.ui.iconos.Iconos
import kotlinx.coroutines.launch

data class InvitacionAcciones(
    val activar: () -> Unit = {},
    val ahoraNo: () -> Unit = {},
    val elegirMetodo: (MetodoBiometrico) -> Unit = {},
    val cancelarFormulario: () -> Unit = {},
    val confirmarActivacion: (contrasena: String) -> Unit = {},
)

/**
 * Invitación a activar el acceso biométrico tras aprobar el KYC (B1 del diseño). Si el dispositivo no puede, el cliente
 * ya lo vio o no se pudo consultar, no se muestra: continúa a Inicio sin que lo note. «Ahora no» no activa nada y no
 * vuelve a preguntar; el cliente puede activarlo cuando quiera en Perfil → Seguridad.
 */
@Composable
fun InvitacionRoute(
    viewModel: BiometriaViewModel,
    vista: VistaInvitacion,
    onContinuar: () -> Unit,
) {
    val estado by viewModel.uiState.collectAsState()
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    val titulo = stringResource(R.string.biometria_prompt_titulo)
    val subtitulo = stringResource(R.string.biometria_prompt_subtitulo)
    val cancelar = stringResource(R.string.biometria_prompt_cancelar)
    val clienteId = SesionActual.clienteId

    // Decide una sola vez, cuando termina de consultar: invitar, o seguir sin mostrar nada.
    var decidido by remember { mutableStateOf(false) }
    var invitar by remember { mutableStateOf(false) }
    LaunchedEffect(estado.fase) {
        if (decidido || estado.fase == FaseBiometria.CARGANDO) return@LaunchedEffect
        decidido = true
        val yaVista = clienteId == null || vista.vista(clienteId)
        if (debeInvitar(estado, yaVista)) invitar = true else onContinuar()
    }
    // Al activarlo no se vuelve a preguntar y se sigue a Inicio.
    LaunchedEffect(estado.aviso) {
        if (estado.aviso == AvisoAcceso.ACTIVADO) {
            clienteId?.let { vista.marcar(it) }
            onContinuar()
        }
    }

    if (!invitar) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    InvitacionScreen(
        estado = estado,
        acciones = InvitacionAcciones(
            activar = viewModel::abrirFormulario,
            ahoraNo = {
                alcance.launch {
                    clienteId?.let { vista.marcar(it) }
                    onContinuar()
                }
            },
            elegirMetodo = viewModel::elegirMetodo,
            cancelarFormulario = viewModel::cancelarFormulario,
            confirmarActivacion = { contrasena ->
                contexto.buscarActividad()?.let {
                    viewModel.confirmarActivacion(contrasena, AutenticadorBiometricPrompt(it, titulo, subtitulo, cancelar))
                }
            },
        ),
    )
}

/** Pantalla B1: círculo de 120 dp con la huella, título de 28 sp, dos garantías con ícono y el botón principal. */
@Composable
fun InvitacionScreen(estado: BiometriaUiState, acciones: InvitacionAcciones) {
    Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp, bottom = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = acciones.ahoraNo, modifier = Modifier.heightIn(min = 48.dp).align(Alignment.CenterVertically)) {
                Text(stringResource(R.string.biometria_ahora_no), style = MaterialTheme.typography.labelLarge)
            }
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(120.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Iconos.Huella,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(64.dp),
                    )
                }
            }
            Text(
                stringResource(R.string.biometria_invitacion_titulo),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.biometria_invitacion_texto),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Garantia(Iconos.Candado, R.string.biometria_nota_local)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Garantia(Iconos.Escudo, R.string.biometria_nota_contrasena_ajustes)
                }
            }
            estado.error?.takeIf { it != ErrorAcceso.CONTRASENA_VACIA && it != ErrorAcceso.CONTRASENA_INCORRECTA }?.let {
                Text(
                    stringResource(mensajeDe(it)),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }
        }
        BotonPrimario(
            stringResource(R.string.biometria_activar),
            onClick = acciones.activar,
            modifier = Modifier.padding(horizontal = 24.dp),
            habilitado = estado.fase == FaseBiometria.ESTADO,
        )
    }
    if (estado.fase == FaseBiometria.FORMULARIO) {
        Formulario(
            estado,
            SeguridadAcciones(
                elegirMetodo = acciones.elegirMetodo,
                cancelarFormulario = acciones.cancelarFormulario,
                confirmarActivacion = acciones.confirmarActivacion,
            ),
        )
    }
    if (estado.fase == FaseBiometria.PROCESANDO) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

@Composable
private fun Garantia(icono: ImageVector, texto: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icono, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Text(stringResource(texto), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun mensajeDe(error: ErrorAcceso): Int =
    when (error) {
        ErrorAcceso.TIEMPO_AGOTADO -> R.string.biometria_error_tiempo
        ErrorAcceso.SIN_CONEXION -> R.string.biometria_error_sin_conexion
        ErrorAcceso.SESION_VENCIDA -> R.string.biometria_error_sesion
        ErrorAcceso.KYC_NO_APROBADO -> R.string.biometria_bloqueo_kyc
        ErrorAcceso.CANCELADO -> R.string.biometria_error_cancelado
        ErrorAcceso.AUTENTICACION -> R.string.biometria_error_autenticacion
        else -> R.string.biometria_error_servicio
    }
