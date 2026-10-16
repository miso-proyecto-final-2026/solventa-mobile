package com.solventa.mobile.verificacion

import com.solventa.mobile.red.ApiClient
import com.solventa.mobile.red.VerificacionApi
import com.solventa.mobile.red.VerificacionResponse
import com.solventa.mobile.sesion.SesionActual
import java.io.File
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response

/** Reto de prueba de vida que acepta el proveedor simulado (HU02): girar a la izquierda. */
const val RETO_VIDA = "GIRAR_IZQUIERDA"

/** Errores de envío sin texto: la pantalla los traduce a recursos por idioma. */
enum class ErrorEnvio { SIN_CONEXION, SIN_SESION, IMAGEN_INVALIDA, BLOQUEADO, YA_PROCESADA, INESPERADO }

/**
 * Envía la verificación a MS Identidad. Ante un corte de conexión reintenta una vez con la misma
 * Idempotency-Key, así el backend no abre dos casos en el proveedor (criterio 8).
 */
class VerificacionRepository(
    private val api: VerificacionApi = ApiClient.verificacionApi,
    private val sesion: SesionActual = SesionActual,
) {

    sealed interface Resultado {
        data class Exito(val respuesta: VerificacionResponse) : Resultado
        data class Fallo(val error: ErrorEnvio, val reintentarEnSegundos: Long? = null) : Resultado
    }

    suspend fun enviar(
        idempotencyKey: String,
        anverso: String,
        reverso: String,
        selfie: String,
    ): Resultado {
        val clienteId = sesion.clienteId
        val token = sesion.accessToken
        if (clienteId == null || token == null) return Resultado.Fallo(ErrorEnvio.SIN_SESION)
        return intentar(clienteId, "Bearer $token", idempotencyKey, anverso, reverso, selfie, reintentos = 1)
    }

    private suspend fun intentar(
        clienteId: String,
        autorizacion: String,
        idempotencyKey: String,
        anverso: String,
        reverso: String,
        selfie: String,
        reintentos: Int,
    ): Resultado =
        try {
            interpretar(
                api.enviar(
                    clienteId = clienteId,
                    autorizacion = autorizacion,
                    idempotencyKey = idempotencyKey,
                    reto = RETO_VIDA.toRequestBody("text/plain".toMediaType()),
                    anverso = parte("documento_anverso", anverso),
                    reverso = parte("documento_reverso", reverso),
                    selfie = parte("selfie", selfie),
                ),
            )
        } catch (e: IOException) {
            if (reintentos > 0) {
                intentar(clienteId, autorizacion, idempotencyKey, anverso, reverso, selfie, reintentos - 1)
            } else {
                Resultado.Fallo(ErrorEnvio.SIN_CONEXION)
            }
        }

    private fun parte(nombre: String, ruta: String): MultipartBody.Part =
        MultipartBody.Part.createFormData(
            nombre,
            "$nombre.jpg",
            File(ruta).asRequestBody("image/jpeg".toMediaType()),
        )

    private fun interpretar(respuesta: Response<VerificacionResponse>): Resultado =
        when (respuesta.code()) {
            200, 201 -> respuesta.body()?.let { Resultado.Exito(it) }
                ?: Resultado.Fallo(ErrorEnvio.INESPERADO)
            401, 403 -> Resultado.Fallo(ErrorEnvio.SIN_SESION)
            409 -> Resultado.Fallo(ErrorEnvio.YA_PROCESADA)
            413, 415, 422 -> Resultado.Fallo(ErrorEnvio.IMAGEN_INVALIDA)
            429 -> Resultado.Fallo(
                ErrorEnvio.BLOQUEADO,
                respuesta.headers()["Retry-After"]?.toLongOrNull(),
            )
            else -> Resultado.Fallo(ErrorEnvio.INESPERADO)
        }
}
