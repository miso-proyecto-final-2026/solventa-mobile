package com.solventa.mobile.compra

import com.solventa.mobile.red.CotizacionResponse

enum class FaseCompra {
    /** Resumen de la oferta y aceptación explícita de términos. */
    OFERTA,

    /** Ingreso del medio de pago. */
    PAGO,

    /** Obteniendo el token y enviando la solicitud. */
    ENVIANDO,

    /** La saga corre: decisión, cobro, firma, emisión. */
    PROGRESO,
    EXITOSA,
    DECISION_RECHAZADA,
    EN_REVISION,
    COBRO_RECHAZADO,
    OFERTA_VENCIDA,
    ERROR,
}

/** Cada falla tiene su mensaje y su acción (criterios 6 a 9). */
enum class ErrorCompra { SIN_CONEXION, SERVICIO, SESION_VENCIDA, RESULTADO_DESCONOCIDO, OFERTA_YA_USADA, SEGUIMIENTO_LENTO }

data class CompraUiState(
    val fase: FaseCompra = FaseCompra.OFERTA,
    val cotizacion: CotizacionResponse? = null,
    val aceptaTerminos: Boolean = false,
    /** Se marca al intentar continuar sin aceptar los términos. */
    val terminosPendientes: Boolean = false,
    val tarjeta: DatosTarjeta = DatosTarjeta(),
    val erroresTarjeta: Set<ErrorTarjeta> = emptySet(),
    /** Paso de la saga que se muestra en el progreso. */
    val paso: String = "DECISION",
    val motivos: List<String> = emptyList(),
    val ajustada: Boolean = false,
    val error: ErrorCompra? = null,
)
