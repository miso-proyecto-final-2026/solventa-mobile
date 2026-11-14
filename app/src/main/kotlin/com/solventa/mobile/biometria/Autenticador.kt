package com.solventa.mobile.biometria

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

sealed interface ResultadoAutenticacion {
    data object Exito : ResultadoAutenticacion

    /** El cliente cerró el diálogo o pulsó «Cancelar»: no es una falla. */
    data object Cancelado : ResultadoAutenticacion

    data object Error : ResultadoAutenticacion
}

/** Pide la biometría al sistema y la liga al [Cipher]; separado del ViewModel para poder simularlo en las pruebas. */
fun interface Autenticador {
    suspend fun confirmar(cifrador: Cipher): ResultadoAutenticacion
}

/** Diálogo biométrico de Android (criterio 5): solo el sistema ve la huella o el rostro. */
class AutenticadorBiometricPrompt(
    private val actividad: FragmentActivity,
    private val titulo: String,
    private val subtitulo: String,
    private val cancelar: String,
) : Autenticador {

    override suspend fun confirmar(cifrador: Cipher): ResultadoAutenticacion =
        suspendCancellableCoroutine { continuacion ->
            val prompt = BiometricPrompt(
                actividad,
                ContextCompat.getMainExecutor(actividad),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (continuacion.isActive) continuacion.resume(ResultadoAutenticacion.Exito)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        val cancelado = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                            errorCode == BiometricPrompt.ERROR_CANCELED
                        if (continuacion.isActive) {
                            continuacion.resume(if (cancelado) ResultadoAutenticacion.Cancelado else ResultadoAutenticacion.Error)
                        }
                    }
                },
            )
            val informacion = BiometricPrompt.PromptInfo.Builder()
                .setTitle(titulo)
                .setSubtitle(subtitulo)
                .setNegativeButtonText(cancelar)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()
            prompt.authenticate(informacion, BiometricPrompt.CryptoObject(cifrador))
            continuacion.invokeOnCancellation { prompt.cancelAuthentication() }
        }
}
