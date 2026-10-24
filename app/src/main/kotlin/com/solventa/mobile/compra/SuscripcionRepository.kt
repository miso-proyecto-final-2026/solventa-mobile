package com.solventa.mobile.compra

import com.solventa.mobile.red.ApiClient
import com.solventa.mobile.red.ErrorSuscripcionResponse
import com.solventa.mobile.red.SolicitudSuscripcion
import com.solventa.mobile.red.SuscripcionApi
import com.solventa.mobile.red.SuscripcionResponse
import com.solventa.mobile.sesion.SesionActual
import java.io.IOException
import java.io.InterruptedIOException
import kotlinx.serialization.json.Json
import retrofit2.Response

/**
 * Inicia y consulta la saga de suscripción (HU15). El envío es idempotente por la clave del intento: ante un
 * corte de conexión se repite una vez con la misma clave y el servidor devuelve la misma saga, sin cobrar dos
 * veces (HU17, criterio 9). Si aun así no se sabe qué pasó, el resultado es [Resultado.ResultadoDesconocido] y
 * el cliente decide: repetir con la misma clave equivale a consultar.
 */
class SuscripcionRepository(
    private val api: SuscripcionApi = ApiClient.suscripcionApi,
    private val sesion: SesionActual = SesionActual,
) {
    private val json = Json { ignoreUnknownKeys = true }

    sealed interface Resultado {
        data class Exito(val saga: SuscripcionResponse) : Resultado
        data object SinSesion : Resultado
        data object OfertaVencida : Resultado
        data object OfertaYaUsada : Resultado
        data object ServicioNoDisponible : Resultado

        /** El envío pudo haber llegado: no se sabe si se creó la saga. */
        data object ResultadoDesconocido : Resultado
        data object SinConexion : Resultado
        data class ErrorServicio(val codigo: Int) : Resultado
    }

    suspend fun iniciar(cotizacionId: String, tokenPago: String, claveIdempotencia: String): Resultado {
        val token = sesion.accessToken ?: return Resultado.SinSesion
        val solicitud = SolicitudSuscripcion(cotizacionId, tokenPago, aceptaTerminos = true)
        return intentar(reintentosDisponibles = 1) { api.iniciar("Bearer $token", claveIdempotencia, solicitud) }
    }

    suspend fun consultar(suscripcionId: String): Resultado {
        val token = sesion.accessToken ?: return Resultado.SinSesion
        return try {
            interpretar(api.consultar(suscripcionId, "Bearer $token"))
        } catch (_: InterruptedIOException) {
            Resultado.SinConexion
        } catch (_: IOException) {
            Resultado.SinConexion
        }
    }

    private suspend fun intentar(
        reintentosDisponibles: Int,
        llamada: suspend () -> Response<SuscripcionResponse>,
    ): Resultado =
        try {
            interpretar(llamada())
        } catch (_: InterruptedIOException) { // incluye SocketTimeoutException: la solicitud pudo llegar
            Resultado.ResultadoDesconocido
        } catch (_: IOException) {
            if (reintentosDisponibles > 0) intentar(reintentosDisponibles - 1, llamada) else Resultado.ResultadoDesconocido
        }

    private fun interpretar(respuesta: Response<SuscripcionResponse>): Resultado {
        val codigo = respuesta.code()
        if (codigo in 200..299) return respuesta.body()?.let { Resultado.Exito(it) } ?: Resultado.ErrorServicio(codigo)
        return when (codigoDeError(respuesta)) {
            "COTIZACION_VENCIDA", "COTIZACION_NO_ENCONTRADA" -> Resultado.OfertaVencida
            "COTIZACION_YA_USADA" -> Resultado.OfertaYaUsada
            "DEPENDENCIA_NO_DISPONIBLE" -> Resultado.ServicioNoDisponible
            else -> if (codigo == 401 || codigo == 403) Resultado.SinSesion else Resultado.ErrorServicio(codigo)
        }
    }

    private fun codigoDeError(respuesta: Response<SuscripcionResponse>): String =
        runCatching { json.decodeFromString<ErrorSuscripcionResponse>(respuesta.errorBody()?.string().orEmpty()).error.code }
            .getOrDefault("")
}
