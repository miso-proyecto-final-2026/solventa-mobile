package com.solventa.mobile.red

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** La billetera del cliente (HU20, HU35): todas sus pólizas, las más recientes primero. */
@Serializable
data class BilleteraResponse(
    @SerialName("cliente_id") val clienteId: String,
    val polizas: List<PolizaResponse> = emptyList(),
)

/** Enlace firmado y con vencimiento para descargar el certificado sin sesión (HU22). `ruta` es relativa al borde. */
@Serializable
data class EnlaceCertificadoResponse(
    val ruta: String,
    @SerialName("vence_en") val venceEn: String,
)

interface PolizasApi {
    // Un `cliente_id` distinto al del token responde 403.
    @GET("v1/polizas")
    suspend fun billetera(
        @Query("cliente_id") clienteId: String,
        @Header("Authorization") autorizacion: String,
    ): Response<BilleteraResponse>

    // El enlace que muestra el código QR de la credencial (HU35). Solo el titular; vence a las 24 h.
    @POST("v1/suscripciones/{id}/certificado/enlace")
    suspend fun enlaceCertificado(
        @Path("id") id: String,
        @Header("Authorization") autorizacion: String,
        @Header("Accept-Language") idioma: String,
    ): Response<EnlaceCertificadoResponse>
}
