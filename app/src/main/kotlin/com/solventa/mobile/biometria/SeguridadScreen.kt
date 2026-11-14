package com.solventa.mobile.biometria

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.solventa.mobile.R
import com.solventa.mobile.ui.componentes.AvisoAdvertencia
import com.solventa.mobile.ui.componentes.AvisoExito
import com.solventa.mobile.ui.componentes.BarraSuperior
import com.solventa.mobile.ui.componentes.BotonDestructivo
import com.solventa.mobile.ui.componentes.BotonDestructivoContorno
import com.solventa.mobile.ui.componentes.BotonPrimario
import com.solventa.mobile.ui.componentes.CampoTexto
import com.solventa.mobile.ui.componentes.ChipEstado
import com.solventa.mobile.ui.componentes.Tarjeta
import com.solventa.mobile.ui.componentes.TipoEstado
import com.solventa.mobile.ui.iconos.Iconos

/** Lo que la pantalla puede pedirle al flujo; separado del ViewModel para poder probarla con estados fijos. */
data class SeguridadAcciones(
    val abrirFormulario: () -> Unit = {},
    val elegirMetodo: (MetodoBiometrico) -> Unit = {},
    val cancelarFormulario: () -> Unit = {},
    val confirmarActivacion: (contrasena: String) -> Unit = {},
    val pedirDesactivar: () -> Unit = {},
    val cancelarDesactivar: () -> Unit = {},
    val confirmarDesactivar: () -> Unit = {},
    val reintentar: () -> Unit = {},
    val atras: () -> Unit = {},
    val sesionVencida: () -> Unit = {},
)

@Composable
fun SeguridadRoute(viewModel: BiometriaViewModel, onAtras: () -> Unit, onSesionVencida: () -> Unit) {
    val estado by viewModel.uiState.collectAsState()
    val contexto = LocalContext.current
    val titulo = stringResource(R.string.biometria_prompt_titulo)
    val subtitulo = stringResource(R.string.biometria_prompt_subtitulo)
    val cancelar = stringResource(R.string.biometria_prompt_cancelar)
    SeguridadScreen(
        estado = estado,
        acciones = SeguridadAcciones(
            abrirFormulario = viewModel::abrirFormulario,
            elegirMetodo = viewModel::elegirMetodo,
            cancelarFormulario = viewModel::cancelarFormulario,
            confirmarActivacion = { contrasena ->
                val actividad = contexto.buscarActividad()
                if (actividad != null) {
                    viewModel.confirmarActivacion(contrasena, AutenticadorBiometricPrompt(actividad, titulo, subtitulo, cancelar))
                }
            },
            pedirDesactivar = viewModel::pedirDesactivar,
            cancelarDesactivar = viewModel::cancelarDesactivar,
            confirmarDesactivar = viewModel::confirmarDesactivar,
            reintentar = viewModel::reintentar,
            atras = onAtras,
            sesionVencida = onSesionVencida,
        ),
    )
}

private fun android.content.Context.buscarActividad(): FragmentActivity? {
    var actual: android.content.Context? = this
    while (actual is android.content.ContextWrapper) {
        if (actual is FragmentActivity) return actual
        actual = actual.baseContext
    }
    return null
}

/**
 * Seguridad (HU04, B1 del diseño): activa o desactiva el acceso biométrico. Dice por qué no se puede cuando el
 * dispositivo no tiene biometría o el KYC no está aprobado, y deja claro que la biometría no sale del teléfono.
 */
@Composable
fun SeguridadScreen(estado: BiometriaUiState, acciones: SeguridadAcciones) {
    Column(modifier = Modifier.fillMaxSize()) {
        BarraSuperior(titulo = stringResource(R.string.biometria_titulo), onAtras = acciones.atras)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (estado.fase) {
                FaseBiometria.CARGANDO -> Progreso(R.string.biometria_cargando)
                FaseBiometria.PROCESANDO -> Progreso(R.string.biometria_procesando)
                else -> Contenido(estado, acciones)
            }
        }
    }
    when (estado.fase) {
        FaseBiometria.FORMULARIO -> Formulario(estado, acciones)
        FaseBiometria.CONFIRMANDO_DESACTIVAR -> ConfirmarDesactivar(acciones)
        else -> Unit
    }
}

