package com.solventa.mobile.verificacion

/** Pasos del flujo de verificación. `numero` es el «n de 4» del indicador de progreso. */
enum class PasoVerificacion(val numero: Int?) {
    PERMISO(null),
    ANVERSO(1),
    REVERSO(2),
    REVISION(3),
    SELFIE(4),
    ENVIANDO(null),
    RESULTADO(null),
}

const val TOTAL_PASOS = 4

enum class EstadoResultado { VERIFICADO, RECHAZADO, EN_REVISION, BLOQUEADO }

data class VerificacionUiState(
    val paso: PasoVerificacion = PasoVerificacion.PERMISO,
    val permisoDenegado: Boolean = false,
    /** Captura recién tomada que espera «usar esta foto» o «repetir». */
    val capturaPendiente: String? = null,
    val analizando: Boolean = false,
    val problemaCalidad: ProblemaImagen? = null,
    val anverso: String? = null,
    val reverso: String? = null,
    val selfie: String? = null,
    val resultado: EstadoResultado? = null,
    val intentosRestantes: Int? = null,
    val reintentarEnSegundos: Long? = null,
    val error: ErrorEnvio? = null,
    val finalizado: Boolean = false,
)
