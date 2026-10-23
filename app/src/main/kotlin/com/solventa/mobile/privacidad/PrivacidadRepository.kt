package com.solventa.mobile.privacidad

import com.solventa.mobile.red.ApiClient
import com.solventa.mobile.red.ConsentimientoApi
import com.solventa.mobile.red.ConsentimientoResponse
import com.solventa.mobile.sesion.SesionActual
import java.io.IOException
import java.io.InterruptedIOException
import retrofit2.Response

const val TIPO_CONSENTIMIENTO = "OPEN_FINANCE_OPEN_DATA"
const val ESTADO_OTORGADO = "OTORGADO"
const val ESTADO_REVOCADO = "REVOCADO"
const val ESTADO_NO_OTORGADO = "NO_OTORGADO"
private const val CANAL_MOVIL = "MOVIL"

/**
 * Consulta y revoca el consentimiento en MS Identidad (HU07). Revocar es idempotente: repetirla responde el
 * estado vigente, así que ante un corte de conexión se reintenta una vez sin riesgo. Superar el tiempo de
 * espera no se reintenta solo: el usuario decide. Si no hay confirmación del servidor, el resultado es una
 * falla y la pantalla no debe mostrar el consentimiento como revocado.
 */
class PrivacidadRepository(
    private val api: ConsentimientoApi = ApiClient.consentimientoApi,
    private val sesion: SesionActual = SesionActual,
) {

    sealed interface Resultado {
        data class Exito(val consentimiento: ConsentimientoResponse) : Resultado
        data object SinSesion : Resultado
        data object TiempoAgotado : Resultado
        data object SinConexion : Resultado
        data class ErrorServicio(val codigo: Int) : Resultado
    }

    suspend fun consultar(): Resultado =
        ejecutar(noEncontradoEsExito = false) { clienteId, autorizacion ->
            api.consultar(clienteId, TIPO_CONSENTIMIENTO, autorizacion)
        }

    suspend fun revocar(): Resultado =
        ejecutar(noEncontradoEsExito = true) { clienteId, autorizacion ->
            api.revocar(clienteId, TIPO_CONSENTIMIENTO, autorizacion, CANAL_MOVIL)
        }

    private suspend fun ejecutar(
        noEncontradoEsExito: Boolean,
        llamada: suspend (clienteId: String, autorizacion: String) -> Response<ConsentimientoResponse>,
    ): Resultado {
        val clienteId = sesion.clienteId
        val token = sesion.accessToken
        if (clienteId == null || token == null) return Resultado.SinSesion
        return intentar(clienteId, noEncontradoEsExito, reintentosDisponibles = 1) { llamada(clienteId, "Bearer $token") }
    }

    private suspend fun intentar(
        clienteId: String,
        noEncontradoEsExito: Boolean,
        reintentosDisponibles: Int,
        llamada: suspend () -> Response<ConsentimientoResponse>,
    ): Resultado =
        try {
            interpretar(clienteId, noEncontradoEsExito, llamada())
        } catch (_: InterruptedIOException) { // incluye SocketTimeoutException y el límite de la llamada
            Resultado.TiempoAgotado
        } catch (_: IOException) {
            if (reintentosDisponibles > 0) {
                intentar(clienteId, noEncontradoEsExito, reintentosDisponibles - 1, llamada)
            } else {
                Resultado.SinConexion
            }
        }

    private fun interpretar(
        clienteId: String,
        noEncontradoEsExito: Boolean,
        respuesta: Response<ConsentimientoResponse>,
    ): Resultado =
        when (val codigo = respuesta.code()) {
            200, 201 -> respuesta.body()?.let { Resultado.Exito(it) } ?: Resultado.ErrorServicio(codigo)
            // Revocar algo que nunca se otorgó: el cliente ya no comparte nada, que es lo que quería.
            404 -> if (noEncontradoEsExito) {
                Resultado.Exito(
                    ConsentimientoResponse(clienteId = clienteId, tipo = TIPO_CONSENTIMIENTO, estado = ESTADO_NO_OTORGADO),
                )
            } else {
                Resultado.ErrorServicio(codigo)
            }
            401, 403 -> Resultado.SinSesion
            else -> Resultado.ErrorServicio(codigo)
        }
}
