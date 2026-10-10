package com.solventa.mobile.registro

import com.solventa.mobile.red.ApiClient
import com.solventa.mobile.red.ClienteApi
import com.solventa.mobile.red.ErrorResponse
import com.solventa.mobile.red.RegistroRequest
import com.solventa.mobile.red.RegistroResponse
import java.io.IOException
import java.util.UUID
import kotlinx.serialization.json.Json
import retrofit2.Response

/**
 * Reintenta una sola vez ante un corte de conexión (EOF/reset), reenviando la misma
 * Idempotency-Key para que el backend no cree dos cuentas (lección del idle timeout del NLB, HA01).
 */
class RegistroRepository(private val api: ClienteApi = ApiClient.clienteApi) {

    sealed interface Resultado {
        data class Exito(val respuesta: RegistroResponse) : Resultado
        data class Duplicado(val mensaje: String) : Resultado
        data class ErrorValidacion(val mensaje: String) : Resultado
        data class ErrorRed(val mensaje: String) : Resultado
    }

    suspend fun registrar(solicitud: RegistroRequest): Resultado {
        val idempotencyKey = UUID.randomUUID().toString()
        return intentar(solicitud, idempotencyKey, reintentosDisponibles = 1)
    }

    private suspend fun intentar(
        solicitud: RegistroRequest,
        idempotencyKey: String,
        reintentosDisponibles: Int,
    ): Resultado =
        try {
            interpretar(api.registrar(solicitud, idempotencyKey))
        } catch (e: IOException) {
            if (reintentosDisponibles > 0) {
                intentar(solicitud, idempotencyKey, reintentosDisponibles - 1)
            } else {
                Resultado.ErrorRed("No hay conexión. Verifica tu red e intenta de nuevo.")
            }
        }

    private fun interpretar(respuesta: Response<RegistroResponse>): Resultado =
        when (respuesta.code()) {
            201 -> respuesta.body()?.let { Resultado.Exito(it) }
                ?: Resultado.ErrorRed("Respuesta inesperada del servidor")
            409 -> Resultado.Duplicado(leerDetalle(respuesta) ?: "No fue posible completar el registro")
            422 -> Resultado.ErrorValidacion(leerDetalle(respuesta) ?: "Revisa los datos ingresados")
            else -> Resultado.ErrorRed("Ocurrió un error inesperado (${respuesta.code()})")
        }

    private fun leerDetalle(respuesta: Response<RegistroResponse>): String? {
        val cuerpoError = respuesta.errorBody()?.string() ?: return null
        return runCatching { Json.decodeFromString<ErrorResponse>(cuerpoError).detail }.getOrNull()
    }
}
