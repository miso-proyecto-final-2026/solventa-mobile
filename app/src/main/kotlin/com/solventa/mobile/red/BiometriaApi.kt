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

/** Lo único que guarda el servidor del acceso biométrico: método, fechas y si sigue activo (HU04, criterio 6). */
@Serializable
data class BiometriaResponse(
    @SerialName("cliente_id") val clienteId: String,
    val activo: Boolean,
    val metodo: String? = null,
    @SerialName("activado_en") val activadoEn: String? = null,
    @SerialName("desactivado_en") val desactivadoEn: String? = null,
)

@Serializable
data class ActivarBiometriaRequest(val metodo: String, val contrasena: String)

interface BiometriaApi {
    @GET("v1/clientes/{clienteId}/biometria")
    suspend fun consultar(
        @Path("clienteId") clienteId: String,
        @Header("Authorization") autorizacion: String,
    ): Response<BiometriaResponse>

    @POST("v1/clientes/{clienteId}/biometria/activar")
    suspend fun activar(
        @Path("clienteId") clienteId: String,
        @Header("Authorization") autorizacion: String,
        @Body solicitud: ActivarBiometriaRequest,
    ): Response<BiometriaResponse>

    @DELETE("v1/clientes/{clienteId}/biometria")
    suspend fun desactivar(
        @Path("clienteId") clienteId: String,
        @Header("Authorization") autorizacion: String,
    ): Response<Unit>
}
