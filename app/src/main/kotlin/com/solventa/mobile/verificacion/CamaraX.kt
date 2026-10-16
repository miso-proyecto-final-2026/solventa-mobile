package com.solventa.mobile.verificacion

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraInfoUnavailableException
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.solventa.mobile.R
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Vista previa y botón de captura con CameraX. Entrega la ruta del JPEG en el caché de la app;
 * quien lo recibe es responsable de borrarlo.
 */
@Composable
fun CamaraX(
    frontal: Boolean,
    descripcion: String,
    onCaptura: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val contexto = LocalContext.current
    val ciclo = LocalLifecycleOwner.current
    val vista = remember { PreviewView(contexto) }
    val captura = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setJpegQuality(85)
            .build()
    }
    var fallo by remember { mutableStateOf(false) }
    var proveedor by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(frontal) {
        try {
            val camaras = proveedorDeCamara(contexto).also { proveedor = it }
            val lente = resolverLente(
                preferida = if (frontal) LenteCamara.FRONTAL else LenteCamara.TRASERA,
                hayFrontal = camaras.tiene(CameraSelector.DEFAULT_FRONT_CAMERA),
                hayTrasera = camaras.tiene(CameraSelector.DEFAULT_BACK_CAMERA),
            )
            if (lente == null) {
                Log.w(ETIQUETA, "El dispositivo no tiene ninguna cámara disponible")
                fallo = true
                return@LaunchedEffect
            }
            if ((lente == LenteCamara.FRONTAL) != frontal) {
                Log.w(ETIQUETA, "Falta la cámara ${if (frontal) "frontal" else "trasera"}: se usa la otra")
            }
            val selector = if (lente == LenteCamara.FRONTAL) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            val vistaPrevia = Preview.Builder().build().also { it.setSurfaceProvider(vista.surfaceProvider) }
            camaras.unbindAll()
            camaras.bindToLifecycle(ciclo, selector, vistaPrevia, captura)
            fallo = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Sin esto el error de pantalla no dice nada: la causa queda en el Logcat (sin datos personales).
            Log.e(ETIQUETA, "No se pudo enlazar la cámara", e)
            fallo = true
        }
    }

    // Al salir de la pantalla se libera la cámara, para que el siguiente paso pueda abrirla
    // (las webcams del emulador no admiten dos usos seguidos sin liberarse).
    DisposableEffect(Unit) {
        onDispose { proveedor?.unbindAll() }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AndroidView(
            factory = { vista },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp, max = 480.dp)
                .semantics { contentDescription = descripcion },
        )
        if (fallo) {
            Text(stringResource(R.string.verificacion_camara_error), color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = {
                val archivo = File.createTempFile("kyc_", ".jpg", contexto.cacheDir)
                captura.takePicture(
                    ImageCapture.OutputFileOptions.Builder(archivo).build(),
                    ContextCompat.getMainExecutor(contexto),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(resultado: ImageCapture.OutputFileResults) {
                            onCaptura(archivo.absolutePath)
                        }

                        override fun onError(excepcion: ImageCaptureException) {
                            Log.e(ETIQUETA, "No se pudo tomar la foto", excepcion)
                            archivo.delete()
                            fallo = true
                        }
                    },
                )
            },
            enabled = !fallo,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.verificacion_tomar_foto))
        }
    }
}

private const val ETIQUETA = "CamaraX"

private fun ProcessCameraProvider.tiene(selector: CameraSelector): Boolean =
    try {
        hasCamera(selector)
    } catch (e: CameraInfoUnavailableException) {
        false
    }

private suspend fun proveedorDeCamara(contexto: Context): ProcessCameraProvider =
    suspendCancellableCoroutine { continuacion ->
        val futuro = ProcessCameraProvider.getInstance(contexto)
        futuro.addListener(
            {
                try {
                    continuacion.resume(futuro.get())
                } catch (e: Exception) {
                    continuacion.resumeWithException(e)
                }
            },
            ContextCompat.getMainExecutor(contexto),
        )
    }
