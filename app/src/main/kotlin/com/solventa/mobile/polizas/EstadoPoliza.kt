package com.solventa.mobile.polizas

import com.solventa.mobile.red.PolizaResponse
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

/** Cómo ve el cliente cada póliza de «Mis pólizas» (HU35, criterios 1 y 4). */
enum class EstadoPoliza {
    VIGENTE,

    /** Vigente, pero vence dentro de [EstadosPoliza.DIAS_PROXIMA_A_VENCER] días. */
    PROXIMA_A_VENCER,
    VENCIDA,

    /** Cobrada, pero el cliente aún no la firmó (HU21): se puede retomar la firma. */
    PENDIENTE_FIRMA,

    /** La firma no se completó y el cobro se devolvió. */
    ANULADA,
    ;

    /** Va en la pestaña «Activas»; el resto, en «Vencidas». */
    val activa: Boolean get() = this == VIGENTE || this == PROXIMA_A_VENCER || this == PENDIENTE_FIRMA

    /** Hay un certificado y una póliza firmada que descargar, y un enlace para el QR. */
    val tieneDocumentos: Boolean get() = this == VIGENTE || this == PROXIMA_A_VENCER || this == VENCIDA

    /** La credencial muestra el QR solo mientras la póliza cubre al cliente. */
    val conQr: Boolean get() = this == VIGENTE || this == PROXIMA_A_VENCER
}

object EstadosPoliza {
    /** Primer aviso que menciona la guía (30 y 7 días); el umbral de la tarjeta es el primero. */
    const val DIAS_PROXIMA_A_VENCER = 30L

    /**
     * `PENDIENTE_FIRMA` y `ANULADA` salen del estado que informa el servidor; el resto, de la fecha de vencimiento. Una
     * fecha que no se puede leer deja la póliza como vigente: es mejor que esconderla.
     */
    fun de(poliza: PolizaResponse, ahora: Instant = Instant.now()): EstadoPoliza = when (poliza.estado) {
        "PENDIENTE_FIRMA" -> EstadoPoliza.PENDIENTE_FIRMA
        "ANULADA" -> EstadoPoliza.ANULADA
        else -> {
            val vence = runCatching { OffsetDateTime.parse(poliza.fechaVencimiento).toInstant() }.getOrNull()
            when {
                vence == null -> EstadoPoliza.VIGENTE
                !vence.isAfter(ahora) -> EstadoPoliza.VENCIDA
                Duration.between(ahora, vence) <= Duration.ofDays(DIAS_PROXIMA_A_VENCER) -> EstadoPoliza.PROXIMA_A_VENCER
                else -> EstadoPoliza.VIGENTE
            }
        }
    }
}
