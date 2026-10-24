package com.solventa.mobile.ui.componentes

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Campo outlined de 56 dp con la etiqueta flotante de 12 sp (Material 3 la anima sola) y radio de 4 dp. La etiqueta
 * queda asociada al campo para el lector de pantalla; con error el mensaje reemplaza a la ayuda.
 */
@Composable
fun CampoTexto(
    valor: String,
    onValorChange: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    ayuda: String? = null,
    error: String? = null,
    prefijo: String? = null,
    icono: @Composable (() -> Unit)? = null,
    teclado: KeyboardOptions = KeyboardOptions.Default,
    transformacion: VisualTransformation = VisualTransformation.None,
    habilitado: Boolean = true,
) {
    val texto = error ?: ayuda
    OutlinedTextField(
        value = valor,
        onValueChange = onValorChange,
        label = { Text(etiqueta) },
        prefix = prefijo?.let { { Text(it) } },
        trailingIcon = icono,
        isError = error != null,
        supportingText = texto?.let { { Text(it) } },
        keyboardOptions = teclado,
        visualTransformation = transformacion,
        enabled = habilitado,
        singleLine = true,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
    )
}
