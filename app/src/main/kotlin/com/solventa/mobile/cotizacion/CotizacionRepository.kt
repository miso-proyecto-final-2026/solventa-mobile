package com.solventa.mobile.cotizacion

import com.solventa.mobile.red.ApiClient
import com.solventa.mobile.red.CotizacionApi
import com.solventa.mobile.red.CotizacionResponse
import com.solventa.mobile.red.ErrorCotizacionResponse
import com.solventa.mobile.red.LatenciaClienteRequest
import com.solventa.mobile.red.SolicitudCotizacion
import com.solventa.mobile.sesion.SesionActual
import java.io.IOException
import java.io.InterruptedIOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.Response

/**
 * Cotiza en MS Cotización (HU37). Ante un corte de conexión (EOF o reset, como el idle timeout del NLB que
 * destapó HA01) reintenta una sola vez sin que el usuario lo note. Superar el tiempo de espera no se
 * reintenta solo: el usuario decide, y no pierde lo que ingresó.
 */
class CotizacionRepository(
    private val api: CotizacionApi = ApiClient.cotizacionApi,
    private val sesion: SesionActual = SesionActual,
) {
    private val json = Json { ignoreUnknownKeys = true }

    sealed interface Resultado {
        data class Exito(val cotizacion: CotizacionResponse) : Resultado

        /** 422: motivo del servidor por campo (sin el prefijo `datos.`). */
        data class Validacion(val errores: Map<String, String>) : Resultado
        data object SinSesion : Resultado
        data object TiempoAgotado : Resultado
        data object SinConexion : Resultado
        data class ErrorServicio(val codigo: Int) : Resultado
    }

    suspend fun cotizar(producto: String, datos: Map<String, JsonPrimitive>): Resultado {
        val clienteId = sesion.clienteId ?: return Resultado.SinSesion
        return intentar(SolicitudCotizacion(clienteId, producto, datos), reintentosDisponibles = 1)
    }

    /** El tiempo observado por el cliente es una medida de apoyo: si no se puede registrar, no se hace nada. */
    suspend fun registrarLatencia(cotizacionId: String, milisegundos: Double) {
        try {
            api.registrarLatencia(cotizacionId, LatenciaClienteRequest(milisegundos))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best-effort.
        }
    }

    private suspend fun intentar(solicitud: SolicitudCotizacion, reintentosDisponibles: Int): Resultado =
        try {
            interpretar(api.cotizar(solicitud))
        } catch (_: InterruptedIOException) { // incluye SocketTimeoutException y el límite de la llamada
            Resultado.TiempoAgotado
        } catch (_: IOException) {
            if (reintentosDisponibles > 0) intentar(solicitud, reintentosDisponibles - 1) else Resultado.SinConexion
        }

    private fun interpretar(respuesta: Response<CotizacionResponse>): Resultado =
        when (val codigo = respuesta.code()) {
            200, 201 -> respuesta.body()?.let { Resultado.Exito(it) } ?: Resultado.ErrorServicio(codigo)
            422 -> Resultado.Validacion(erroresPorCampo(respuesta))
            401, 403 -> Resultado.SinSesion
            else -> Resultado.ErrorServicio(codigo)
        }

    private fun erroresPorCampo(respuesta: Response<CotizacionResponse>): Map<String, String> =
        runCatching {
            val cuerpo = respuesta.errorBody()?.string().orEmpty()
            json.decodeFromString<ErrorCotizacionResponse>(cuerpo).error.details
                .associate { it.campo.removePrefix("datos.") to it.motivo }
        }.getOrDefault(emptyMap())
}
