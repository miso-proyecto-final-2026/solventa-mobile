package com.solventa.mobile.firma

import com.solventa.mobile.red.ApiClient
import com.solventa.mobile.red.DocumentoFirmaResponse
import com.solventa.mobile.red.ErrorSuscripcionResponse
import com.solventa.mobile.red.FirmaApi
import com.solventa.mobile.red.FirmaResponse
import com.solventa.mobile.red.PolizaResponse
import com.solventa.mobile.red.SolicitudFirma
import com.solventa.mobile.sesion.SesionActual
import java.io.IOException
import java.io.InterruptedIOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import retrofit2.Response

/**
 * Firma de la póliza (HU21): revisar el documento, pedir el código y firmar. Firmar es idempotente en el servidor:
 * ante un corte de conexión se repite una vez y, si aun así no se sabe qué pasó, el resultado es
 * [Resultado.Desconocido] y el cliente consulta el estado antes de volver a pedir el código.
 */
class FirmaRepository(
    private val api: FirmaApi = ApiClient.firmaApi,
    private val sesion: SesionActual = SesionActual,
) {
    private val json = Json { ignoreUnknownKeys = true }

    sealed interface Resultado<out T> {
        data class Exito<T>(val valor: T) : Resultado<T>
        data object SinSesion : Resultado<Nothing>

        /** Cobrada pero el PDF aún no está generado (se genera aparte, en menos de un segundo). */
        data object DocumentoNoListo : Resultado<Nothing>

        /** El certificado solo existe cuando la póliza ya está emitida. */
        data object CertificadoNoDisponible : Resultado<Nothing>

        /** La suscripción ya no está en el paso de firma. */
        data object FirmaNoDisponible : Resultado<Nothing>
        data object Vencida : Resultado<Nothing>

        /** Se agotaron los códigos: el cobro se devuelve. */
        data object Fallida : Resultado<Nothing>
        data object DocumentoAlterado : Resultado<Nothing>

        /** Código incorrecto; [intentosRestantes] puede faltar si el servidor no lo informa. */
        data class CodigoInvalido(val intentosRestantes: Int?) : Resultado<Nothing>

        /** El envío pudo haber llegado: no se sabe si se firmó. */
        data object Desconocido : Resultado<Nothing>
        data object SinConexion : Resultado<Nothing>
        data class ErrorServicio(val codigo: Int) : Resultado<Nothing>
    }

    suspend fun documento(suscripcionId: String): Resultado<DocumentoFirmaResponse> =
        llamar { token -> api.documento(suscripcionId, token) }

    /** El PDF que se va a firmar, para leerlo en la app. */
    suspend fun pdf(suscripcionId: String): Resultado<ByteArray> = descargar { token -> api.pdf(suscripcionId, token) }

    /** El certificado de cobertura en PDF (HU22), en el idioma del teléfono. Solo lo descarga el titular de una póliza emitida. */
    suspend fun certificado(suscripcionId: String, idioma: String): Resultado<ByteArray> =
        descargar { token -> api.certificado(suscripcionId, token, idioma) }

    /** La póliza emitida: producto, vigencia, prima y coberturas, para mostrar el certificado. */
    suspend fun poliza(suscripcionId: String): Resultado<PolizaResponse> = llamar { token -> api.poliza(suscripcionId, token) }

    private suspend fun descargar(llamada: suspend (String) -> Response<ResponseBody>): Resultado<ByteArray> {
        val token = sesion.accessToken ?: return Resultado.SinSesion
        return try {
            val respuesta = llamada("Bearer $token")
            val cuerpo = respuesta.body()
            if (respuesta.isSuccessful && cuerpo != null) {
                Resultado.Exito(withContext(Dispatchers.IO) { cuerpo.bytes() })
            } else {
                interpretarError(respuesta)
            }
        } catch (_: IOException) {
            Resultado.SinConexion
        }
    }

    suspend fun pedirCodigo(suscripcionId: String): Resultado<Unit> =
        llamar { token -> api.pedirCodigo(suscripcionId, token) }

    suspend fun firmar(suscripcionId: String, solicitud: SolicitudFirma): Resultado<FirmaResponse> {
        val token = sesion.accessToken ?: return Resultado.SinSesion
        return intentar(reintentosDisponibles = 1) { api.firmar(suscripcionId, "Bearer $token", solicitud) }
    }

    private suspend fun <T> llamar(llamada: suspend (String) -> Response<T>): Resultado<T> {
        val token = sesion.accessToken ?: return Resultado.SinSesion
        return try {
            interpretar(llamada("Bearer $token"))
        } catch (_: IOException) { // incluye los tiempos de espera: estas llamadas no cambian nada, se pueden repetir
            Resultado.SinConexion
        }
    }

    private suspend fun <T> intentar(reintentosDisponibles: Int, llamada: suspend () -> Response<T>): Resultado<T> =
        try {
            interpretar(llamada())
        } catch (_: InterruptedIOException) { // la solicitud pudo llegar
            Resultado.Desconocido
        } catch (_: IOException) {
            if (reintentosDisponibles > 0) intentar(reintentosDisponibles - 1, llamada) else Resultado.Desconocido
        }

    private fun <T> interpretar(respuesta: Response<T>): Resultado<T> {
        if (respuesta.isSuccessful) {
            @Suppress("UNCHECKED_CAST")
            return Resultado.Exito((respuesta.body() ?: Unit) as T)
        }
        return interpretarError(respuesta)
    }

    private fun interpretarError(respuesta: Response<*>): Resultado<Nothing> {
        val codigo = respuesta.code()
        val error = runCatching {
            json.decodeFromString<ErrorSuscripcionResponse>(respuesta.errorBody()?.string().orEmpty()).error
        }.getOrNull()
        return when (error?.code) {
            "POLIZA_NO_LISTA", "POLIZA_NO_ENCONTRADA" -> Resultado.DocumentoNoListo
            "CERTIFICADO_NO_DISPONIBLE" -> Resultado.CertificadoNoDisponible
            "FIRMA_NO_DISPONIBLE" -> Resultado.FirmaNoDisponible
            "FIRMA_VENCIDA" -> Resultado.Vencida
            "FIRMA_FALLIDA" -> Resultado.Fallida
            "DOCUMENTO_ALTERADO" -> Resultado.DocumentoAlterado
            "OTP_INVALIDO" -> Resultado.CodigoInvalido(restantes(error.details.firstOrNull()?.motivo))
            else -> if (codigo == 401 || codigo == 403) Resultado.SinSesion else Resultado.ErrorServicio(codigo)
        }
    }

    /** El servidor dice «intentos restantes: 2». */
    private fun restantes(motivo: String?): Int? = motivo?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() }
}
