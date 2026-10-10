package com.solventa.mobile.registro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RegistroViewModel @JvmOverloads constructor(
    private val repository: RegistroRepository = RegistroRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegistroUiState())
    val uiState: StateFlow<RegistroUiState> = _uiState.asStateFlow()

    fun onNombreChange(valor: String) = actualizarCampo { it.copy(nombre = valor) }
    fun onApellidoChange(valor: String) = actualizarCampo { it.copy(apellido = valor) }
    fun onTipoDocumentoChange(valor: String) = actualizarCampo { it.copy(tipoDocumento = valor) }
    fun onNumeroDocumentoChange(valor: String) = actualizarCampo { it.copy(numeroDocumento = valor) }
    fun onEmailChange(valor: String) = actualizarCampo { it.copy(email = valor) }
    fun onCelularChange(valor: String) = actualizarCampo { it.copy(celular = valor) }
    fun onPasswordChange(valor: String) = actualizarCampo { it.copy(password = valor) }
    fun onAceptoTerminosChange(valor: Boolean) = actualizarCampo { it.copy(aceptoTerminos = valor) }

    private inline fun actualizarCampo(crossinline transformar: (RegistroUiState) -> RegistroUiState) {
        _uiState.update { transformar(it).copy(mensajeError = null) }
    }

    fun enviar() {
        val estadoActual = _uiState.value
        val errores = validar(estadoActual)
        if (errores.isNotEmpty()) {
            _uiState.update { it.copy(errores = errores) }
            return
        }

        _uiState.update { it.copy(cargando = true, errores = emptyMap(), mensajeError = null) }
        viewModelScope.launch {
            when (val resultado = repository.registrar(estadoActual.aSolicitud())) {
                is RegistroRepository.Resultado.Exito ->
                    _uiState.update { it.copy(cargando = false, registroExitoso = true) }

                is RegistroRepository.Resultado.Duplicado ->
                    _uiState.update { it.copy(cargando = false, mensajeError = resultado.mensaje) }

                is RegistroRepository.Resultado.ErrorValidacion ->
                    _uiState.update { it.copy(cargando = false, mensajeError = resultado.mensaje) }

                is RegistroRepository.Resultado.ErrorRed ->
                    _uiState.update { it.copy(cargando = false, mensajeError = resultado.mensaje) }
            }
        }
    }

    private fun validar(estado: RegistroUiState): Map<String, String> {
        val errores = mutableMapOf<String, String>()
        Validadores.errorNombre(estado.nombre)?.let { errores["nombre"] = it }
        Validadores.errorNombre(estado.apellido)?.let { errores["apellido"] = it }
        Validadores.errorDocumento(estado.numeroDocumento)?.let { errores["numeroDocumento"] = it }
        Validadores.errorEmail(estado.email)?.let { errores["email"] = it }
        Validadores.errorCelular(estado.celular)?.let { errores["celular"] = it }
        Validadores.errorPassword(estado.password)?.let { errores["password"] = it }
        if (!estado.aceptoTerminos) {
            errores["terminos"] = "Debes aceptar la política de tratamiento de datos para continuar"
        }
        return errores
    }
}
