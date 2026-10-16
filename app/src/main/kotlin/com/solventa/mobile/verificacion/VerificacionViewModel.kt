package com.solventa.mobile.verificacion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Flujo de HU02: permiso de cámara → documento (anverso, reverso) → revisión → selfie con reto de
 * vida → envío. Las imágenes se conservan hasta que el servidor las tiene, de modo que un fallo de
 * red no obliga a capturarlas otra vez.
 */
class VerificacionViewModel @JvmOverloads constructor(
    private val repository: VerificacionRepository = VerificacionRepository(),
    private val analizador: AnalizadorCalidad = AnalizadorCalidadAndroid(),
    private val borrarArchivo: (String) -> Unit = { runCatching { File(it).delete() } },
    private val nuevaClave: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(VerificacionUiState())
    val uiState: StateFlow<VerificacionUiState> = _uiState.asStateFlow()

    /** Una por conjunto de capturas: los reintentos de red la reutilizan (idempotencia). */
    private var claveIdempotencia: String? = null

    // -- permiso de cámara -----------------------------------------------------------------

    fun onPermiso(concedido: Boolean) {
        _uiState.update {
            when {
                concedido && it.paso == PasoVerificacion.PERMISO ->
                    it.copy(paso = PasoVerificacion.ANVERSO, permisoDenegado = false)
                concedido -> it.copy(permisoDenegado = false)
                // Sin permiso no se avanza: la pantalla explica cómo habilitarlo.
                else -> it.copy(permisoDenegado = true)
            }
        }
    }

    // -- capturas --------------------------------------------------------------------------

    fun onCapturada(ruta: String) {
        val estado = _uiState.value
        if (estado.analizando || estado.capturaPendiente != null || !estado.paso.admiteCaptura()) return
        _uiState.update { it.copy(analizando = true, problemaCalidad = null, error = null) }
        viewModelScope.launch {
            val problema = analizador.analizar(ruta)
            if (problema != null) {
                borrarArchivo(ruta)
                _uiState.update { it.copy(analizando = false, problemaCalidad = problema) }
            } else {
                _uiState.update { it.copy(analizando = false, capturaPendiente = ruta) }
            }
        }
    }

    fun repetirCaptura() {
        _uiState.value.capturaPendiente?.let(borrarArchivo)
        _uiState.update { it.copy(capturaPendiente = null, problemaCalidad = null) }
    }

    fun usarCaptura() {
        val estado = _uiState.value
        val ruta = estado.capturaPendiente ?: return
        when (estado.paso) {
            PasoVerificacion.ANVERSO -> _uiState.update {
                // Si ya hay reverso se está repitiendo desde la revisión: se vuelve a ella.
                val siguiente = if (it.reverso != null) PasoVerificacion.REVISION else PasoVerificacion.REVERSO
                it.copy(anverso = ruta, capturaPendiente = null, paso = siguiente)
            }
            PasoVerificacion.REVERSO -> _uiState.update {
                it.copy(reverso = ruta, capturaPendiente = null, paso = PasoVerificacion.REVISION)
            }
            PasoVerificacion.SELFIE -> {
                _uiState.update { it.copy(selfie = ruta, capturaPendiente = null) }
                enviar()
            }
            else -> Unit
        }
    }

    /** Desde la revisión: vuelve a capturar el anverso o el reverso. */
    fun repetirParte(paso: PasoVerificacion) {
        if (paso != PasoVerificacion.ANVERSO && paso != PasoVerificacion.REVERSO) return
        val estado = _uiState.value
        val ruta = if (paso == PasoVerificacion.ANVERSO) estado.anverso else estado.reverso
        ruta?.let(borrarArchivo)
        claveIdempotencia = null
        _uiState.update {
            if (paso == PasoVerificacion.ANVERSO) it.copy(anverso = null, paso = paso)
            else it.copy(reverso = null, paso = paso)
        }
    }

    fun continuarDesdeRevision() {
        if (_uiState.value.paso == PasoVerificacion.REVISION) {
            _uiState.update { it.copy(paso = PasoVerificacion.SELFIE) }
        }
    }

    // -- envío -----------------------------------------------------------------------------

    /** También reintenta un envío fallido por red, con las mismas imágenes y la misma clave. */
    fun enviar() {
        val estado = _uiState.value
        val anverso = estado.anverso
        val reverso = estado.reverso
        val selfie = estado.selfie
        if (anverso == null || reverso == null || selfie == null) return
        val clave = claveIdempotencia ?: nuevaClave().also { claveIdempotencia = it }
        _uiState.update { it.copy(paso = PasoVerificacion.ENVIANDO, error = null) }
        viewModelScope.launch {
            when (val resultado = repository.enviar(clave, anverso, reverso, selfie)) {
                is VerificacionRepository.Resultado.Exito -> alRecibirResultado(resultado)
                is VerificacionRepository.Resultado.Fallo -> alFallar(resultado)
            }
        }
    }

    private fun alRecibirResultado(exito: VerificacionRepository.Resultado.Exito) {
        val respuesta = exito.respuesta
        val estadoResultado = when (respuesta.estado) {
            "VERIFICADO" -> EstadoResultado.VERIFICADO
            "RECHAZADO" -> EstadoResultado.RECHAZADO
            "EN_REVISION" -> EstadoResultado.EN_REVISION
            else -> null
        }
        if (estadoResultado == null) {
            _uiState.update { it.copy(error = ErrorEnvio.INESPERADO) }
            return
        }
        // El servidor ya tiene las imágenes (también en revisión): se borran del teléfono.
        if (estadoResultado != EstadoResultado.RECHAZADO) borrarCapturas()
        _uiState.update {
            it.copy(
                paso = PasoVerificacion.RESULTADO,
                resultado = estadoResultado,
                intentosRestantes = respuesta.intentosRestantes,
                error = null,
            )
        }
    }

    private fun alFallar(fallo: VerificacionRepository.Resultado.Fallo) {
        when (fallo.error) {
            // Se conservan las imágenes: la pantalla ofrece reintentar el envío.
            ErrorEnvio.SIN_CONEXION, ErrorEnvio.INESPERADO ->
                _uiState.update { it.copy(error = fallo.error) }

            ErrorEnvio.BLOQUEADO -> _uiState.update {
                it.copy(
                    paso = PasoVerificacion.RESULTADO,
                    resultado = EstadoResultado.BLOQUEADO,
                    reintentarEnSegundos = fallo.reintentarEnSegundos,
                    error = null,
                )
            }

            // El servidor rechazó una imagen: hay que capturar de nuevo.
            ErrorEnvio.IMAGEN_INVALIDA -> {
                borrarCapturas()
                claveIdempotencia = null
                _uiState.update {
                    VerificacionUiState(paso = PasoVerificacion.ANVERSO, error = ErrorEnvio.IMAGEN_INVALIDA)
                }
            }

            ErrorEnvio.SIN_SESION, ErrorEnvio.YA_PROCESADA -> _uiState.update {
                it.copy(paso = PasoVerificacion.RESULTADO, error = fallo.error)
            }
        }
    }

    /** Tras un rechazo: nuevas capturas y un nuevo intento. */
    fun reintentarVerificacion() {
        borrarCapturas()
        claveIdempotencia = null
        _uiState.update { VerificacionUiState(paso = PasoVerificacion.ANVERSO) }
    }

    fun finalizar() {
        _uiState.update { it.copy(finalizado = true) }
    }

    private fun borrarCapturas() {
        val estado = _uiState.value
        listOfNotNull(estado.anverso, estado.reverso, estado.selfie, estado.capturaPendiente)
            .forEach(borrarArchivo)
        _uiState.update { it.copy(anverso = null, reverso = null, selfie = null, capturaPendiente = null) }
    }

    private fun PasoVerificacion.admiteCaptura() =
        this == PasoVerificacion.ANVERSO || this == PasoVerificacion.REVERSO || this == PasoVerificacion.SELFIE
}
