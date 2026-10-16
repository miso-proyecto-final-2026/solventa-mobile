package com.solventa.mobile.verificacion

import android.content.Context
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

    LaunchedEffect(frontal) {
        try {
            val proveedor = proveedorDeCamara(contexto)
            val vistaPrevia = Preview.Builder().build().also { it.setSurfaceProvider(vista.surfaceProvider) }
            val selector = if (frontal) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            proveedor.unbindAll()
            proveedor.bindToLifecycle(ciclo, selector, vistaPrevia, captura)
            fallo = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fallo = true
        }
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
