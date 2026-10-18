package com.solventa.mobile.resultado

import com.solventa.mobile.red.EstadoKycResponse
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

/** Lo que comunica la pantalla de resultado (HU03). Se deriva solo de la respuesta de MS Identidad. */
sealed interface PantallaResultado {
    /** Criterio 1. */
    data object Aprobada : PantallaResultado

    /** Criterio 2. `motivo` es el código genérico de HU02, nunca el criterio interno del proveedor. */
    data class RechazadaConReintentos(val intentosRestantes: Int, val motivo: String?) : PantallaResultado

    /**
     * Criterio 3, ajustado al backend: tras 3 rechazos MS Identidad bloquea durante una ventana de
     * 30 min, así que se informa desde cuándo podrá intentarlo de nuevo (si el servidor lo indica).
     */
    data class RechazadaSinReintentos(val disponibleDesde: Instant?) : PantallaResultado

    /** Criterio 4. */
    data object EnRevision : PantallaResultado

    /** Aún no hay verificación: no hay resultado que mostrar y el usuario debe hacerla. */
    data object Pendiente : PantallaResultado
}

/** Un solo llamado a la acción por pantalla. */
enum class AccionResultado { CONTINUAR, REINTENTAR, ENTENDIDO }

val PantallaResultado.accion: AccionResultado?
    get() = when (this) {
        PantallaResultado.Aprobada -> AccionResultado.CONTINUAR
        is PantallaResultado.RechazadaConReintentos -> AccionResultado.REINTENTAR
        is PantallaResultado.RechazadaSinReintentos, PantallaResultado.EnRevision -> AccionResultado.ENTENDIDO
        PantallaResultado.Pendiente -> null
    }

/** `null` si el servidor envía un estado que la app no conoce. */
fun mapearResultado(respuesta: EstadoKycResponse): PantallaResultado? =
    when (respuesta.estadoKyc) {
        "VERIFICADO" -> PantallaResultado.Aprobada
        "EN_REVISION" -> PantallaResultado.EnRevision
        "RECHAZADO", "PENDIENTE" -> when {
            // Sin intentos en la ventana no se ofrece reintento, sea cual sea el último estado.
            respuesta.intentosRestantes <= 0 ->
                PantallaResultado.RechazadaSinReintentos(parsearInstante(respuesta.bloqueadoHasta))
            respuesta.estadoKyc == "RECHAZADO" ->
                PantallaResultado.RechazadaConReintentos(respuesta.intentosRestantes, respuesta.motivo)
            else -> PantallaResultado.Pendiente
        }
        else -> null
    }

/** Fecha ISO-8601 de FastAPI; sin zona se asume UTC, que es como la guarda MS Identidad. */
fun parsearInstante(texto: String?): Instant? {
    if (texto == null) return null
    return try {
        OffsetDateTime.parse(texto).toInstant()
    } catch (e: DateTimeParseException) {
        try {
            LocalDateTime.parse(texto).toInstant(ZoneOffset.UTC)
        } catch (e: DateTimeParseException) {
            null
        }
    }
}

/** Hora corta según el idioma del dispositivo: «3:30 p. m.» en es-CO, «3:30 PM» en en-US. */
fun formatearHora(instante: Instant, idioma: Locale, zona: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(idioma).format(instante.atZone(zona))
