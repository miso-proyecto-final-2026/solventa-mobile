package com.solventa.mobile.red

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

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
    @SerialName("fecha_revocacion") val fechaRevocacion: String? = null,
)

interface ConsentimientoApi {
    // 201 la primera vez, 200 si ya estaba otorgado: reintentar es seguro (idempotente).
    @POST("v1/clientes/{clienteId}/consentimientos")
    suspend fun otorgar(
        @Path("clienteId") clienteId: String,
        @Header("Authorization") autorizacion: String,
        @Body solicitud: ConsentimientoRequest,
    ): Response<ConsentimientoResponse>

    // Consentimiento vigente del tipo: OTORGADO, NO_OTORGADO o REVOCADO.
    @GET("v1/clientes/{clienteId}/consentimientos/{tipo}")
    suspend fun consultar(
        @Path("clienteId") clienteId: String,
        @Path("tipo") tipo: String,
        @Header("Authorization") autorizacion: String,
    ): Response<ConsentimientoResponse>

    // HU07. 200 con REVOCADO (también al repetirla: idempotente), 404 si nunca se otorgó, 401/403 sin sesión válida.
    @DELETE("v1/clientes/{clienteId}/consentimientos/{tipo}")
    suspend fun revocar(
        @Path("clienteId") clienteId: String,
        @Path("tipo") tipo: String,
        @Header("Authorization") autorizacion: String,
        @Query("canal") canal: String,
    ): Response<ConsentimientoResponse>
}
