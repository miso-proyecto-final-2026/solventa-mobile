package com.solventa.mobile.red

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

@Serializable
data class ConsentimientoRequest(
    val tipo: String,
    @SerialName("version_texto") val versionTexto: String,
    val canal: String,
)

@Serializable
data class ConsentimientoResponse(
    @SerialName("cliente_id") val clienteId: String,
    val tipo: String,
    val estado: String,
    @SerialName("version_texto") val versionTexto: String? = null,
    @SerialName("fecha_otorgamiento") val fechaOtorgamiento: String? = null,
)

interface ConsentimientoApi {
    // 201 la primera vez, 200 si ya estaba otorgado: reintentar es seguro (idempotente).
    @POST("v1/clientes/{clienteId}/consentimientos")
    suspend fun otorgar(
        @Path("clienteId") clienteId: String,
        @Header("Authorization") autorizacion: String,
        @Body solicitud: ConsentimientoRequest,
    ): Response<ConsentimientoResponse>
}
