package com.solventa.mobile.polizas

import com.solventa.mobile.red.ApiClient
import com.solventa.mobile.red.EnlaceCertificadoResponse
import com.solventa.mobile.red.FirmaApi
import com.solventa.mobile.red.PolizaResponse
import com.solventa.mobile.red.PolizasApi
import com.solventa.mobile.sesion.SesionActual
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Response

/**
 * «Mis pólizas» (HU35): la billetera del cliente, el enlace del QR y las descargas. Todas son lecturas o emisiones de un
 * enlace que vence, así que repetirlas es seguro: un corte de conexión se informa como [Resultado.SinConexion] y la
 * pantalla ofrece reintentar.
 */
class PolizasRepository(
    private val api: PolizasApi = ApiClient.polizasApi,
    private val firmaApi: FirmaApi = ApiClient.firmaApi,
    private val sesion: SesionActual = SesionActual,
) {
    sealed interface Resultado<out T> {
        data class Exito<T>(val valor: T) : Resultado<T>
        data object SinSesion : Resultado<Nothing>

        /** 404: la póliza ya no existe, o aún no tiene ese documento. */
        data object NoDisponible : Resultado<Nothing>
        data object SinConexion : Resultado<Nothing>
        data class ErrorServicio(val codigo: Int) : Resultado<Nothing>
    }

    suspend fun billetera(): Resultado<List<PolizaResponse>> {
        val cliente = sesion.clienteId ?: return Resultado.SinSesion
        return llamar { token -> api.billetera(cliente, token) }.mapear { it.polizas }
    }

    suspend fun enlaceCertificado(suscripcionId: String, idioma: String): Resultado<EnlaceCertificadoResponse> =
        llamar { token -> api.enlaceCertificado(suscripcionId, token, idioma) }

    /** La póliza firmada en PDF (HU21). */
    suspend fun polizaFirmada(suscripcionId: String): Resultado<ByteArray> =
        descargar { token -> firmaApi.polizaFirmada(suscripcionId, token) }

    /** El certificado de cobertura en PDF (HU22), en el idioma del teléfono. */
    suspend fun certificado(suscripcionId: String, idioma: String): Resultado<ByteArray> =
        descargar { token -> firmaApi.certificado(suscripcionId, token, idioma) }

    private suspend fun <T> llamar(llamada: suspend (String) -> Response<T>): Resultado<T> {
        val token = sesion.accessToken ?: return Resultado.SinSesion
        return try {
            val respuesta = llamada("Bearer $token")
            val cuerpo = respuesta.body()
            if (respuesta.isSuccessful && cuerpo != null) Resultado.Exito(cuerpo) else interpretarError(respuesta.code())
        } catch (_: IOException) { // incluye los tiempos de espera
            Resultado.SinConexion
        }
    }

    private suspend fun descargar(llamada: suspend (String) -> Response<ResponseBody>): Resultado<ByteArray> {
        val token = sesion.accessToken ?: return Resultado.SinSesion
        return try {
            val respuesta = llamada("Bearer $token")
            val cuerpo = respuesta.body()
            if (respuesta.isSuccessful && cuerpo != null) {
                Resultado.Exito(withContext(Dispatchers.IO) { cuerpo.bytes() })
            } else {
                interpretarError(respuesta.code())
            }
        } catch (_: IOException) {
            Resultado.SinConexion
        }
    }

    private fun interpretarError(codigo: Int): Resultado<Nothing> = when (codigo) {
        401, 403 -> Resultado.SinSesion
        404 -> Resultado.NoDisponible
        else -> Resultado.ErrorServicio(codigo)
    }

    private inline fun <T, R> Resultado<T>.mapear(transformar: (T) -> R): Resultado<R> = when (this) {
        is Resultado.Exito -> Resultado.Exito(transformar(valor))
        Resultado.SinSesion -> Resultado.SinSesion
        Resultado.NoDisponible -> Resultado.NoDisponible
        Resultado.SinConexion -> Resultado.SinConexion
        is Resultado.ErrorServicio -> this
    }
}