@Composable
private fun Progreso(texto: Int) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(stringResource(texto), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@Composable
private fun Contenido(estado: BiometriaUiState, acciones: SeguridadAcciones) {
    estado.error?.takeIf { it != ErrorAcceso.CONTRASENA_VACIA && it != ErrorAcceso.CONTRASENA_INCORRECTA }
        ?.let { Falla(it, estado.pendiente, acciones) }
    estado.aviso?.let { Aviso(it) }
    Tarjeta {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Iconos.Huella, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.biometria_opcion),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.biometria_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (estado.activo) {
            ChipEstado(
                stringResource(R.string.biometria_chip_activo),
                Iconos.Check,
                TipoEstado.ACTIVO,
            )
            Text(
                stringResource(R.string.biometria_activo_con, nombreDe(estado.metodoActivo)),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            ChipEstado(stringResource(R.string.biometria_chip_inactivo), Iconos.Menos, TipoEstado.NEUTRO)
        }
        Nota(R.string.biometria_nota_local)
        Nota(R.string.biometria_nota_contrasena)
        val bloqueo = estado.bloqueo
        if (bloqueo != null) {
            AvisoAdvertencia(stringResource(motivoDe(bloqueo)), Iconos.Advertencia)
        }
        if (estado.activo) {
            BotonDestructivoContorno(stringResource(R.string.biometria_desactivar), onClick = acciones.pedirDesactivar)
        } else {
            BotonPrimario(
                stringResource(R.string.biometria_activar),
                onClick = acciones.abrirFormulario,
                habilitado = estado.puedeActivar,
            )
        }
    }
}

@Composable
private fun Nota(texto: Int) {
    Text(stringResource(texto), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun nombreDe(metodo: MetodoBiometrico?): String =
    stringResource(if (metodo == MetodoBiometrico.FACIAL) R.string.biometria_metodo_facial else R.string.biometria_metodo_huella)

private fun motivoDe(bloqueo: MotivoBloqueo): Int =
    when (bloqueo) {
        MotivoBloqueo.KYC_NO_APROBADO -> R.string.biometria_bloqueo_kyc
        MotivoBloqueo.SIN_SENSOR -> R.string.biometria_bloqueo_sin_sensor
        MotivoBloqueo.SIN_REGISTRO -> R.string.biometria_bloqueo_sin_registro
        MotivoBloqueo.NO_DISPONIBLE -> R.string.biometria_bloqueo_no_disponible
    }

@Composable
private fun Aviso(aviso: AvisoAcceso) {
    val modificador = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    when (aviso) {
        AvisoAcceso.ACTIVADO -> AvisoExito(stringResource(R.string.biometria_ok_activado), Iconos.Check, modificador)
        AvisoAcceso.DESACTIVADO -> AvisoExito(stringResource(R.string.biometria_ok_desactivado), Iconos.Check, modificador)
        AvisoAcceso.CLAVE_INVALIDADA ->
            AvisoAdvertencia(stringResource(R.string.biometria_clave_invalidada), Iconos.Advertencia, modificador)
    }
}

@Composable
private fun Falla(error: ErrorAcceso, pendiente: AccionAcceso?, acciones: SeguridadAcciones) {
    val mensaje = when (error) {
        ErrorAcceso.TIEMPO_AGOTADO -> R.string.biometria_error_tiempo
        ErrorAcceso.SIN_CONEXION -> R.string.biometria_error_sin_conexion
        ErrorAcceso.SESION_VENCIDA -> R.string.biometria_error_sesion
        ErrorAcceso.KYC_NO_APROBADO -> R.string.biometria_bloqueo_kyc
        ErrorAcceso.CANCELADO -> R.string.biometria_error_cancelado
        ErrorAcceso.AUTENTICACION -> R.string.biometria_error_autenticacion
        else -> R.string.biometria_error_servicio
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive },
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(mensaje))
            if (pendiente == AccionAcceso.DESACTIVAR) Text(stringResource(R.string.biometria_local_desactivado))
            when {
                error == ErrorAcceso.SESION_VENCIDA ->
                    BotonPrimario(stringResource(R.string.biometria_iniciar_sesion), onClick = acciones.sesionVencida)
                pendiente != null -> BotonPrimario(stringResource(R.string.biometria_reintentar), onClick = acciones.reintentar)
            }
        }
    }
}

