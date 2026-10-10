package com.solventa.mobile.registro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.solventa.mobile.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistroScreen(
    viewModel: RegistroViewModel,
    onRegistroExitoso: () -> Unit,
) {
    val estado by viewModel.uiState.collectAsState()

    LaunchedEffect(estado.registroExitoso) {
        if (estado.registroExitoso) {
            onRegistroExitoso()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = stringResource(R.string.registro_titulo), style = MaterialTheme.typography.headlineSmall)

        CampoTexto(
            valor = estado.nombre,
            etiqueta = stringResource(R.string.registro_nombre),
            error = estado.errores["nombre"],
            onValorCambia = viewModel::onNombreChange,
        )
        CampoTexto(
            valor = estado.apellido,
            etiqueta = stringResource(R.string.registro_apellido),
            error = estado.errores["apellido"],
            onValorCambia = viewModel::onApellidoChange,
        )

        Text(text = stringResource(R.string.registro_tipo_documento))
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
            etiqueta = stringResource(R.string.registro_numero_documento),
            error = estado.errores["numeroDocumento"],
            onValorCambia = viewModel::onNumeroDocumentoChange,
        )
        CampoTexto(
            valor = estado.email,
            etiqueta = stringResource(R.string.registro_email),
            error = estado.errores["email"],
            tipoTeclado = KeyboardType.Email,
            onValorCambia = viewModel::onEmailChange,
        )
        CampoTexto(
            valor = estado.celular,
            etiqueta = stringResource(R.string.registro_celular),
            error = estado.errores["celular"],
            tipoTeclado = KeyboardType.Phone,
            onValorCambia = viewModel::onCelularChange,
        )
        CampoTexto(
            valor = estado.password,
            etiqueta = stringResource(R.string.registro_password),
            error = estado.errores["password"],
            esPassword = true,
            onValorCambia = viewModel::onPasswordChange,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = estado.aceptoTerminos,
                onCheckedChange = viewModel::onAceptoTerminosChange,
                modifier = Modifier.semantics {
                    contentDescription = "Aceptar política de tratamiento de datos"
                },
            )
            Text(text = stringResource(R.string.registro_terminos))
        }
        estado.errores["terminos"]?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }

        estado.mensajeError?.let { mensaje ->
            Text(text = mensaje, color = MaterialTheme.colorScheme.error)
        }

        if (estado.cargando) {
            CircularProgressIndicator()
            Text(text = stringResource(R.string.registro_cargando))
        } else {
            Button(onClick = viewModel::enviar, modifier = Modifier.fillMaxWidth()) {
                val textoBoton = if (estado.mensajeError != null) {
                    stringResource(R.string.registro_reintentar)
                } else {
                    stringResource(R.string.registro_boton)
                }
                Text(text = textoBoton)
            }
        }
    }
}

@Composable
private fun CampoTexto(
    valor: String,
    etiqueta: String,
    error: String?,
    onValorCambia: (String) -> Unit,
    tipoTeclado: KeyboardType = KeyboardType.Text,
    esPassword: Boolean = false,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onValorCambia,
        label = { Text(etiqueta) },
        isError = error != null,
        supportingText = { error?.let { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = tipoTeclado),
        visualTransformation = if (esPassword) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = etiqueta },
    )
}
