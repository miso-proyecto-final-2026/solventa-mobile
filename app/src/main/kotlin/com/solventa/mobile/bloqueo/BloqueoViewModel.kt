package com.solventa.mobile.bloqueo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solventa.mobile.biometria.Autenticador
import com.solventa.mobile.biometria.ClaveBiometrica
import com.solventa.mobile.biometria.EstadoClave
import com.solventa.mobile.biometria.ResultadoAutenticacion
import android.security.keystore.KeyPermanentlyInvalidatedException
import java.security.GeneralSecurityException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FaseBloqueo { LISTO, AUTENTICANDO, DESBLOQUEADO, CONTRASENA }

/** Por qué se pide la contraseña en vez de la biometría. */
enum class MotivoContrasena { ELEGIDA, TRES_FALLOS, BIOMETRIA_CAMBIO, ERROR }

data class BloqueoUiState(
    val fase: FaseBloqueo = FaseBloqueo.LISTO,
    val fallidos: Int = 0,
    /** El cliente cerró el diálogo del sistema: se queda en el bloqueo, con la huella y la contraseña a elegir. */
    val cancelado: Boolean = false,
    val motivo: MotivoContrasena? = null,
) {
    val intentosRestantes: Int get() = (MAX_INTENTOS_BIOMETRIA - fallidos).coerceAtLeast(0)
}

/**
 * Bloqueo biométrico al abrir la app (HU05). Máquina de estados: biometría correcta → desbloquea; 3 fallos seguidos
 * (acumulados aunque se cancele y se reintente), error del sistema o cambio de las biometrías del dispositivo → pide la
 * contraseña; cancelar el diálogo deja la pantalla de bloqueo para reintentar o elegir la contraseña. La biometría y
 * el token no se registran en ningún log.
 */
class BloqueoViewModel(private val clave: ClaveBiometrica) : ViewModel() {

    private val _uiState = MutableStateFlow(BloqueoUiState())
    val uiState: StateFlow<BloqueoUiState> = _uiState.asStateFlow()

    /** Verdadero si las biometrías cambiaron: tras iniciar sesión hay que invitar a reactivar el acceso (HU04, criterio 8). */
    @Volatile
    var cambioDeBiometria: Boolean = false
        private set

    init {
        if (clave.estado() == EstadoClave.INVALIDADA) biometriaCambio()
    }

    fun autenticar(autenticador: Autenticador) {
        val estado = _uiState.value
        if (estado.fase != FaseBloqueo.LISTO) return
        val cifrador = try {
            clave.cifradorExistente()
        } catch (_: KeyPermanentlyInvalidatedException) {
            biometriaCambio()
            return
        } catch (_: GeneralSecurityException) {
            _uiState.update { it.copy(fase = FaseBloqueo.CONTRASENA, motivo = MotivoContrasena.ERROR) }
            return
        }
        _uiState.update { it.copy(fase = FaseBloqueo.AUTENTICANDO, cancelado = false) }
        viewModelScope.launch {
            when (autenticador.confirmar(cifrador) { _uiState.update { it.copy(fallidos = it.fallidos + 1) } }) {
                ResultadoAutenticacion.Exito -> _uiState.update { it.copy(fase = FaseBloqueo.DESBLOQUEADO) }
                ResultadoAutenticacion.Cancelado -> _uiState.update { it.copy(fase = FaseBloqueo.LISTO, cancelado = true) }
                ResultadoAutenticacion.Agotado ->
                    _uiState.update { it.copy(fase = FaseBloqueo.CONTRASENA, motivo = MotivoContrasena.TRES_FALLOS) }
                ResultadoAutenticacion.Error ->
                    _uiState.update { it.copy(fase = FaseBloqueo.CONTRASENA, motivo = MotivoContrasena.ERROR) }
            }
        }
    }

    fun usarContrasena() {
        if (_uiState.value.fase != FaseBloqueo.LISTO) return
        _uiState.update { it.copy(fase = FaseBloqueo.CONTRASENA, motivo = MotivoContrasena.ELEGIDA) }
    }

    private fun biometriaCambio() {
        // La clave ya no sirve: se borra y el cliente entra con la contraseña; luego se le invita a reactivarlo.
        clave.borrar()
        cambioDeBiometria = true
        _uiState.update { it.copy(fase = FaseBloqueo.CONTRASENA, motivo = MotivoContrasena.BIOMETRIA_CAMBIO) }
    }
}
