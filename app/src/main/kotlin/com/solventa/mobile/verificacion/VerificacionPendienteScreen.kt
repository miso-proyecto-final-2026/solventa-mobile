package com.solventa.mobile.verificacion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.solventa.mobile.R

/**
 * Inicio del flujo de verificación de identidad (HU02), fuera de alcance de HU01.
 * Placeholder para cumplir el criterio de aceptación "navega al inicio de la verificación".
 */
@Composable
fun VerificacionPendienteScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.verificacion_titulo),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(text = stringResource(R.string.verificacion_mensaje))
    }
}
