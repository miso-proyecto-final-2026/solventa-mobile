package com.solventa.mobile.biometria

import com.solventa.mobile.red.ActivarBiometriaRequest
import com.solventa.mobile.red.ApiClient
import com.solventa.mobile.red.BiometriaApi
import com.solventa.mobile.red.BiometriaResponse
import com.solventa.mobile.sesion.SesionActual
import java.io.IOException
import java.io.InterruptedIOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.Response

const val CODIGO_KYC_NO_APROBADO = "KYC_NO_APROBADO"
const val CODIGO_CONTRASENA_INCORRECTA = "CONTRASENA_INCORRECTA"

/** Código de negocio que MS Identidad pone en `detail.codigo` de un 403; `null` si el cuerpo no lo trae. */
fun codigoDeError(cuerpo: String?): String? =
    try {
        cuerpo?.let { Json.parseToJsonElement(it).jsonObject["detail"]?.jsonObject?.get("codigo")?.jsonPrimitive?.content }
    } catch (_: Exception) {
        null
    }

/**
 * Registra en MS Identidad qué método biométrico activó el cliente (HU04). Las tres operaciones son idempotentes en
 * el servidor, así que ante un corte de conexión se reintenta una vez; superar el tiempo de espera no se reintenta.
 */
class BiometriaRepository(
    private val api: BiometriaApi = ApiClient.biometriaApi,
    private val sesion: SesionActual = SesionActual,
) {

    sealed interface Resultado {
        data class Exito(val biometria: BiometriaResponse?) : Resultado
        data object SinSesion : Resultado
        data object KycNoAprobado : Resultado
        data object ContrasenaIncorrecta : Resultado
        data object TiempoAgotado : Resultado
        data object SinConexion : Resultado
        data class ErrorServicio(val codigo: Int) : Resultado
    }

    suspend fun consultar(): Resultado = ejecutar { id, auth -> api.consultar(id, auth) }

    suspend fun activar(metodo: MetodoBiometrico, contrasena: String): Resultado =
        ejecutar { id, auth -> api.activar(id, auth, ActivarBiometriaRequest(metodo.codigo, contrasena)) }

    suspend fun desactivar(): Resultado = ejecutar { id, auth -> api.desactivar(id, auth) }

    private suspend fun <T> ejecutar(llamada: suspend (clienteId: String, autorizacion: String) -> Response<T>): Resultado {
        val clienteId = sesion.clienteId
        val token = sesion.accessToken
        if (clienteId == null || token == null) return Resultado.SinSesion
        return intentar(reintentosDisponibles = 1) { llamada(clienteId, "Bearer $token") }
    }

    private suspend fun <T> intentar(reintentosDisponibles: Int, llamada: suspend () -> Response<T>): Resultado =
        try {
            interpretar(llamada())
        } catch (_: InterruptedIOException) {
            Resultado.TiempoAgotado
        } catch (_: IOException) {
            if (reintentosDisponibles > 0) intentar(reintentosDisponibles - 1, llamada) else Resultado.SinConexion
        }

    private fun <T> interpretar(respuesta: Response<T>): Resultado =
        when (val codigo = respuesta.code()) {
            200, 204 -> Resultado.Exito(respuesta.body() as? BiometriaResponse)
            401 -> Resultado.SinSesion
            403 -> when (codigoDeError(respuesta.errorBody()?.string())) {
                CODIGO_KYC_NO_APROBADO -> Resultado.KycNoAprobado
                CODIGO_CONTRASENA_INCORRECTA -> Resultado.ContrasenaIncorrecta
                else -> Resultado.SinSesion
            }
            else -> Resultado.ErrorServicio(codigo)
        }
}
