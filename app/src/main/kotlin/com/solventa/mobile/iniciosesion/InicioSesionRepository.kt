package com.solventa.mobile.iniciosesion

import com.solventa.mobile.red.ApiClient
import com.solventa.mobile.red.ClienteApi
import com.solventa.mobile.red.InicioSesionRequest
import com.solventa.mobile.red.RegistroResponse
import com.solventa.mobile.sesion.SesionActual
import java.io.IOException
import java.io.InterruptedIOException
import retrofit2.Response

/** Si el servidor bloquea sin decir cuánto falta, se asume la ventana completa del límite (15 min). */
const val BLOQUEO_POR_DEFECTO_S = 15 * 60

/**
 * Inicia sesión con correo y contraseña en MS Identidad (HU05): es el respaldo de la biometría y la salida cuando la
 * sesión guardada venció. No se reintenta sola: cada intento fallido cuenta para el límite del servidor, y un corte
 * de conexión no dice si el servidor alcanzó a contarlo.
 */
class InicioSesionRepository(
    private val api: ClienteApi = ApiClient.clienteApi,
    private val sesion: SesionActual = SesionActual,
) {

    sealed interface Resultado {
        data class Exito(val respuesta: RegistroResponse) : Resultado
        data object CredencialesInvalidas : Resultado
        data class Bloqueado(val segundos: Int) : Resultado
        data object TiempoAgotado : Resultado
        data object SinConexion : Resultado
        data class ErrorServicio(val codigo: Int) : Resultado
    }

    suspend fun iniciar(email: String, password: String): Resultado =
        try {
            interpretar(email, api.iniciarSesion(InicioSesionRequest(email, password)))
        } catch (_: InterruptedIOException) {
            Resultado.TiempoAgotado
        } catch (_: IOException) {
            Resultado.SinConexion
        }

    private fun interpretar(email: String, respuesta: Response<RegistroResponse>): Resultado =
        when (val codigo = respuesta.code()) {
            200 -> respuesta.body()?.let {
                sesion.iniciar(it.id, it.accessToken, it.nombre, email)
                Resultado.Exito(it)
            } ?: Resultado.ErrorServicio(codigo)
            401 -> Resultado.CredencialesInvalidas
            429 -> Resultado.Bloqueado(respuesta.headers()["Retry-After"]?.toIntOrNull() ?: BLOQUEO_POR_DEFECTO_S)
            else -> Resultado.ErrorServicio(codigo)
        }
}
