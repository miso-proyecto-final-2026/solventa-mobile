package com.solventa.mobile.compra

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solventa.mobile.red.CotizacionResponse
import com.solventa.mobile.red.SuscripcionResponse
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Compra de la oferta (HU17): resumen y términos → medio de pago → seguimiento de la saga → resultado. No calcula
 * precios: muestra los de MS Cotización. Cada intento de compra tiene su clave de idempotencia; se conserva mientras
 * el cliente repite el mismo intento (corte de conexión). Tras un cobro rechazado no se reintenta desde la app: el
 * servidor deja la saga esperando a HU19 y un envío nuevo para la misma oferta daría 409.
 */
class CompraViewModel @JvmOverloads constructor(
    private val repository: SuscripcionRepository = SuscripcionRepository(),
    private val tokenizador: TokenizadorPago = TokenizadorSimulado(),
    private val nuevaClave: () -> String = { UUID.randomUUID().toString() },
    private val ahora: () -> Instant = { Instant.now() },
    private val intervaloConsultaMs: Long = 1_500,
    private val maximoConsultas: Int = 40,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompraUiState())
    val uiState: StateFlow<CompraUiState> = _uiState.asStateFlow()

    private var claveIdempotencia: String? = null
    private var tokenPago: String? = null
    private var suscripcionId: String? = null
    private var trabajo: Job? = null

    fun iniciar(cotizacion: CotizacionResponse) {
        if (_uiState.value.cotizacion?.id == cotizacion.id) return
        trabajo?.cancel()
        claveIdempotencia = null
        tokenPago = null
        suscripcionId = null
        _uiState.value = CompraUiState(
            fase = if (vencida(cotizacion)) FaseCompra.OFERTA_VENCIDA else FaseCompra.OFERTA,
            cotizacion = cotizacion,
        )
    }

    fun aceptarTerminos(acepta: Boolean) =
        _uiState.update { it.copy(aceptaTerminos = acepta, terminosPendientes = it.terminosPendientes && !acepta) }

    fun continuarAPago() {
        val estado = _uiState.value
        if (estado.fase != FaseCompra.OFERTA) return
        if (!estado.aceptaTerminos) {
            _uiState.update { it.copy(terminosPendientes = true) }
            return
        }
        _uiState.update { it.copy(fase = FaseCompra.PAGO) }
    }

    fun volverALaOferta() = _uiState.update { if (it.fase == FaseCompra.PAGO) it.copy(fase = FaseCompra.OFERTA) else it }

    fun cambiarTarjeta(numero: String, vencimiento: String, cvv: String) = _uiState.update {
        it.copy(tarjeta = DatosTarjeta(numero.filter(Char::isDigit), vencimiento, cvv.filter(Char::isDigit)), erroresTarjeta = emptySet())
    }

    fun pagar() {
        val estado = _uiState.value
        val cotizacion = estado.cotizacion ?: return
        if (estado.fase != FaseCompra.PAGO) return
        val errores = ValidadorTarjeta.validar(estado.tarjeta)
        if (errores.isNotEmpty()) {
            _uiState.update { it.copy(erroresTarjeta = errores) }
            return
        }
        if (vencida(cotizacion)) {
            _uiState.update { it.copy(fase = FaseCompra.OFERTA_VENCIDA, tarjeta = DatosTarjeta()) }
            return
        }
        val tarjeta = estado.tarjeta
        // Desde aquí la tarjeta ya no vive en el estado: solo queda el token.
        _uiState.update { it.copy(fase = FaseCompra.ENVIANDO, tarjeta = DatosTarjeta(), erroresTarjeta = emptySet(), error = null) }
        claveIdempotencia = nuevaClave()
        trabajo?.cancel()
        trabajo = viewModelScope.launch {
            tokenPago = tokenizador.tokenizar(tarjeta)
            enviar(cotizacion.id)
        }
    }

    /** Repite lo que quedó a medias con la misma clave: no puede cobrar dos veces. */
    fun reintentar() {
        val cotizacion = _uiState.value.cotizacion ?: return
        if (_uiState.value.fase != FaseCompra.ERROR) return
        _uiState.update {
            it.copy(fase = if (suscripcionId != null) FaseCompra.PROGRESO else FaseCompra.ENVIANDO, error = null)
        }
        trabajo?.cancel()
        trabajo = viewModelScope.launch {
            val id = suscripcionId
            when {
                id != null -> seguir(id)
                tokenPago != null && claveIdempotencia != null -> enviar(cotizacion.id)
                else -> _uiState.update { it.copy(fase = FaseCompra.PAGO) }
            }
        }
    }

    private suspend fun enviar(cotizacionId: String) {
        val token = tokenPago ?: return
        val clave = claveIdempotencia ?: return
        when (val resultado = repository.iniciar(cotizacionId, token, clave)) {
            is SuscripcionRepository.Resultado.Exito -> {
                suscripcionId = resultado.saga.id
                // El token es de un solo uso y ya está en el servidor: no hace falta conservarlo.
                tokenPago = null
                if (!aplicar(resultado.saga)) seguir(resultado.saga.id)
            }

            SuscripcionRepository.Resultado.OfertaVencida -> fase(FaseCompra.OFERTA_VENCIDA)
            SuscripcionRepository.Resultado.OfertaYaUsada -> fallar(ErrorCompra.OFERTA_YA_USADA)
            SuscripcionRepository.Resultado.SinSesion -> fallar(ErrorCompra.SESION_VENCIDA)
            SuscripcionRepository.Resultado.ResultadoDesconocido -> fallar(ErrorCompra.RESULTADO_DESCONOCIDO)
            SuscripcionRepository.Resultado.SinConexion -> fallar(ErrorCompra.SIN_CONEXION)
            SuscripcionRepository.Resultado.ServicioNoDisponible, is SuscripcionRepository.Resultado.ErrorServicio ->
                fallar(ErrorCompra.SERVICIO)
        }
    }

    /** Consulta la saga hasta que termine para el cliente; la pantalla se actualiza sola (criterio 4). */
    private suspend fun seguir(id: String) {
        var fallosSeguidos = 0
        repeat(maximoConsultas) {
            delay(intervaloConsultaMs)
            when (val resultado = repository.consultar(id)) {
                is SuscripcionRepository.Resultado.Exito -> {
                    fallosSeguidos = 0
                    if (aplicar(resultado.saga)) return
                }

                SuscripcionRepository.Resultado.SinSesion -> return fallar(ErrorCompra.SESION_VENCIDA)
                else -> if (++fallosSeguidos >= 3) return fallar(ErrorCompra.SIN_CONEXION)
            }
        }
        fallar(ErrorCompra.SEGUIMIENTO_LENTO)
    }

    /** Refleja la saga en la pantalla. Devuelve `true` si ya terminó para el cliente. */
    private fun aplicar(saga: SuscripcionResponse): Boolean {
        suscripcionId = saga.id
        when (val progreso = ProgresoCompra.de(saga)) {
            is ProgresoCompra.EnCurso -> {
                _uiState.update { it.copy(fase = FaseCompra.PROGRESO, paso = progreso.paso, error = null) }
                return false
            }

            is ProgresoCompra.Exitosa ->
                _uiState.update { it.copy(fase = FaseCompra.EXITOSA, referenciaPago = progreso.referencia, fechaPago = progreso.fecha) }
            is ProgresoCompra.DecisionRechazada ->
                _uiState.update { it.copy(fase = FaseCompra.DECISION_RECHAZADA, motivos = progreso.motivos) }
            is ProgresoCompra.EnRevision ->
                _uiState.update { it.copy(fase = FaseCompra.EN_REVISION, ajustada = progreso.ajustada) }
            is ProgresoCompra.CobroRechazado -> _uiState.update {
                it.copy(fase = FaseCompra.COBRO_RECHAZADO, motivoCobro = progreso.motivo, graciaHasta = progreso.graciaHasta)
            }
            ProgresoCompra.Fallida -> _uiState.update { it.copy(fase = FaseCompra.ERROR, error = ErrorCompra.SERVICIO) }
        }
        return true
    }

    private fun fase(fase: FaseCompra) = _uiState.update { it.copy(fase = fase) }

    private fun fallar(error: ErrorCompra) = _uiState.update { it.copy(fase = FaseCompra.ERROR, error = error) }

    private fun vencida(cotizacion: CotizacionResponse): Boolean =
        runCatching { !Instant.parse(cotizacion.vigenteHasta).isAfter(ahora()) }.getOrDefault(false)
}
