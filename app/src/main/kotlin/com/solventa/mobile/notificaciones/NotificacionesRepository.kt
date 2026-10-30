package com.solventa.mobile.notificaciones

import com.solventa.mobile.red.ApiClient
import com.solventa.mobile.red.NotificacionResponse
import com.solventa.mobile.red.NotificacionesApi
import com.solventa.mobile.red.SolicitudDispositivo
import com.solventa.mobile.sesion.SesionActual
import java.io.IOException
import retrofit2.Response

/**
 * Registro del dispositivo y bandeja del push (HU22). Ninguna de estas llamadas es crítica: si fallan, se reintentan en el
 * siguiente sondeo y el cliente no ve ningún error.
 */
class NotificacionesRepository(
    private val api: NotificacionesApi = ApiClient.notificacionesApi,
    private val sesion: SesionActual = SesionActual,
) {
    sealed interface Resultado<out T> {
        data class Exito<T>(val valor: T) : Resultado<T>
        data object SinSesion : Resultado<Nothing>
        data object SinConexion : Resultado<Nothing>
        data class Error(val codigo: Int) : Resultado<Nothing>
    }

    /** Idempotente: registrar otra vez el mismo dispositivo solo actualiza el idioma. */
    suspend fun registrarDispositivo(dispositivoId: String, idioma: String): Resultado<Unit> =
        llamar { token -> api.registrarDispositivo(token, SolicitudDispositivo(dispositivoId, idioma)) }.mapear { }

    /** Las notificaciones que la app aún no confirmó haber mostrado. */
    suspend fun nuevas(): Resultado<List<NotificacionResponse>> = llamar { token -> api.nuevas(token) }

    suspend fun confirmarRecibida(notificacionId: String): Resultado<Unit> =
        llamar { token -> api.confirmarRecibida(notificacionId, token) }

    private suspend fun <T> llamar(llamada: suspend (String) -> Response<T>): Resultado<T> {
        val token = sesion.accessToken ?: return Resultado.SinSesion
        return try {
            val respuesta = llamada("Bearer $token")
            when {
                respuesta.isSuccessful -> {
                    @Suppress("UNCHECKED_CAST")
                    Resultado.Exito((respuesta.body() ?: Unit) as T)
                }
                respuesta.code() == 401 || respuesta.code() == 403 -> Resultado.SinSesion
                else -> Resultado.Error(respuesta.code())
            }
        } catch (_: IOException) {
            Resultado.SinConexion
        }
    }

    private inline fun <T, R> Resultado<T>.mapear(transformar: (T) -> R): Resultado<R> = when (this) {
        is Resultado.Exito -> Resultado.Exito(transformar(valor))
        Resultado.SinSesion -> Resultado.SinSesion
        Resultado.SinConexion -> Resultado.SinConexion
        is Resultado.Error -> this
    }
}
