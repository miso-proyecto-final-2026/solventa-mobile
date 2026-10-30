package com.solventa.mobile.firma

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solventa.mobile.red.SolicitudFirma
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** El canal con el que esta app firma; el servidor lo guarda en la evidencia de no repudio. */
const val CANAL_MOVIL = "MOVIL"

/**
 * Firma de la póliza (HU21, E4 del diseño): documento → lectura y casilla de revisión → código de un solo uso →
 * firma. El código nunca se guarda ni se escribe en un log; solo vive en el estado mientras el cliente lo escribe y
 * se borra al enviarlo. Firmar dos veces es seguro: el servidor devuelve la misma firma.
 */
class FirmaViewModel @JvmOverloads constructor(
    private val repository: FirmaRepository = FirmaRepository(),
    /** Modelo del teléfono: parte de la evidencia (dispositivo). */
    private val dispositivo: () -> String = { "Android" },
    private val segundosEntreCodigos: Int = 60,
    /** El PDF se genera justo después del cobro: se reintenta unos instantes hasta que esté. */
    private val intervaloDocumentoMs: Long = 1_000,
    private val maximoConsultasDocumento: Int = 15,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FirmaUiState())
    val uiState: StateFlow<FirmaUiState> = _uiState.asStateFlow()

    private var suscripcionId: String? = null
    private var cargando: Job? = null
    private var cuentaAtras: Job? = null

    /** Abre la firma de una suscripción. Llamarlo otra vez con la misma no reinicia lo que el cliente ya escribió. */
    fun iniciar(id: String) {
        if (suscripcionId == id) return
        suscripcionId = id
        _uiState.value = FirmaUiState()
        cargar()
    }

    fun reintentar() {
        if (_uiState.value.fase != FaseFirma.ERROR) return
        _uiState.update { it.copy(fase = FaseFirma.CARGANDO, error = null) }
        cargar()
    }

    private fun cargar() {
        val id = suscripcionId ?: return
        cargando?.cancel()
        cargando = viewModelScope.launch {
            repeat(maximoConsultasDocumento) { intento ->
                when (val resultado = repository.documento(id)) {
                    is FirmaRepository.Resultado.Exito -> {
                        val documento = resultado.valor
                        if (documento.estado == "FIRMADA") {
                            _uiState.update { it.copy(fase = FaseFirma.FIRMADA, documento = documento) }
                        } else {
                            _uiState.update { it.copy(fase = FaseFirma.LISTA, documento = documento, aviso = null) }
                            pedirCodigo()
                        }
                        return@launch
                    }

                    FirmaRepository.Resultado.DocumentoNoListo -> if (intento < maximoConsultasDocumento - 1) delay(intervaloDocumentoMs)
                    else -> return@launch terminarCon(resultado)
                }
            }
            fallar(ErrorFirma.DOCUMENTO_NO_LISTO)
        }
    }

    fun marcarRevisado(revisado: Boolean) =
        _uiState.update { it.copy(revisado = revisado, revisionPendiente = it.revisionPendiente && !revisado) }

    /** Solo dígitos y a lo más [LARGO_CODIGO]: pegar un código con espacios o guiones también funciona. */
    fun cambiarCodigo(texto: String) = _uiState.update {
        it.copy(codigo = texto.filter(Char::isDigit).take(LARGO_CODIGO), codigoIncompleto = false, aviso = it.aviso.takeUnless { a -> a == AvisoFirma.CODIGO_INCORRECTO })
    }

    fun reenviarCodigo() {
        if (_uiState.value.fase != FaseFirma.LISTA || _uiState.value.segundosParaReenviar > 0) return
        _uiState.update { it.copy(codigoReenviado = false) }
        viewModelScope.launch { pedirCodigo(reenvio = true) }
    }

    private suspend fun pedirCodigo(reenvio: Boolean = false) {
        val id = suscripcionId ?: return
        when (repository.pedirCodigo(id)) {
            is FirmaRepository.Resultado.Exito -> {
                _uiState.update { it.copy(codigoReenviado = reenvio, aviso = it.aviso.takeUnless { a -> a == AvisoFirma.SIN_CONEXION }) }
                iniciarCuentaAtras()
            }
            // No pedirlo no impide firmar si el cliente ya tiene un código: se avisa y se puede reintentar.
            FirmaRepository.Resultado.SinConexion -> _uiState.update { it.copy(aviso = AvisoFirma.SIN_CONEXION) }
            else -> _uiState.update { it.copy(aviso = AvisoFirma.SERVICIO) }
        }
    }

    private fun iniciarCuentaAtras() {
        cuentaAtras?.cancel()
        _uiState.update { it.copy(segundosParaReenviar = segundosEntreCodigos) }
        cuentaAtras = viewModelScope.launch {
            while (_uiState.value.segundosParaReenviar > 0) {
                delay(1_000)
                _uiState.update { it.copy(segundosParaReenviar = (it.segundosParaReenviar - 1).coerceAtLeast(0)) }
            }
        }
    }

    // --- lector del PDF ---------------------------------------------------------------------------------------------

    fun leer() {
        val id = suscripcionId ?: return
        val estado = _uiState.value
        if (estado.fase != FaseFirma.LISTA || estado.cargandoPdf) return
        _uiState.update { it.copy(cargandoPdf = true, errorPdf = false) }
        viewModelScope.launch {
            when (val resultado = repository.pdf(id)) {
                is FirmaRepository.Resultado.Exito -> _uiState.update { it.copy(pdf = ContenidoPdf(resultado.valor), cargandoPdf = false) }
                else -> _uiState.update { it.copy(cargandoPdf = false, errorPdf = true) }
            }
        }
    }

    fun cerrarLector() = _uiState.update { it.copy(pdf = null, errorPdf = false) }

    // --- firmar -----------------------------------------------------------------------------------------------------

    fun firmar() {
        val id = suscripcionId ?: return
        val estado = _uiState.value
        val documento = estado.documento ?: return
        if (estado.fase != FaseFirma.LISTA) return
        if (!estado.revisado || estado.codigo.length != LARGO_CODIGO) {
            _uiState.update { it.copy(revisionPendiente = !it.revisado, codigoIncompleto = it.codigo.length != LARGO_CODIGO) }
            return
        }
        val solicitud = SolicitudFirma(
            documentoSha256 = documento.documentoSha256,
            documentoRevisado = true,
            codigo = estado.codigo,
            canal = CANAL_MOVIL,
            dispositivo = dispositivo().take(200),
        )
        // El código ya no vive en el estado desde que se envía.
        _uiState.update { it.copy(fase = FaseFirma.FIRMANDO, codigo = "", aviso = null, revisionPendiente = false, codigoIncompleto = false) }
        cargando?.cancel()
        cargando = viewModelScope.launch {
            when (val resultado = repository.firmar(id, solicitud)) {
                is FirmaRepository.Resultado.Exito -> _uiState.update { it.copy(fase = FaseFirma.FIRMADA) }
                is FirmaRepository.Resultado.CodigoInvalido -> _uiState.update {
                    it.copy(fase = FaseFirma.LISTA, aviso = AvisoFirma.CODIGO_INCORRECTO, intentosRestantes = resultado.intentosRestantes)
                }
                FirmaRepository.Resultado.DocumentoAlterado -> recargarTrasCambio()
                FirmaRepository.Resultado.Desconocido -> comprobarSiSeFirmo()
                FirmaRepository.Resultado.SinConexion ->
                    _uiState.update { it.copy(fase = FaseFirma.LISTA, aviso = AvisoFirma.SIN_CONEXION) }
                else -> terminarCon(resultado)
            }
        }
    }

    /** No se supo si el envío llegó: se consulta antes de pedirle otro código al cliente. */
    private suspend fun comprobarSiSeFirmo() {
        val id = suscripcionId ?: return
        when (val resultado = repository.documento(id)) {
            is FirmaRepository.Resultado.Exito ->
                if (resultado.valor.estado == "FIRMADA") _uiState.update { it.copy(fase = FaseFirma.FIRMADA) }
                else _uiState.update { it.copy(fase = FaseFirma.LISTA, aviso = AvisoFirma.SIN_CONEXION) }
            else -> fallar(ErrorFirma.RESULTADO_DESCONOCIDO)
        }
    }

    /** El PDF cambió desde que se abrió: se vuelve a leer su hash y el cliente revisa de nuevo. */
    private suspend fun recargarTrasCambio() {
        val id = suscripcionId ?: return
        when (val resultado = repository.documento(id)) {
            is FirmaRepository.Resultado.Exito -> _uiState.update {
                it.copy(fase = FaseFirma.LISTA, documento = resultado.valor, revisado = false, aviso = AvisoFirma.DOCUMENTO_CAMBIO)
            }
            else -> terminarCon(resultado)
        }
    }

    private fun terminarCon(resultado: FirmaRepository.Resultado<*>) = when (resultado) {
        FirmaRepository.Resultado.Vencida -> _uiState.update { it.copy(fase = FaseFirma.VENCIDA, codigo = "") }
        FirmaRepository.Resultado.Fallida -> _uiState.update { it.copy(fase = FaseFirma.FALLIDA, codigo = "") }
        FirmaRepository.Resultado.SinSesion -> fallar(ErrorFirma.SESION_VENCIDA)
        FirmaRepository.Resultado.SinConexion -> fallar(ErrorFirma.SIN_CONEXION)
        FirmaRepository.Resultado.FirmaNoDisponible -> fallar(ErrorFirma.NO_DISPONIBLE)
        else -> fallar(ErrorFirma.SERVICIO)
    }

    private fun fallar(error: ErrorFirma) = _uiState.update { it.copy(fase = FaseFirma.ERROR, error = error) }
}
