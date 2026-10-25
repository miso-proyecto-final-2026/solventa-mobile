package com.solventa.mobile.registro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import com.solventa.mobile.R
import com.solventa.mobile.ui.componentes.BarraSuperior
import com.solventa.mobile.ui.componentes.BotonPrimario
import com.solventa.mobile.ui.componentes.CampoTexto
import com.solventa.mobile.ui.componentes.IndicadorPasos
import com.solventa.mobile.ui.iconos.Iconos

/** Prefijo de país del celular: solo se muestra; el número que se envía sigue siendo de 10 dígitos. */
private const val PREFIJO_CELULAR = "+57"

/**
 * Registro (HU01, A1 del diseño): paso 1 de 3 del alta. Mantiene los datos que exige el contrato de Identidad
 * (apellido y documento) además de los cuatro del diseño.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistroScreen(
    viewModel: RegistroViewModel,
    onRegistroExitoso: () -> Unit,
) {
    val estado by viewModel.uiState.collectAsState()
    var mostrarPassword by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(estado.registroExitoso) {
        if (estado.registroExitoso) {
            onRegistroExitoso()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Es la primera pantalla: no hay a dónde volver, así que la barra solo lleva el título.
        BarraSuperior(titulo = stringResource(R.string.registro_titulo), onAtras = null)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            IndicadorPasos(paso = 1, total = 3, nombre = stringResource(R.string.registro_paso_nombre))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.registro_encabezado),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.registro_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CampoTexto(
                    valor = estado.nombre,
                    onValorChange = viewModel::onNombreChange,
                    etiqueta = stringResource(R.string.registro_nombre),
                    error = estado.errores["nombre"],
                )
                CampoTexto(
                    valor = estado.apellido,
                    onValorChange = viewModel::onApellidoChange,
                    etiqueta = stringResource(R.string.registro_apellido),
                    error = estado.errores["apellido"],
                )

                Text(
                    text = stringResource(R.string.registro_tipo_documento),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val tiposDocumento = listOf("CC", "CE", "PASAPORTE")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    tiposDocumento.forEachIndexed { indice, tipo ->
                        SegmentedButton(
                            selected = estado.tipoDocumento == tipo,
                            onClick = { viewModel.onTipoDocumentoChange(tipo) },
                            shape = SegmentedButtonDefaults.itemShape(index = indice, count = tiposDocumento.size),
                        ) {
                            Text(tipo)
                        }
                    }
                }

                CampoTexto(
                    valor = estado.numeroDocumento,
                    onValorChange = viewModel::onNumeroDocumentoChange,
                    etiqueta = stringResource(R.string.registro_numero_documento),
                    error = estado.errores["numeroDocumento"],
                )
                CampoTexto(
                    valor = estado.email,
                    onValorChange = viewModel::onEmailChange,
                    etiqueta = stringResource(R.string.registro_email),
                    error = estado.errores["email"],
                    teclado = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                CampoTexto(
                    valor = estado.celular,
                    onValorChange = viewModel::onCelularChange,
                    etiqueta = stringResource(R.string.registro_celular),
                    error = estado.errores["celular"],
                    prefijo = PREFIJO_CELULAR,
                    teclado = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                CampoTexto(
                    valor = estado.password,
                    onValorChange = viewModel::onPasswordChange,
                    etiqueta = stringResource(R.string.registro_password),
                    ayuda = stringResource(R.string.registro_password_ayuda),
                    error = estado.errores["password"],
                    teclado = KeyboardOptions(keyboardType = KeyboardType.Password),
                    transformacion = if (mostrarPassword) VisualTransformation.None else PasswordVisualTransformation(),
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
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(
                        value = estado.aceptoTerminos,
                        role = Role.Checkbox,
                        onValueChange = viewModel::onAceptoTerminosChange,
                    ),
            ) {
                Checkbox(checked = estado.aceptoTerminos, onCheckedChange = null)
                Text(text = stringResource(R.string.registro_terminos), style = MaterialTheme.typography.bodyMedium)
            }
            estado.errores["terminos"]?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            estado.mensajeError?.let { mensaje ->
                Text(
                    text = mensaje,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
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
                    Text(text = stringResource(R.string.registro_cargando))
                }
            } else {
                BotonPrimario(
                    texto = stringResource(
                        if (estado.mensajeError != null) R.string.registro_reintentar else R.string.registro_boton,
                    ),
                    onClick = viewModel::enviar,
                )
            }
        }
    }
}
