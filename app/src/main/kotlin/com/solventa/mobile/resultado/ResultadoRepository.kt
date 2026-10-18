package com.solventa.mobile.resultado

import com.solventa.mobile.red.ApiClient
import com.solventa.mobile.red.ConsentimientoApi
import com.solventa.mobile.red.EstadoKycResponse
import com.solventa.mobile.red.VerificacionApi
import com.solventa.mobile.sesion.SesionActual
import java.io.IOException
import retrofit2.Response

private const val TIPO_OPEN_FINANCE_OPEN_DATA = "OPEN_FINANCE_OPEN_DATA"

/** Errores de consulta sin texto: la pantalla los traduce a recursos por idioma. */
enum class ErrorConsulta { SIN_CONEXION, INESPERADO }

/**
 * Consulta en MS Identidad el estado de la verificación y, para decidir el paso siguiente, el del
 * consentimiento. Son lecturas: ante un corte de conexión se reintenta una vez sin riesgo.
 */
class ResultadoRepository(
    private val verificacionApi: VerificacionApi = ApiClient.verificacionApi,
    private val consentimientoApi: ConsentimientoApi = ApiClient.consentimientoApi,
    private val sesion: SesionActual = SesionActual,
) {

    sealed interface Consulta<out T> {
        data class Exito<T>(val valor: T) : Consulta<T>

        /** Token inválido o cliente inexistente: la sesión guardada ya no sirve. */
        data object SinSesion : Consulta<Nothing>
        data class Fallo(val error: ErrorConsulta) : Consulta<Nothing>
    }

    suspend fun estado(): Consulta<EstadoKycResponse> =
        consultar { clienteId, autorizacion -> verificacionApi.consultarEstado(clienteId, autorizacion) }

    suspend fun consentimientoOtorgado(): Consulta<Boolean> =
        when (
            val consulta = consultar { clienteId, autorizacion ->
                consentimientoApi.consultar(clienteId, TIPO_OPEN_FINANCE_OPEN_DATA, autorizacion)
            }
        ) {
            is Consulta.Exito -> Consulta.Exito(consulta.valor.estado == "OTORGADO")
            is Consulta.SinSesion -> consulta
            is Consulta.Fallo -> consulta
        }

    private suspend fun <T> consultar(
        llamada: suspend (clienteId: String, autorizacion: String) -> Response<T>,
    ): Consulta<T> {
        val clienteId = sesion.clienteId
        val token = sesion.accessToken
        if (clienteId == null || token == null) return Consulta.SinSesion
        return intentar(reintentos = 1) { llamada(clienteId, "Bearer $token") }
    }

    private suspend fun <T> intentar(reintentos: Int, llamada: suspend () -> Response<T>): Consulta<T> =
        try {
            interpretar(llamada())
        } catch (e: IOException) {
            if (reintentos > 0) intentar(reintentos - 1, llamada) else Consulta.Fallo(ErrorConsulta.SIN_CONEXION)
        }

    private fun <T> interpretar(respuesta: Response<T>): Consulta<T> =
        when (respuesta.code()) {
            200 -> respuesta.body()?.let { Consulta.Exito(it) } ?: Consulta.Fallo(ErrorConsulta.INESPERADO)
            401, 403, 404 -> Consulta.SinSesion
            else -> Consulta.Fallo(ErrorConsulta.INESPERADO)
        }
}
