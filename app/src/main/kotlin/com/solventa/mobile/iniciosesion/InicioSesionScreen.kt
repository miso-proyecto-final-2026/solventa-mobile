package com.solventa.mobile.iniciosesion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.solventa.mobile.R
import com.solventa.mobile.ui.componentes.AvisoAdvertencia
import com.solventa.mobile.ui.componentes.BarraSuperior
import com.solventa.mobile.ui.componentes.BotonPrimario
import com.solventa.mobile.ui.componentes.BotonSecundario
import com.solventa.mobile.ui.componentes.CampoTexto
import com.solventa.mobile.ui.componentes.ProtegerPantalla
import com.solventa.mobile.ui.iconos.Iconos

data class InicioSesionAcciones(
    val onEmailChange: (String) -> Unit = {},
    val onPasswordChange: (String) -> Unit = {},
    val enviar: () -> Unit = {},
    val crearCuenta: () -> Unit = {},
    val atras: (() -> Unit)? = null,
)

@Composable
fun InicioSesionRoute(
    viewModel: InicioSesionViewModel,
    onSesionIniciada: () -> Unit,
    onCrearCuenta: () -> Unit,
    onAtras: (() -> Unit)? = null,
    /** Por qué se pide la contraseña (tras fallar la biometría o cambiar las biometrías); `null` si entró por su cuenta. */
    aviso: Int? = null,
) {
    val estado by viewModel.uiState.collectAsState()
    LaunchedEffect(estado.sesionIniciada) { if (estado.sesionIniciada) onSesionIniciada() }
    InicioSesionScreen(
        estado = estado,
        acciones = InicioSesionAcciones(
            onEmailChange = viewModel::onEmailChange,
            onPasswordChange = viewModel::onPasswordChange,
            enviar = viewModel::enviar,
            crearCuenta = onCrearCuenta,
            atras = onAtras,
        ),
        aviso = aviso,
    )
}

/**
 * Inicio de sesión con correo y contraseña (HU05). El lienzo de diseño solo trae el enlace «Ya tengo una cuenta» de
 * Registro; la pantalla se arma con los mismos componentes de Crear cuenta. Se protege contra capturas porque se
 * escribe la contraseña.
 */
@Composable
fun InicioSesionScreen(estado: InicioSesionUiState, acciones: InicioSesionAcciones, aviso: Int? = null) {
    ProtegerPantalla()
    var mostrarPassword by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        BarraSuperior(titulo = stringResource(R.string.iniciosesion_titulo), onAtras = acciones.atras)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                stringResource(R.string.iniciosesion_encabezado),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.iniciosesion_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            aviso?.let { AvisoAdvertencia(stringResource(it), Iconos.Informacion) }
            CampoTexto(
                valor = estado.email,
                onValorChange = acciones.onEmailChange,
                etiqueta = stringResource(R.string.iniciosesion_correo),
                teclado = KeyboardOptions(keyboardType = KeyboardType.Email),
                habilitado = !estado.cargando,
            )
            CampoTexto(
                valor = estado.password,
                onValorChange = acciones.onPasswordChange,
                etiqueta = stringResource(R.string.iniciosesion_contrasena),
                teclado = KeyboardOptions(keyboardType = KeyboardType.Password),
                transformacion = if (mostrarPassword) VisualTransformation.None else PasswordVisualTransformation(),
                habilitado = !estado.cargando,
                icono = {
                    IconButton(onClick = { mostrarPassword = !mostrarPassword }) {
                        Icon(
                            imageVector = if (mostrarPassword) Iconos.OjoTachado else Iconos.Ojo,
                            contentDescription = stringResource(
                                if (mostrarPassword) R.string.registro_ocultar_password else R.string.registro_mostrar_password,
                            ),
                        )
                    }
                },
            )
            estado.error?.let { error ->
                Text(
                    text = mensajeDe(error, estado.minutosBloqueo),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (estado.cargando) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    Text(stringResource(R.string.iniciosesion_cargando))
                }
            } else {
                BotonPrimario(stringResource(R.string.iniciosesion_boton), onClick = acciones.enviar)
            }
            BotonSecundario(stringResource(R.string.iniciosesion_crear_cuenta), onClick = acciones.crearCuenta, habilitado = !estado.cargando)
        }
    }
}

@Composable
private fun mensajeDe(error: ErrorInicioSesion, minutos: Int): String =
    when (error) {
        ErrorInicioSesion.CAMPOS_VACIOS -> stringResource(R.string.iniciosesion_error_campos)
        ErrorInicioSesion.CREDENCIALES -> stringResource(R.string.iniciosesion_error_credenciales)
        ErrorInicioSesion.BLOQUEADO -> pluralStringResource(R.plurals.iniciosesion_error_bloqueado, minutos, minutos)
        ErrorInicioSesion.SIN_CONEXION -> stringResource(R.string.iniciosesion_error_sin_conexion)
        ErrorInicioSesion.TIEMPO_AGOTADO -> stringResource(R.string.iniciosesion_error_tiempo)
        ErrorInicioSesion.SERVICIO -> stringResource(R.string.iniciosesion_error_servicio)
    }
