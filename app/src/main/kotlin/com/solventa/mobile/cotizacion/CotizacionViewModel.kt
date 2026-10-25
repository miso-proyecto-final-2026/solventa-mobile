package com.solventa.mobile.cotizacion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Flujo de cotización (HU37): elegir producto → datos del seguro → cargando → resultado. No calcula ningún
 * precio: solo valida lo que ingresa el usuario y muestra lo que devuelve MS Cotización.
 */
class CotizacionViewModel @JvmOverloads constructor(
    private val repository: CotizacionRepository = CotizacionRepository(),
    /** Milisegundos monótonos; la diferencia entre dos lecturas es el tiempo observado por el cliente. */
    private val reloj: () -> Long = { System.nanoTime() / 1_000_000 },
) : ViewModel() {

    private val _uiState = MutableStateFlow(CotizacionUiState())
    val uiState: StateFlow<CotizacionUiState> = _uiState.asStateFlow()

    fun elegirProducto(id: String) {
        val producto = Catalogo.porId(id) ?: return
        _uiState.value = CotizacionUiState(fase = FaseCotizacion.FORMULARIO, producto = producto)
    }

    fun cambiarValor(clave: String, valor: String) {
        val producto = _uiState.value.producto ?: return
        val campo = producto.campos.firstOrNull { it.clave == clave } ?: return
        val error = campo.validar(valor)
        _uiState.update {
            it.copy(
                valores = it.valores + (clave to valor),
                errores = if (error == null) it.errores - clave else it.errores + (clave to error),
                erroresServidor = it.erroresServidor - clave,
            )
        }
    }

    fun solicitar() {
        val estado = _uiState.value
        val producto = estado.producto ?: return
        if (estado.fase == FaseCotizacion.CARGANDO) return
        val errores = producto.validar(estado.valores)
        if (errores.isNotEmpty()) {
            _uiState.update { it.copy(errores = errores, erroresServidor = emptyMap(), error = null) }
            return
        }
        _uiState.update {
            it.copy(fase = FaseCotizacion.CARGANDO, errores = emptyMap(), erroresServidor = emptyMap(), error = null)
        }
        viewModelScope.launch {
            val inicio = reloj()
            val resultado = repository.cotizar(producto.id, producto.datos(estado.valores))
            val observadoMs = reloj() - inicio
            when (resultado) {
                is CotizacionRepository.Resultado.Exito -> {
                    _uiState.update { it.copy(fase = FaseCotizacion.RESULTADO, cotizacion = resultado.cotizacion) }
                    repository.registrarLatencia(resultado.cotizacion.id, observadoMs.toDouble())
                }

                is CotizacionRepository.Resultado.Validacion -> volverConError(erroresServidor = resultado.errores)
                CotizacionRepository.Resultado.TiempoAgotado -> volverConError(ErrorCotizacion.TIEMPO_AGOTADO)
                CotizacionRepository.Resultado.SinConexion -> volverConError(ErrorCotizacion.SIN_CONEXION)
                is CotizacionRepository.Resultado.ErrorServicio -> volverConError(ErrorCotizacion.SERVICIO)
                CotizacionRepository.Resultado.SinSesion -> volverConError(ErrorCotizacion.SESION_VENCIDA)
            }
        }
    }

    /** Repite la solicitud con lo que el usuario ya ingresó: ante un error no se pierde nada. */
    fun reintentar() = solicitar()

    /** Vuelve al formulario con lo que el usuario ya ingresó, para cambiar un dato y cotizar de nuevo. */
    fun modificarDatos() {
        _uiState.update {
            if (it.producto == null) it else it.copy(fase = FaseCotizacion.FORMULARIO, cotizacion = null, error = null)
        }
    }

    fun volverAProductos() = nuevaCotizacion()

    fun nuevaCotizacion() {
        _uiState.value = CotizacionUiState()
    }

    private fun volverConError(error: ErrorCotizacion? = null, erroresServidor: Map<String, String> = emptyMap()) =
        _uiState.update {
            it.copy(fase = FaseCotizacion.FORMULARIO, error = error, erroresServidor = erroresServidor)
        }
}
