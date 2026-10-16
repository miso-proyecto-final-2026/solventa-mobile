package com.solventa.mobile.verificacion

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** Conecta la pantalla con el permiso real de cámara del sistema. */
@Composable
fun VerificacionRoute(viewModel: VerificacionViewModel, onFinalizar: () -> Unit) {
    val contexto = LocalContext.current
    val ciclo = LocalLifecycleOwner.current
    var concedido by remember { mutableStateOf(tienePermisoDeCamara(contexto)) }

    val solicitar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        concedido = ok
        viewModel.onPermiso(ok)
    }

    // Al volver de los ajustes del sistema el permiso puede haber cambiado.
    DisposableEffect(ciclo) {
        val observador = LifecycleEventObserver { _, evento ->
            if (evento == Lifecycle.Event.ON_RESUME) concedido = tienePermisoDeCamara(contexto)
        }
        ciclo.lifecycle.addObserver(observador)
        onDispose { ciclo.lifecycle.removeObserver(observador) }
    }

    VerificacionScreen(
        viewModel = viewModel,
        permisoCamaraConcedido = concedido,
        onSolicitarPermiso = { solicitar.launch(Manifest.permission.CAMERA) },
        onAbrirAjustes = {
            contexto.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", contexto.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
        onFinalizar = onFinalizar,
    )
}

private fun tienePermisoDeCamara(contexto: Context): Boolean =
    ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
