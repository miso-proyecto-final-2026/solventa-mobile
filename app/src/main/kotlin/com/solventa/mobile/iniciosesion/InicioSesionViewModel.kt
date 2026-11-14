package com.solventa.mobile.iniciosesion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Cada falla tiene su mensaje (HU05, criterio 9); ninguno revela si el correo existe. */
enum class ErrorInicioSesion { CAMPOS_VACIOS, CREDENCIALES, BLOQUEADO, SIN_CONEXION, TIEMPO_AGOTADO, SERVICIO }

data class InicioSesionUiState(
    val email: String = "",
    val password: String = "",
    val cargando: Boolean = false,
    val error: ErrorInicioSesion? = null,
    /** Minutos que faltan para poder reintentar; solo con [ErrorInicioSesion.BLOQUEADO]. */
    val minutosBloqueo: Int = 0,
    val sesionIniciada: Boolean = false,
)

/** Inicio de sesión con correo y contraseña (HU05): respaldo de la biometría y salida de una sesión vencida. */
class InicioSesionViewModel @JvmOverloads constructor(
    emailInicial: String = "",
    private val repository: InicioSesionRepository = InicioSesionRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(InicioSesionUiState(email = emailInicial))
    val uiState: StateFlow<InicioSesionUiState> = _uiState.asStateFlow()

    fun onEmailChange(valor: String) = _uiState.update { it.copy(email = valor, error = null) }

    fun onPasswordChange(valor: String) = _uiState.update { it.copy(password = valor, error = null) }

    fun enviar() {
        val estado = _uiState.value
        if (estado.cargando || estado.sesionIniciada) return
        val email = estado.email.trim()
        if (email.isEmpty() || estado.password.isEmpty()) {
            _uiState.update { it.copy(error = ErrorInicioSesion.CAMPOS_VACIOS) }
            return
        }
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            when (val resultado = repository.iniciar(email, estado.password)) {
                is InicioSesionRepository.Resultado.Exito ->
                    // La contraseña no se conserva en el estado una vez que sirvió.
                    _uiState.update { it.copy(cargando = false, password = "", sesionIniciada = true) }
                InicioSesionRepository.Resultado.CredencialesInvalidas ->
                    _uiState.update { it.copy(cargando = false, error = ErrorInicioSesion.CREDENCIALES) }
                is InicioSesionRepository.Resultado.Bloqueado ->
                    _uiState.update {
                        it.copy(
                            cargando = false,
                            error = ErrorInicioSesion.BLOQUEADO,
                            minutosBloqueo = maxOf(1, (resultado.segundos + 59) / 60),
                        )
                    }
                InicioSesionRepository.Resultado.SinConexion ->
                    _uiState.update { it.copy(cargando = false, error = ErrorInicioSesion.SIN_CONEXION) }
                InicioSesionRepository.Resultado.TiempoAgotado ->
                    _uiState.update { it.copy(cargando = false, error = ErrorInicioSesion.TIEMPO_AGOTADO) }
                is InicioSesionRepository.Resultado.ErrorServicio ->
                    _uiState.update { it.copy(cargando = false, error = ErrorInicioSesion.SERVICIO) }
            }
        }
    }
}
