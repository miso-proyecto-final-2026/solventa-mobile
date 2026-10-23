package com.solventa.mobile.privacidad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Privacidad y consentimiento (HU07). Revocar exige confirmación, y el consentimiento solo se muestra como
 * revocado cuando el servidor lo confirmó: ante cualquier falla se conserva el estado anterior y se ofrece
 * reintentar.
 */
class PrivacidadViewModel @JvmOverloads constructor(
    private val repository: PrivacidadRepository = PrivacidadRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrivacidadUiState())
    val uiState: StateFlow<PrivacidadUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _uiState.update { it.copy(fase = FasePrivacidad.CARGANDO, error = null, pendiente = null, recienRevocado = false) }
        viewModelScope.launch {
            when (val resultado = repository.consultar()) {
                is PrivacidadRepository.Resultado.Exito -> _uiState.update {
                    it.copy(fase = FasePrivacidad.ESTADO, consentimiento = resultado.consentimiento)
                }
                else -> falla(resultado, AccionPrivacidad.CONSULTAR)
            }
        }
    }

    /** Primer paso de la revocación: pide confirmación, no toca al servidor. */
    fun pedirRevocar() {
        val estado = _uiState.value
        if (estado.fase != FasePrivacidad.ESTADO || !estado.puedeRevocar) return
        _uiState.update { it.copy(fase = FasePrivacidad.CONFIRMANDO, error = null, recienRevocado = false) }
    }

    fun cancelar() {
        if (_uiState.value.fase != FasePrivacidad.CONFIRMANDO) return
        _uiState.update { it.copy(fase = FasePrivacidad.ESTADO) }
    }

    fun confirmarRevocar() {
        if (_uiState.value.fase != FasePrivacidad.CONFIRMANDO) return
        revocar()
    }

    /** Repite lo que falló: la consulta o la revocación ya confirmada por el usuario. */
    fun reintentar() {
        when (_uiState.value.pendiente) {
            AccionPrivacidad.CONSULTAR -> cargar()
            AccionPrivacidad.REVOCAR -> revocar()
            null -> Unit
        }
    }

    private fun revocar() {
        _uiState.update { it.copy(fase = FasePrivacidad.REVOCANDO, error = null, pendiente = null) }
        viewModelScope.launch {
            when (val resultado = repository.revocar()) {
                is PrivacidadRepository.Resultado.Exito -> _uiState.update {
                    it.copy(
                        fase = FasePrivacidad.ESTADO,
                        consentimiento = resultado.consentimiento,
                        recienRevocado = resultado.consentimiento.estado == ESTADO_REVOCADO,
                    )
                }
                else -> falla(resultado, AccionPrivacidad.REVOCAR)
            }
        }
    }

    private fun falla(resultado: PrivacidadRepository.Resultado, pendiente: AccionPrivacidad) {
        val error = when (resultado) {
            PrivacidadRepository.Resultado.SinSesion -> ErrorPrivacidad.SESION_VENCIDA
            PrivacidadRepository.Resultado.TiempoAgotado -> ErrorPrivacidad.TIEMPO_AGOTADO
            PrivacidadRepository.Resultado.SinConexion -> ErrorPrivacidad.SIN_CONEXION
            else -> ErrorPrivacidad.SERVICIO
        }
        // El consentimiento queda como estaba: sin confirmación del servidor no se muestra como revocado.
        _uiState.update { it.copy(fase = FasePrivacidad.ESTADO, error = error, pendiente = pendiente, recienRevocado = false) }
    }
}
