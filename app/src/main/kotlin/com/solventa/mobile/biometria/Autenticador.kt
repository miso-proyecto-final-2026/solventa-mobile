package com.solventa.mobile.biometria

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

sealed interface ResultadoAutenticacion {
    data object Exito : ResultadoAutenticacion

    /** El cliente cerró el diálogo o pulsó «Cancelar»: no es una falla. */
    data object Cancelado : ResultadoAutenticacion

    /** Se alcanzó el máximo de intentos fallidos permitido por la pantalla (HU05: 3). */
    data object Agotado : ResultadoAutenticacion

    data object Error : ResultadoAutenticacion
}

/** Pide la biometría al sistema y la liga al [Cipher]; separado del ViewModel para poder simularlo en las pruebas. */
fun interface Autenticador {
    /** [alFallar] se llama por cada intento que no coincide (huella o rostro no reconocidos), sin cerrar el diálogo. */
    suspend fun confirmar(cifrador: Cipher, alFallar: () -> Unit): ResultadoAutenticacion
}

/** Diálogo biométrico de Android (criterio 5): solo el sistema ve la huella o el rostro. */
class AutenticadorBiometricPrompt(
    private val actividad: FragmentActivity,
    private val titulo: String,
    private val subtitulo: String,
    private val cancelar: String,
    /** Intentos fallidos tras los que se cierra el diálogo y se resuelve [ResultadoAutenticacion.Agotado]; `null` = sin tope propio. */
    private val maxFallos: Int? = null,
) : Autenticador {

    override suspend fun confirmar(cifrador: Cipher, alFallar: () -> Unit): ResultadoAutenticacion =
        suspendCancellableCoroutine { continuacion ->
            var fallos = 0
            var agotado = false
            lateinit var prompt: BiometricPrompt
            prompt = BiometricPrompt(
                actividad,
                ContextCompat.getMainExecutor(actividad),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        // La clave exige biometría: si no se puede usar, la autenticación no cuenta (no basta con el callback).
                        val usable = try {
                            result.cryptoObject?.cipher?.doFinal(ByteArray(0))
                            true
                        } catch (_: GeneralSecurityException) {
                            false
                        }
                        if (continuacion.isActive) {
                            continuacion.resume(if (usable) ResultadoAutenticacion.Exito else ResultadoAutenticacion.Error)
                        }
                    }

                    override fun onAuthenticationFailed() {
                        fallos++
                        alFallar()
                        if (maxFallos != null && fallos >= maxFallos && !agotado) {
                            agotado = true
                            prompt.cancelAuthentication()
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        val cancelado = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                            errorCode == BiometricPrompt.ERROR_CANCELED
                        if (continuacion.isActive) {
                            continuacion.resume(
                                when {
                                    agotado -> ResultadoAutenticacion.Agotado
                                    cancelado -> ResultadoAutenticacion.Cancelado
                                    else -> ResultadoAutenticacion.Error
                                },
                            )
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
