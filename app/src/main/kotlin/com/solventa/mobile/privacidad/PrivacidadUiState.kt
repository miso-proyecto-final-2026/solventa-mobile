package com.solventa.mobile.privacidad

import com.solventa.mobile.red.ConsentimientoResponse

enum class FasePrivacidad { CARGANDO, ESTADO, CONFIRMANDO, REVOCANDO }

/** Cada falla tiene su mensaje y su acción (HU07, criterio 8). */
enum class ErrorPrivacidad { TIEMPO_AGOTADO, SIN_CONEXION, SERVICIO, SESION_VENCIDA }

/** Lo que `reintentar()` repite. */
enum class AccionPrivacidad { CONSULTAR, REVOCAR }

data class PrivacidadUiState(
    val fase: FasePrivacidad = FasePrivacidad.CARGANDO,
    /** Estado confirmado por el servidor; nunca se adelanta a la respuesta. */
    val consentimiento: ConsentimientoResponse? = null,
    val error: ErrorPrivacidad? = null,
    val pendiente: AccionPrivacidad? = null,
    /** Verdadero solo justo después de que el servidor confirmó la revocación. */
    val recienRevocado: Boolean = false,
) {
    val puedeRevocar: Boolean get() = consentimiento?.estado == ESTADO_OTORGADO
    val puedeCompartir: Boolean get() = consentimiento != null && !puedeRevocar
}
