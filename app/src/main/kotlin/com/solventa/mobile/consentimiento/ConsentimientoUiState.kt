package com.solventa.mobile.consentimiento

/** Cómo terminó el paso. `null` mientras el usuario aún no decide. */
sealed interface DecisionConsentimiento {
    data class Otorgado(val versionTexto: String, val fechaOtorgamiento: String) : DecisionConsentimiento
    data object Rechazado : DecisionConsentimiento
}

data class ConsentimientoUiState(
    val detalleExpandido: Boolean = false,
    val cargando: Boolean = false,
    val mensajeError: String? = null,
    val decision: DecisionConsentimiento? = null,
    val continuar: Boolean = false,
)
