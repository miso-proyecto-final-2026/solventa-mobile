package com.solventa.mobile.bloqueo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.solventa.mobile.R
import com.solventa.mobile.biometria.AutenticadorBiometricPrompt
import com.solventa.mobile.biometria.buscarActividad
import com.solventa.mobile.ui.componentes.ProtegerPantalla
import com.solventa.mobile.ui.iconos.Iconos

data class BloqueoAcciones(
    val usarHuella: () -> Unit = {},
    val usarContrasena: () -> Unit = {},
)

/**
 * Bloqueo biométrico (HU05, B2 del diseño). Al aparecer pide la biometría sola; si el cliente cancela, queda esta
 * pantalla con el botón de la huella y «Usar contraseña». No muestra datos del cliente salvo el nombre de pila del saludo.
 */
@Composable
fun BloqueoRoute(
    viewModel: BloqueoViewModel,
    nombre: String?,
    onDesbloqueado: () -> Unit,
    onUsarContrasena: (MotivoContrasena) -> Unit,
) {
    val estado by viewModel.uiState.collectAsState()
    val contexto = LocalContext.current
    val titulo = stringResource(R.string.bloqueo_prompt_titulo)
    val subtitulo = stringResource(R.string.bloqueo_prompt_subtitulo)
    val cancelar = stringResource(R.string.biometria_prompt_cancelar)

    fun pedirBiometria() {
        contexto.buscarActividad()?.let {
            viewModel.autenticar(AutenticadorBiometricPrompt(it, titulo, subtitulo, cancelar, maxFallos = estado.intentosRestantes))
        }
    }
    // Al aparecer se pide sola (criterio 1); si el estado ya pide contraseña (cambio de biometrías) no hay nada que pedir.
    LaunchedEffect(Unit) { pedirBiometria() }
    LaunchedEffect(estado.fase) {
        when (estado.fase) {
            FaseBloqueo.DESBLOQUEADO -> onDesbloqueado()
            FaseBloqueo.CONTRASENA -> onUsarContrasena(estado.motivo ?: MotivoContrasena.ELEGIDA)
            else -> Unit
        }
    }
    BloqueoScreen(
        estado = estado,
        nombre = nombre,
        acciones = BloqueoAcciones(usarHuella = ::pedirBiometria, usarContrasena = viewModel::usarContrasena),
    )
}

@Composable
fun BloqueoScreen(estado: BloqueoUiState, nombre: String?, acciones: BloqueoAcciones) {
    ProtegerPantalla()
    Column(modifier = Modifier.fillMaxSize().padding(top = 24.dp, bottom = 16.dp)) {
        Spacer(Modifier.height(64.dp))
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Iconos.EscudoVisto, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Text(stringResource(R.string.bloqueo_marca), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (nombre.isNullOrBlank()) stringResource(R.string.bloqueo_saludo_sin_nombre) else stringResource(R.string.bloqueo_saludo, nombre),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.bloqueo_texto),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Button(
                onClick = acciones.usarHuella,
                enabled = estado.fase == FaseBloqueo.LISTO,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                modifier = Modifier.size(96.dp),
            ) {
                Icon(
                    Iconos.Huella,
                    contentDescription = stringResource(R.string.bloqueo_boton_biometria),
                    modifier = Modifier.size(52.dp),
                )
            }
            Text(
                stringResource(R.string.bloqueo_toca),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (estado.cancelado) {
                Text(
                    stringResource(R.string.bloqueo_cancelado),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (estado.fallidos > 0 && estado.fase != FaseBloqueo.CONTRASENA) {
                Text(
                    pluralStringResource(R.plurals.bloqueo_fallidos, estado.intentosRestantes, estado.intentosRestantes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp).semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }
        }
        Box(modifier = Modifier.padding(horizontal = 24.dp)) {
            OutlinedButton(
                onClick = acciones.usarContrasena,
                enabled = estado.fase == FaseBloqueo.LISTO,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(stringResource(R.string.bloqueo_usar_contrasena), style = MaterialTheme.typography.labelLarge) }
        }
    }
}
