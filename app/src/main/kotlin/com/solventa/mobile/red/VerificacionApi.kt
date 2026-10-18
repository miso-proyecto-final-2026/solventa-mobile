package com.solventa.mobile.red

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

@Serializable
data class VerificacionResponse(
    val id: String,
    val estado: String,
    val motivo: String? = null,
    @SerialName("intentos_restantes") val intentosRestantes: Int,
    @SerialName("creado_en") val creadoEn: String,
)

/** Estado vigente de la verificación (HU03); el cliente no calcula el resultado, solo lo muestra. */
@Serializable
data class EstadoKycResponse(
    @SerialName("cliente_id") val clienteId: String,
    @SerialName("estado_kyc") val estadoKyc: String,
    val motivo: String? = null,
    @SerialName("intentos_restantes") val intentosRestantes: Int,
    @SerialName("bloqueado_hasta") val bloqueadoHasta: String? = null,
    @SerialName("ultima_verificacion_id") val ultimaVerificacionId: String? = null,
)

interface VerificacionApi {
    // 201 la primera vez, 200 si es una repetición de la misma Idempotency-Key.
    @Multipart
    @POST("v1/clientes/{clienteId}/verificaciones")
    suspend fun enviar(
        @Path("clienteId") clienteId: String,
        @Header("Authorization") autorizacion: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Part("reto") reto: RequestBody,
        @Part anverso: MultipartBody.Part,
        @Part reverso: MultipartBody.Part,
        @Part selfie: MultipartBody.Part,
    ): Response<VerificacionResponse>

    @GET("v1/clientes/{clienteId}/verificacion")
    suspend fun consultarEstado(
        @Path("clienteId") clienteId: String,
        @Header("Authorization") autorizacion: String,
    ): Response<EstadoKycResponse>
}
