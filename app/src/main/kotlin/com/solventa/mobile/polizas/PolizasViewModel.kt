package com.solventa.mobile.polizas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solventa.mobile.BuildConfig
import com.solventa.mobile.firma.ContenidoPdf
import com.solventa.mobile.red.PolizaResponse
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * «Mis pólizas» (HU35, diseño F1 y F2): la billetera, el detalle de una póliza con su credencial y los documentos.
 * El enlace del QR vence (24 h), así que se pide de nuevo cada vez que se abre un detalle. Solo se piden pólizas del
 * propio cliente: el servidor responde 403 si el `cliente_id` no es el del token.
 */
class PolizasViewModel @JvmOverloads constructor(
    private val repository: PolizasRepository = PolizasRepository(),
    private val reloj: () -> Instant = { Instant.now() },
    /** Idioma del teléfono (`es-CO`, `en-US`): el certificado y su enlace salen en él. */
    private val idioma: () -> String = { Locale.getDefault().toLanguageTag() },
    private val baseUrl: String = BuildConfig.BASE_URL,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PolizasUiState())
    val uiState: StateFlow<PolizasUiState> = _uiState.asStateFlow()

    private var cargando: Job? = null
    private var enlace: Job? = null
    private var descargando: Job? = null

    /** Suscripción cuyo detalle se pidió antes de que llegara la lista (por ejemplo, desde una notificación). */
    private var pendienteDeAbrir: String? = null
    private var iniciado = false

    /**
     * Al entrar a «Mis pólizas». La primera vez muestra la carga; las siguientes refrescan en silencio para que una compra
     * recién hecha aparezca sin que el cliente vea parpadear la lista (y sin perderla si la red falla).
     */
    fun iniciar() {
        val primera = !iniciado
        iniciado = true
        cargar(silencioso = !primera)
    }

    fun recargar() {
        _uiState.update { it.copy(fase = FasePolizas.CARGANDO, error = null) }
        cargar(silencioso = false)
    }

    private fun cargar(silencioso: Boolean) {
        cargando?.cancel()
        cargando = viewModelScope.launch {
            when (val resultado = repository.billetera()) {
                is PolizasRepository.Resultado.Exito -> {
                    _uiState.update { it.copy(fase = FasePolizas.LISTA, polizas = resultado.valor, ahora = reloj(), error = null) }
                    pendienteDeAbrir?.let { id ->
                        pendienteDeAbrir = null
                        abrirPorSuscripcion(id)
                    }
                }

                PolizasRepository.Resultado.SinSesion -> fallar(ErrorPolizas.SESION_VENCIDA)
                // Un refresco silencioso que falla deja la lista que ya se tenía.
                PolizasRepository.Resultado.SinConexion -> if (!silencioso) fallar(ErrorPolizas.SIN_CONEXION)
                else -> if (!silencioso) fallar(ErrorPolizas.SERVICIO)
            }
        }
    }

    private fun fallar(error: ErrorPolizas) = _uiState.update { it.copy(fase = FasePolizas.ERROR, error = error) }

    fun elegirFiltro(filtro: FiltroPolizas) = _uiState.update { it.copy(filtro = filtro) }

    /** Abre el detalle de la póliza de una suscripción; si la lista aún no llegó, lo hace cuando llegue. */
    fun abrirPorSuscripcion(suscripcionId: String) {
        val poliza = _uiState.value.polizas.firstOrNull { it.suscripcionId == suscripcionId }
        if (poliza != null) {
            abrir(poliza)
        } else if (_uiState.value.fase != FasePolizas.LISTA) {
            // La lista aún no llegó (o falló): se abre cuando llegue. Si ya llegó y la póliza no está, no hay nada que abrir.
            pendienteDeAbrir = suscripcionId
            if (_uiState.value.fase == FasePolizas.ERROR) recargar() else iniciar()
        }
    }

    fun abrir(poliza: PolizaResponse) {
        descargando?.cancel()
        _uiState.update {
            it.copy(seleccionada = poliza, qr = EnlaceQr.NoAplica, documento = null, descarga = EstadoDescarga.INACTIVO, pdf = null)
        }
        if (_uiState.value.estadoDe(poliza).conQr) pedirEnlace(poliza)
    }

    fun cerrarDetalle() {
        enlace?.cancel()
        descargando?.cancel()
        _uiState.update {
            it.copy(seleccionada = null, qr = EnlaceQr.NoAplica, documento = null, descarga = EstadoDescarga.INACTIVO, pdf = null)
        }
    }

    fun reintentarQr() {
        _uiState.value.seleccionada?.let(::pedirEnlace)
    }

    private fun pedirEnlace(poliza: PolizaResponse) {
        enlace?.cancel()
        _uiState.update { it.copy(qr = EnlaceQr.Cargando) }
        enlace = viewModelScope.launch {
            when (val resultado = repository.enlaceCertificado(poliza.suscripcionId, idioma())) {
                is PolizasRepository.Resultado.Exito ->
                    _uiState.update { it.copy(qr = EnlaceQr.Listo(CodigoQr.urlAbsoluta(baseUrl, resultado.valor.ruta))) }

                PolizasRepository.Resultado.SinSesion -> fallar(ErrorPolizas.SESION_VENCIDA)
                else -> _uiState.update { it.copy(qr = EnlaceQr.Fallido) }
            }
        }
    }

    // --- documentos ---------------------------------------------------------------------------------------------------

    /** Baja el PDF; la pantalla le pide al cliente dónde guardarlo cuando está listo (mismo mecanismo que HU22). */
    fun descargar(documento: DocumentoPoliza) {
        val poliza = _uiState.value.seleccionada ?: return
        if (_uiState.value.descarga == EstadoDescarga.DESCARGANDO) return
        _uiState.update { it.copy(documento = documento, descarga = EstadoDescarga.DESCARGANDO, pdf = null) }
        descargando = viewModelScope.launch {
            val resultado = when (documento) {
                DocumentoPoliza.POLIZA_FIRMADA -> repository.polizaFirmada(poliza.suscripcionId)
                DocumentoPoliza.CERTIFICADO -> repository.certificado(poliza.suscripcionId, idioma())
            }
            when (resultado) {
                is PolizasRepository.Resultado.Exito ->
                    _uiState.update { it.copy(descarga = EstadoDescarga.LISTO_PARA_GUARDAR, pdf = ContenidoPdf(resultado.valor)) }

                PolizasRepository.Resultado.SinSesion -> fallar(ErrorPolizas.SESION_VENCIDA)
                PolizasRepository.Resultado.NoDisponible -> _uiState.update { it.copy(descarga = EstadoDescarga.NO_DISPONIBLE) }
                else -> _uiState.update { it.copy(descarga = EstadoDescarga.ERROR) }
            }
        }
    }

    /** El selector del sistema respondió: `true` se guardó, `false` falló al escribir, `null` el cliente canceló. */
    fun descargaGuardada(exito: Boolean?) = _uiState.update {
        it.copy(
            pdf = null,
            descarga = when (exito) {
                true -> EstadoDescarga.GUARDADO
                null -> EstadoDescarga.INACTIVO
                false -> EstadoDescarga.ERROR
            },
        )
    }
}
