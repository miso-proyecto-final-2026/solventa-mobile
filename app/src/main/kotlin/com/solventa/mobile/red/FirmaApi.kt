package com.solventa.mobile.red

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/** Lo que el cliente revisa antes de firmar (HU21). `estado` es PENDIENTE o FIRMADA; los campos nuevos se ignoran. */
@Serializable
data class DocumentoFirmaResponse(
    @SerialName("suscripcion_id") val suscripcionId: String,
    @SerialName("numero_poliza") val numeroPoliza: String,
    /** Hash del PDF: se devuelve al firmar para probar qué documento vio el cliente. */
    @SerialName("documento_sha256") val documentoSha256: String,
    val estado: String,
    @SerialName("vence_en") val venceEn: String? = null,
    val autenticacion: String = "OTP",
)

/** `canal` es MOVIL o WEB; `codigo` es el código de un solo uso. Sin valores por defecto para que todo viaje. */
@Serializable
data class SolicitudFirma(
    @SerialName("documento_sha256") val documentoSha256: String,
    @SerialName("documento_revisado") val documentoRevisado: Boolean,
    val codigo: String,
    val canal: String,
    val dispositivo: String,
)

@Serializable
data class FirmaResponse(
    @SerialName("suscripcion_id") val suscripcionId: String,
    @SerialName("poliza_id") val polizaId: String,
    @SerialName("estado_saga") val estadoSaga: String,
    @SerialName("documento_sha256") val documentoSha256: String,
    @SerialName("firmada_en") val firmadaEn: String,
    val canal: String,
    @SerialName("metodo_autenticacion") val metodoAutenticacion: String,
)

/** Cobertura de la póliza con su monto y deducible, tal como las entrega la billetera. */
@Serializable
data class CoberturaPolizaResponse(
    val tipo: String,
    val monto: Long,
    val deducible: Long = 0,
)

/** La póliza emitida (HU20, HU22): lo que muestra el certificado en pantalla. Los campos que la app no conoce se ignoran. */
@Serializable
data class PolizaResponse(
    val id: String,
    @SerialName("suscripcion_id") val suscripcionId: String,
    @SerialName("numero_poliza") val numeroPoliza: String,
    val producto: String,
    val estado: String,
    @SerialName("vigencia_desde") val vigenciaDesde: String,
    @SerialName("fecha_vencimiento") val fechaVencimiento: String,
    @SerialName("fecha_emision") val fechaEmision: String? = null,
    val moneda: String = "COP",
    @SerialName("prima_anual") val primaAnual: Long = 0,
    @SerialName("prima_mensual") val primaMensual: Long = 0,
    @SerialName("periodicidad_pago") val periodicidadPago: String = "ANUAL",
    val coberturas: List<CoberturaPolizaResponse> = emptyList(),
)

interface FirmaApi {
    @GET("v1/suscripciones/{id}/documento-firma")
    suspend fun documento(
        @Path("id") id: String,
        @Header("Authorization") autorizacion: String,
    ): Response<DocumentoFirmaResponse>

    @GET("v1/suscripciones/{id}/documento-firma/pdf")
    suspend fun pdf(
        @Path("id") id: String,
        @Header("Authorization") autorizacion: String,
    ): Response<ResponseBody>

    // 202 sin cuerpo: el código sale por el proveedor de firma (en esta etapa, simulado).
    @POST("v1/suscripciones/{id}/firma/codigo")
    suspend fun pedirCodigo(
        @Path("id") id: String,
        @Header("Authorization") autorizacion: String,
    ): Response<Unit>

    // 200 al firmar; 200 con `Idempotent-Replay: true` si ya estaba firmada con el mismo documento (repetir es seguro).
    @POST("v1/suscripciones/{id}/firma")
    suspend fun firmar(
        @Path("id") id: String,
        @Header("Authorization") autorizacion: String,
        @Body solicitud: SolicitudFirma,
    ): Response<FirmaResponse>

    // La póliza de la suscripción (HU20): producto, vigencia, prima y coberturas para el certificado en pantalla.
    @GET("v1/suscripciones/{id}/poliza")
    suspend fun poliza(
        @Path("id") id: String,
        @Header("Authorization") autorizacion: String,
    ): Response<PolizaResponse>

    // El PDF del certificado de cobertura (HU22), en el idioma de `Accept-Language`. Solo lo descarga el titular.
    @GET("v1/suscripciones/{id}/certificado")
    suspend fun certificado(
        @Path("id") id: String,
        @Header("Authorization") autorizacion: String,
        @Header("Accept-Language") idioma: String,
    ): Response<ResponseBody>
}
