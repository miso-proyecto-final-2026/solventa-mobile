package com.solventa.mobile.red

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

@Serializable
data class SolicitudCotizacion(
    @SerialName("cliente_id") val clienteId: String,
    val producto: String,
    val datos: Map<String, JsonPrimitive>,
)

@Serializable
data class CoberturaResponse(val tipo: String, val monto: Long, val deducible: Long)

@Serializable
data class CotizacionResponse(
    val id: String,
    val producto: String,
    val moneda: String,
    @SerialName("prima_anual") val primaAnual: Long,
    @SerialName("prima_mensual") val primaMensual: Long,
    val coberturas: List<CoberturaResponse>,
    @SerialName("vigente_hasta") val vigenteHasta: String,
    val degradado: Boolean = false,
    val aproximada: Boolean = false,
    val aviso: String? = null,
)

@Serializable
data class DetalleErrorCotizacion(val campo: String, val motivo: String)

@Serializable
data class CuerpoErrorCotizacion(
    val code: String,
    val message: String = "",
    val details: List<DetalleErrorCotizacion> = emptyList(),
)

/** Cuerpo de los errores de MS Cotización: `{"error": {"code", "message", "details": [{"campo", "motivo"}]}}`. */
@Serializable
data class ErrorCotizacionResponse(val error: CuerpoErrorCotizacion)

@Serializable
data class LatenciaClienteRequest(@SerialName("latencia_cliente_ms") val latenciaClienteMs: Double)

interface CotizacionApi {
    @POST("v1/cotizaciones")
    suspend fun cotizar(@Body solicitud: SolicitudCotizacion): Response<CotizacionResponse>

    @PUT("v1/cotizaciones/{id}/latencia-cliente")
    suspend fun registrarLatencia(
        @Path("id") id: String,
        @Body cuerpo: LatenciaClienteRequest,
    ): Response<Unit>
}
