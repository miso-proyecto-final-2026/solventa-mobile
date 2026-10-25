package com.solventa.mobile.red

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Nunca el número de tarjeta: `metodoPago` es el token de un solo uso de la pasarela (HU17, criterio 3).
 * `periodicidadPago` es ANUAL o MENSUAL (HU18 cobra el primer período); sin valor por defecto para que viaje siempre.
 */
@Serializable
data class SolicitudSuscripcion(
    @SerialName("cotizacion_id") val cotizacionId: String,
    @SerialName("metodo_pago") val metodoPago: String,
    @SerialName("acepta_terminos") val aceptaTerminos: Boolean,
    @SerialName("periodicidad_pago") val periodicidadPago: String,
)

@Serializable
data class DecisionResponse(
    val decision: String,
    val motivos: List<String> = emptyList(),
)

/**
 * Resultado del cobro según MS Pagos (HU18). Un rechazo no cambia el estado de la saga: sigue `APROBADA` en el paso
 * `COBRO` y aquí vienen el motivo y la fecha límite del período de gracia. Nunca incluye el token ni la tarjeta.
 */
@Serializable
data class CobroResponse(
    val estado: String,
    @SerialName("transaccion_id") val transaccionId: String? = null,
    @SerialName("referencia_pasarela") val referenciaPasarela: String? = null,
    val codigo: String? = null,
    val motivo: String? = null,
    @SerialName("gracia_hasta") val graciaHasta: String? = null,
    @SerialName("resuelto_en") val resueltoEn: String? = null,
)

/** Estado de la saga de MS Suscripción (HU15). Los campos que la app no conoce se ignoran. */
@Serializable
data class SuscripcionResponse(
    val id: String,
    @SerialName("cotizacion_id") val cotizacionId: String,
    @SerialName("estado_saga") val estadoSaga: String,
    @SerialName("paso_actual") val pasoActual: String,
    val pasos: List<String> = emptyList(),
    val moneda: String = "COP",
    val decision: DecisionResponse? = null,
    val cobro: CobroResponse? = null,
    @SerialName("revision_asistida") val revisionAsistida: Boolean = false,
)

@Serializable
data class DetalleErrorSuscripcion(val campo: String = "", val motivo: String = "")

@Serializable
data class CuerpoErrorSuscripcion(val code: String = "", val message: String = "", val details: List<DetalleErrorSuscripcion> = emptyList())

/** Cuerpo de error estándar: `{"error": {"code", "message", "details"}}`. */
@Serializable
data class ErrorSuscripcionResponse(val error: CuerpoErrorSuscripcion)

interface SuscripcionApi {
    // 202 al crear; 200 con `Idempotent-Replay: true` si la clave ya se usó con la misma cotización (reintentar es seguro).
    @POST("v1/suscripciones")
    suspend fun iniciar(
        @Header("Authorization") autorizacion: String,
        @Header("Idempotency-Key") claveIdempotencia: String,
        @Body solicitud: SolicitudSuscripcion,
    ): Response<SuscripcionResponse>

    @GET("v1/suscripciones/{id}")
    suspend fun consultar(
        @Path("id") id: String,
        @Header("Authorization") autorizacion: String,
    ): Response<SuscripcionResponse>
}
