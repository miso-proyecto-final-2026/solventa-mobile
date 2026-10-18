package com.solventa.mobile.resultado

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solventa.mobile.resultado.ResultadoRepository.Consulta
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ResultadoUiState {
    data object Cargando : ResultadoUiState

    /** No se pudo consultar el estado y no hay uno anterior que mostrar. */
    data class Error(val error: ErrorConsulta) : ResultadoUiState

    data class Mostrando(
        val pantalla: PantallaResultado,
        /** Consultando el consentimiento tras «Continuar». */
        val procesando: Boolean = false,
        val errorAccion: ErrorConsulta? = null,
    ) : ResultadoUiState
}

/** A dónde lleva la pantalla de resultado; la navegación de la app lo atiende y lo limpia. */
enum class Destino {
    REGISTRO,

    /** Sin verificación todavía y sin consentimiento: el orden actual es consentimiento → verificación. */
    CONSENTIMIENTO_ANTES_DE_VERIFICAR,

    /** Aprobada pero sin consentimiento (criterio 1): tras otorgarlo se sigue al inicio. */
    CONSENTIMIENTO_TRAS_APROBAR,
    VERIFICACION,
    INICIO,
}

/**
 * Pantalla de resultado de HU03. No calcula el resultado: lo consulta en MS Identidad cada vez que
 * la pantalla vuelve a primer plano, así que al reabrir la app se ve el estado vigente.
 */
class ResultadoViewModel @JvmOverloads constructor(
    private val repository: ResultadoRepository = ResultadoRepository(),
    /** La sesión guardada dejó de ser válida: se borra para no reintentar con ella. */
    private val alPerderSesion: suspend () -> Unit = {},
) : ViewModel() {

    private val _uiState = MutableStateFlow<ResultadoUiState>(ResultadoUiState.Cargando)
    val uiState: StateFlow<ResultadoUiState> = _uiState.asStateFlow()

    private val _destino = MutableStateFlow<Destino?>(null)
    val destino: StateFlow<Destino?> = _destino.asStateFlow()

    private var consultando = false

    /** Al entrar y al volver a la app: un caso en revisión puede haberse resuelto mientras tanto. */
    fun actualizar() {
        if (consultando) return
        consultando = true
        // Si ya hay un resultado en pantalla se mantiene mientras se consulta, sin parpadeos.
        if (_uiState.value !is ResultadoUiState.Mostrando) _uiState.value = ResultadoUiState.Cargando
        viewModelScope.launch {
            try {
                alRecibirEstado(repository.estado())
            } finally {
                consultando = false
            }
        }
    }

    private suspend fun alRecibirEstado(consulta: Consulta<com.solventa.mobile.red.EstadoKycResponse>) {
        when (consulta) {
            is Consulta.Exito -> {
                val pantalla = mapearResultado(consulta.valor)
                when (pantalla) {
                    null -> mostrarError(ErrorConsulta.INESPERADO)
                    PantallaResultado.Pendiente -> irAVerificar()
                    else -> _uiState.value = ResultadoUiState.Mostrando(pantalla)
                }
            }
            is Consulta.SinSesion -> sesionPerdida()
            is Consulta.Fallo -> mostrarError(consulta.error)
        }
    }

    private suspend fun irAVerificar() {
        when (val consentimiento = repository.consentimientoOtorgado()) {
            is Consulta.Exito -> _destino.value =
                if (consentimiento.valor) Destino.VERIFICACION else Destino.CONSENTIMIENTO_ANTES_DE_VERIFICAR
            is Consulta.SinSesion -> sesionPerdida()
            is Consulta.Fallo -> mostrarError(consentimiento.error)
        }
    }

    /** Un fallo al refrescar no tapa el resultado ya mostrado. */
    private fun mostrarError(error: ErrorConsulta) {
        if (_uiState.value !is ResultadoUiState.Mostrando) _uiState.value = ResultadoUiState.Error(error)
    }

    private suspend fun sesionPerdida() {
        alPerderSesion()
        _destino.value = Destino.REGISTRO
    }

    /** Criterio 1: «Continuar» lleva al consentimiento si falta y, si ya está, al paso siguiente. */
    fun continuar() {
        val estado = _uiState.value as? ResultadoUiState.Mostrando ?: return
        if (estado.pantalla != PantallaResultado.Aprobada || estado.procesando) return
        _uiState.value = estado.copy(procesando = true, errorAccion = null)
        viewModelScope.launch {
            when (val consentimiento = repository.consentimientoOtorgado()) {
                is Consulta.Exito -> {
                    _uiState.update { (it as ResultadoUiState.Mostrando).copy(procesando = false) }
                    _destino.value = if (consentimiento.valor) Destino.INICIO else Destino.CONSENTIMIENTO_TRAS_APROBAR
                }
                is Consulta.SinSesion -> sesionPerdida()
                is Consulta.Fallo -> _uiState.update {
                    (it as ResultadoUiState.Mostrando).copy(procesando = false, errorAccion = consentimiento.error)
                }
            }
        }
    }

    /** Criterio 2: solo con intentos disponibles. */
    fun reintentar() {
        val pantalla = (_uiState.value as? ResultadoUiState.Mostrando)?.pantalla
        if (pantalla is PantallaResultado.RechazadaConReintentos) _destino.value = Destino.VERIFICACION
    }

    fun destinoAtendido() {
        _destino.value = null
    }
}
