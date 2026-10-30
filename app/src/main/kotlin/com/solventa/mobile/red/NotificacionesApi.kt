package com.solventa.mobile.red

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** El dispositivo y el idioma del teléfono: con ellos el servidor escribe las notificaciones en ese idioma (HU22, criterio 9). */
@Serializable
data class SolicitudDispositivo(
    @SerialName("dispositivo_id") val dispositivoId: String,
    val idioma: String,
)

@Serializable
data class DispositivoResponse(
    @SerialName("dispositivo_id") val dispositivoId: String,
    val idioma: String,
)

/**
 * Una notificación push de la bandeja del servidor. `tituloPublico` y `textoPublico` son lo que se ve con la pantalla de
 * bloqueo: sin datos de la póliza (HU22, criterio 8). Los campos que la app no conoce se ignoran.
 */
@Serializable
data class NotificacionResponse(
    val id: String,
    val tipo: String,
    val titulo: String,
    val texto: String,
    @SerialName("titulo_publico") val tituloPublico: String,
    @SerialName("texto_publico") val textoPublico: String,
    val prioridad: String = "NORMAL",
    val idioma: String = "es-CO",
    @SerialName("suscripcion_id") val suscripcionId: String,
    @SerialName("creada_en") val creadaEn: String,
    val recibida: Boolean = false,
)

interface NotificacionesApi {
    @POST("v1/dispositivos")
    suspend fun registrarDispositivo(
        @Header("Authorization") autorizacion: String,
        @Body solicitud: SolicitudDispositivo,
    ): Response<DispositivoResponse>

    // Por defecto solo las nuevas: las que la app aún no confirmó haber mostrado.
    @GET("v1/notificaciones")
    suspend fun nuevas(
        @Header("Authorization") autorizacion: String,
        @Query("solo_nuevas") soloNuevas: Boolean = true,
    ): Response<List<NotificacionResponse>>

    // 204 sin cuerpo. Confirmarla otra vez no cambia nada.
    @POST("v1/notificaciones/{id}/recibida")
    suspend fun confirmarRecibida(
        @Path("id") id: String,
        @Header("Authorization") autorizacion: String,
    ): Response<Unit>
}
