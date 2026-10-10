package com.solventa.mobile.red

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

@Serializable
data class RegistroRequest(
    val nombre: String,
    val apellido: String,
    @SerialName("tipo_documento") val tipoDocumento: String,
    @SerialName("numero_documento") val numeroDocumento: String,
    val email: String,
    val celular: String,
    val password: String,
    @SerialName("acepto_terminos") val aceptoTerminos: Boolean,
)

@Serializable
data class RegistroResponse(
    val id: String,
    val nombre: String,
    val apellido: String,
    val email: String,
    @SerialName("estado_kyc") val estadoKyc: String,
    @SerialName("access_token") val accessToken: String,
    @SerialName("creado_en") val creadoEn: String,
)

@Serializable
data class ErrorResponse(val detail: String)

interface ClienteApi {
    @POST("v1/clientes")
    suspend fun registrar(
        @Body solicitud: RegistroRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): Response<RegistroResponse>
}
