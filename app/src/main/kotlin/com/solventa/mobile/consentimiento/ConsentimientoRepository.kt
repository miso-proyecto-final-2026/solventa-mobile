package com.solventa.mobile.consentimiento

import com.solventa.mobile.red.ApiClient
import com.solventa.mobile.red.ConsentimientoApi
import com.solventa.mobile.red.ConsentimientoRequest
import com.solventa.mobile.red.ConsentimientoResponse
import com.solventa.mobile.sesion.SesionActual
import java.io.IOException
import retrofit2.Response

/** Versión del texto legal que muestra la app; debe coincidir con los recursos `consentimiento_*_v1`. */
const val VERSION_TEXTO_LEGAL = "v1"
private const val TIPO_OPEN_FINANCE_OPEN_DATA = "OPEN_FINANCE_OPEN_DATA"
private const val CANAL_MOVIL = "MOVIL"

/**
 * Otorga el consentimiento en MS Identidad. El endpoint es idempotente, así que ante un corte de
 * conexión se reintenta una vez sin riesgo de duplicar el consentimiento.
 */
class ConsentimientoRepository(
    private val api: ConsentimientoApi = ApiClient.consentimientoApi,
    private val sesion: SesionActual = SesionActual,
) {

    sealed interface Resultado {
        data class Otorgado(val respuesta: ConsentimientoResponse) : Resultado
        data object SinSesion : Resultado
        data class ErrorRed(val mensaje: String) : Resultado
    }

    suspend fun otorgar(): Resultado {
        val clienteId = sesion.clienteId
        val token = sesion.accessToken
        if (clienteId == null || token == null) return Resultado.SinSesion
        val solicitud = ConsentimientoRequest(
            tipo = TIPO_OPEN_FINANCE_OPEN_DATA,
            versionTexto = VERSION_TEXTO_LEGAL,
            canal = CANAL_MOVIL,
        )
        return intentar(clienteId, "Bearer $token", solicitud, reintentosDisponibles = 1)
    }

    private suspend fun intentar(
        clienteId: String,
        autorizacion: String,
        solicitud: ConsentimientoRequest,
        reintentosDisponibles: Int,
    ): Resultado =
        try {
            interpretar(api.otorgar(clienteId, autorizacion, solicitud))
        } catch (e: IOException) {
            if (reintentosDisponibles > 0) {
                intentar(clienteId, autorizacion, solicitud, reintentosDisponibles - 1)
            } else {
                Resultado.ErrorRed("No hay conexión. Verifica tu red e intenta de nuevo.")
            }
        }

    private fun interpretar(respuesta: Response<ConsentimientoResponse>): Resultado =
        when (respuesta.code()) {
            200, 201 -> respuesta.body()?.let { Resultado.Otorgado(it) }
                ?: Resultado.ErrorRed("Respuesta inesperada del servidor")
            401, 403 -> Resultado.SinSesion
            else -> Resultado.ErrorRed("Ocurrió un error inesperado (${respuesta.code()})")
        }
}
