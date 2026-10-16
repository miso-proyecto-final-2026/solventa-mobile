package com.solventa.mobile.consentimiento

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Paso de consentimiento (HU06). No hay aceptación implícita: solo `aceptar()` otorga, y
 * `rechazar()` deja el consentimiento sin otorgar sin llamar al servidor (el cliente sigue sin
 * compartir datos y las cotizaciones usan el perfil por defecto, HU37).
 */
class ConsentimientoViewModel @JvmOverloads constructor(
    private val repository: ConsentimientoRepository = ConsentimientoRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConsentimientoUiState())
    val uiState: StateFlow<ConsentimientoUiState> = _uiState.asStateFlow()

    fun alternarDetalle() = _uiState.update { it.copy(detalleExpandido = !it.detalleExpandido) }

    fun aceptar() {
        if (_uiState.value.cargando || _uiState.value.decision != null) return
        _uiState.update { it.copy(cargando = true, mensajeError = null) }
        viewModelScope.launch {
            when (val resultado = repository.otorgar()) {
                is ConsentimientoRepository.Resultado.Otorgado -> _uiState.update {
                    it.copy(
                        cargando = false,
                        decision = DecisionConsentimiento.Otorgado(
                            versionTexto = resultado.respuesta.versionTexto ?: VERSION_TEXTO_LEGAL,
                            fechaOtorgamiento = resultado.respuesta.fechaOtorgamiento.orEmpty(),
                        ),
                    )
                }

                ConsentimientoRepository.Resultado.SinSesion -> _uiState.update {
                    it.copy(cargando = false, mensajeError = MENSAJE_SIN_SESION)
                }

                is ConsentimientoRepository.Resultado.ErrorRed -> _uiState.update {
                    it.copy(cargando = false, mensajeError = resultado.mensaje)
                }
            }
        }
    }

    fun rechazar() {
        if (_uiState.value.cargando || _uiState.value.decision != null) return
        _uiState.update { it.copy(decision = DecisionConsentimiento.Rechazado, mensajeError = null) }
    }

    fun continuar() = _uiState.update { it.copy(continuar = true) }

    private companion object {
        const val MENSAJE_SIN_SESION = "Tu sesión no es válida. Vuelve a iniciar el registro."
    }
}