/** Elección del método (criterio 3) y confirmación con la contraseña actual (criterio 4). */
@Composable
private fun Formulario(estado: BiometriaUiState, acciones: SeguridadAcciones) {
    var contrasena by remember { mutableStateOf("") }
    var mostrar by remember { mutableStateOf(false) }
    val error = when (estado.error) {
        ErrorAcceso.CONTRASENA_VACIA -> stringResource(R.string.biometria_error_contrasena_vacia)
        ErrorAcceso.CONTRASENA_INCORRECTA -> stringResource(R.string.biometria_error_contrasena)
        else -> null
    }
    AlertDialog(
        onDismissRequest = acciones.cancelarFormulario,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(stringResource(R.string.biometria_formulario_titulo)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                estado.error?.takeIf {
                    it != ErrorAcceso.CONTRASENA_VACIA && it != ErrorAcceso.CONTRASENA_INCORRECTA
                }?.let { Text(stringResource(R.string.biometria_error_servicio), color = MaterialTheme.colorScheme.error) }
                Text(stringResource(R.string.biometria_metodo_pregunta), style = MaterialTheme.typography.titleSmall)
                Column(modifier = Modifier.selectableGroup()) {
                    estado.capacidad.metodos.forEach { metodo ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = estado.metodoElegido == metodo,
                                    onClick = { acciones.elegirMetodo(metodo) },
                                    role = Role.RadioButton,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RadioButton(selected = estado.metodoElegido == metodo, onClick = null)
                            Text(nombreDe(metodo))
                        }
                    }
                }
                CampoTexto(
                    valor = contrasena,
                    onValorChange = { contrasena = it },
                    etiqueta = stringResource(R.string.biometria_contrasena),
                    ayuda = stringResource(R.string.biometria_contrasena_ayuda),
                    error = error,
                    teclado = KeyboardOptions(keyboardType = KeyboardType.Password),
                    transformacion = if (mostrar) VisualTransformation.None else PasswordVisualTransformation(),
                    icono = {
                        IconButton(onClick = { mostrar = !mostrar }) {
                            Icon(
                                imageVector = if (mostrar) Iconos.OjoTachado else Iconos.Ojo,
                                contentDescription = stringResource(
                                    if (mostrar) R.string.registro_ocultar_password else R.string.registro_mostrar_password,
                                ),
                            )
                        }
                    },
                )
            }
        },
        confirmButton = {
            BotonPrimario(stringResource(R.string.biometria_continuar), onClick = { acciones.confirmarActivacion(contrasena) })
        },
        dismissButton = {
            TextButton(onClick = acciones.cancelarFormulario, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.biometria_cancelar), style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

@Composable
private fun ConfirmarDesactivar(acciones: SeguridadAcciones) {
    AlertDialog(
        onDismissRequest = acciones.cancelarDesactivar,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        icon = {
            Icon(Iconos.Advertencia, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp))
        },
        title = { Text(stringResource(R.string.biometria_desactivar_titulo)) },
        text = {
            Text(
                stringResource(R.string.biometria_desactivar_texto),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { BotonDestructivo(stringResource(R.string.biometria_desactivar_si), onClick = acciones.confirmarDesactivar) },
        dismissButton = {
            TextButton(onClick = acciones.cancelarDesactivar, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.biometria_cancelar), style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}
