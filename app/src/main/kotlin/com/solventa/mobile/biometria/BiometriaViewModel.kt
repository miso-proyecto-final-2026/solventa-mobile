package com.solventa.mobile.biometria

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solventa.mobile.resultado.ResultadoRepository
import java.security.GeneralSecurityException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val KYC_VERIFICADO = "VERIFICADO"

/**
 * Acceso biométrico (HU04). El servidor solo registra qué método se activó; la clave que exige la biometría vive en
 * el dispositivo. Activar primero confirma la contraseña y el KYC con el servidor y luego pide la biometría al
 * sistema; si el cliente cancela, la activación se revierte. Al abrir la pantalla se reconcilian servidor y clave
 * local: si la clave ya no sirve (cambiaron las biometrías del dispositivo, criterio 8), se desactiva en ambos lados.
 */
class BiometriaViewModel @JvmOverloads constructor(
    private val proveedor: ProveedorCapacidad,
    private val clave: ClaveBiometrica,
    private val repository: BiometriaRepository = BiometriaRepository(),
    private val resultado: ResultadoRepository = ResultadoRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(BiometriaUiState())
    val uiState: StateFlow<BiometriaUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _uiState.update { it.copy(fase = FaseBiometria.CARGANDO, error = null, pendiente = null, aviso = null) }
        viewModelScope.launch {
            val capacidad = proveedor.consultar()
            when (val respuesta = repository.consultar()) {
                is BiometriaRepository.Resultado.Exito -> {
                    val kyc = (resultado.estado() as? ResultadoRepository.Consulta.Exito)?.valor?.estadoKyc
                    reconciliar(capacidad, kyc?.let { it == KYC_VERIFICADO }, respuesta.biometria?.activo == true, respuesta.biometria?.metodo)
                }
                else -> falla(respuesta, AccionAcceso.CONSULTAR, capacidad)
            }
        }
    }

    private suspend fun reconciliar(capacidad: Capacidad, kycAprobado: Boolean?, activoEnServidor: Boolean, metodo: String?) {
        val estadoClave = clave.estado()
        if (activoEnServidor && estadoClave != EstadoClave.VALIDA) {
            // La clave local ya no existe o dejó de servir: el servidor no puede seguir diciendo que está activo.
            clave.borrar()
            repository.desactivar()
            _uiState.update {
                it.copy(
                    fase = FaseBiometria.ESTADO,
                    capacidad = capacidad,
                    kycAprobado = kycAprobado,
                    activo = false,
                    metodoActivo = null,
                    aviso = AvisoAcceso.CLAVE_INVALIDADA,
                )
            }
            return
        }
        if (!activoEnServidor && estadoClave != EstadoClave.AUSENTE) clave.borrar()
        _uiState.update {
            it.copy(
                fase = FaseBiometria.ESTADO,
                capacidad = capacidad,
                kycAprobado = kycAprobado,
                activo = activoEnServidor,
                metodoActivo = if (activoEnServidor) MetodoBiometrico.desde(metodo) else null,
            )
        }
    }

    fun abrirFormulario() {
        val estado = _uiState.value
        if (estado.fase != FaseBiometria.ESTADO || !estado.puedeActivar) return
        _uiState.update {
            it.copy(
                fase = FaseBiometria.FORMULARIO,
                metodoElegido = it.capacidad.metodos.firstOrNull(),
                error = null,
                aviso = null,
            )
        }
    }

    fun elegirMetodo(metodo: MetodoBiometrico) {
        if (_uiState.value.fase != FaseBiometria.FORMULARIO || metodo !in _uiState.value.capacidad.metodos) return
        _uiState.update { it.copy(metodoElegido = metodo) }
    }

    fun cancelarFormulario() {
        if (_uiState.value.fase != FaseBiometria.FORMULARIO) return
        _uiState.update { it.copy(fase = FaseBiometria.ESTADO, error = null, metodoElegido = null) }
    }

    fun confirmarActivacion(contrasena: String, autenticador: Autenticador) {
        val estado = _uiState.value
        val metodo = estado.metodoElegido
        if (estado.fase != FaseBiometria.FORMULARIO || metodo == null) return
        if (contrasena.isBlank()) {
            _uiState.update { it.copy(error = ErrorAcceso.CONTRASENA_VACIA) }
            return
        }
        _uiState.update { it.copy(fase = FaseBiometria.PROCESANDO, error = null) }
        viewModelScope.launch {
            when (val respuesta = repository.activar(metodo, contrasena)) {
                is BiometriaRepository.Resultado.Exito -> pedirBiometria(metodo, autenticador)
                BiometriaRepository.Resultado.ContrasenaIncorrecta ->
                    _uiState.update { it.copy(fase = FaseBiometria.FORMULARIO, error = ErrorAcceso.CONTRASENA_INCORRECTA) }
                BiometriaRepository.Resultado.KycNoAprobado ->
                    _uiState.update {
                        it.copy(fase = FaseBiometria.ESTADO, kycAprobado = false, error = ErrorAcceso.KYC_NO_APROBADO)
                    }
                else -> _uiState.update {
                    it.copy(fase = FaseBiometria.FORMULARIO, error = errorDe(respuesta))
                }
            }
        }
    }

    /** El servidor ya registró la activación; sin la biometría del sistema se revierte para no dejarlas desalineadas. */
    private suspend fun pedirBiometria(metodo: MetodoBiometrico, autenticador: Autenticador) {
        val cifrador = try {
            clave.crearParaAutenticar()
        } catch (_: GeneralSecurityException) {
            revertir(ErrorAcceso.AUTENTICACION)
            return
        }
        when (autenticador.confirmar(cifrador)) {
            ResultadoAutenticacion.Exito -> _uiState.update {
                it.copy(
                    fase = FaseBiometria.ESTADO,
                    activo = true,
                    metodoActivo = metodo,
                    metodoElegido = null,
                    aviso = AvisoAcceso.ACTIVADO,
                )
            }
            ResultadoAutenticacion.Cancelado -> revertir(ErrorAcceso.CANCELADO)
            ResultadoAutenticacion.Error -> revertir(ErrorAcceso.AUTENTICACION)
        }
    }

    private suspend fun revertir(error: ErrorAcceso) {
        clave.borrar()
        repository.desactivar()
        _uiState.update { it.copy(fase = FaseBiometria.ESTADO, activo = false, metodoActivo = null, metodoElegido = null, error = error) }
    }

    fun pedirDesactivar() {
        val estado = _uiState.value
        if (estado.fase != FaseBiometria.ESTADO || !estado.activo) return
        _uiState.update { it.copy(fase = FaseBiometria.CONFIRMANDO_DESACTIVAR, error = null, aviso = null) }
    }

    fun cancelarDesactivar() {
        if (_uiState.value.fase != FaseBiometria.CONFIRMANDO_DESACTIVAR) return
        _uiState.update { it.copy(fase = FaseBiometria.ESTADO) }
    }

    fun confirmarDesactivar() {
        if (_uiState.value.fase != FaseBiometria.CONFIRMANDO_DESACTIVAR) return
        desactivar()
    }

    /** Repite lo que falló: la consulta o la desactivación ya confirmada por el cliente. */
    fun reintentar() {
        when (_uiState.value.pendiente) {
            AccionAcceso.CONSULTAR -> cargar()
            AccionAcceso.DESACTIVAR -> desactivar()
            null -> Unit
        }
    }

    private fun desactivar() {
        // La clave local se borra primero (criterio 7): aunque el servidor no responda, la biometría ya no abre nada.
        clave.borrar()
        _uiState.update { it.copy(fase = FaseBiometria.PROCESANDO, error = null, pendiente = null) }
        viewModelScope.launch {
            when (val respuesta = repository.desactivar()) {
                is BiometriaRepository.Resultado.Exito -> _uiState.update {
                    it.copy(fase = FaseBiometria.ESTADO, activo = false, metodoActivo = null, aviso = AvisoAcceso.DESACTIVADO)
                }
                else -> _uiState.update {
                    it.copy(fase = FaseBiometria.ESTADO, error = errorDe(respuesta), pendiente = AccionAcceso.DESACTIVAR)
                }
            }
        }
    }

    private fun falla(respuesta: BiometriaRepository.Resultado, pendiente: AccionAcceso, capacidad: Capacidad) {
        _uiState.update {
            it.copy(fase = FaseBiometria.ESTADO, capacidad = capacidad, error = errorDe(respuesta), pendiente = pendiente)
        }
    }

    private fun errorDe(respuesta: BiometriaRepository.Resultado): ErrorAcceso =
        when (respuesta) {
            BiometriaRepository.Resultado.SinSesion -> ErrorAcceso.SESION_VENCIDA
            BiometriaRepository.Resultado.TiempoAgotado -> ErrorAcceso.TIEMPO_AGOTADO
            BiometriaRepository.Resultado.SinConexion -> ErrorAcceso.SIN_CONEXION
            else -> ErrorAcceso.SERVICIO
        }
}
