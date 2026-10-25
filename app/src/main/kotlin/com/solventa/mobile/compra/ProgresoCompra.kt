package com.solventa.mobile.compra

import com.solventa.mobile.red.SuscripcionResponse

/**
 * Lectura de la saga para el cliente (HU17, criterios 4 a 7). Es tolerante: un estado o paso que esta versión de la
 * app no conoce se trata como «en curso» y se sigue consultando, así una app anterior sigue funcionando cuando el
 * servidor agrega estados (criterio 11).
 */
sealed interface ProgresoCompra {
    /** La saga corre: [paso] es DECISION, COBRO, FIRMA, EMISION u otro que el servidor agregue. */
    data class EnCurso(val paso: String) : ProgresoCompra

    /** Se cobró: la compra se completó y continúa la firma (HU21). [referencia] y [fecha] son los de la pasarela. */
    data class Exitosa(val referencia: String?, val fecha: String?) : ProgresoCompra
    data class DecisionRechazada(val motivos: List<String>) : ProgresoCompra

    /** Derivada a revisión asistida o ajustada: sin cobro. [ajustada] distingue un ajuste de la oferta. */
    data class EnRevision(val ajustada: Boolean) : ProgresoCompra
    /** La pasarela rechazó el cobro; [graciaHasta] es la fecha límite para resolverlo (HU19 gestiona el siguiente paso). */
    data class CobroRechazado(val motivo: String?, val graciaHasta: String?) : ProgresoCompra

    /** La saga se compensó o terminó mal: el dinero no queda retenido. */
    data object Fallida : ProgresoCompra

    companion object {
        fun de(saga: SuscripcionResponse): ProgresoCompra {
            val decision = saga.decision?.decision
            val cobro = saga.cobro
            return when (saga.estadoSaga) {
                "COBRO_REALIZADO", "FIRMADA", "EMITIDA" -> Exitosa(cobro?.referenciaPasarela, cobro?.resueltoEn)
                // Contrato de HU18: el rechazo del cobro llega en `cobro`, con la saga aún APROBADA en el paso COBRO.
                "APROBADA" ->
                    if (cobro?.estado == "RECHAZADO") CobroRechazado(cobro.motivo, cobro.graciaHasta) else EnCurso("COBRO")
                "RECHAZADA" ->
                    if (saga.pasoActual == "COBRO") CobroRechazado(cobro?.motivo, cobro?.graciaHasta)
                    else DecisionRechazada(saga.decision?.motivos.orEmpty())
                "COMPENSADA" -> Fallida
                else -> when {
                    decision == "AJUSTADA" -> EnRevision(ajustada = true)
                    decision == "DERIVADA" || saga.revisionAsistida -> EnRevision(ajustada = false)
                    else -> EnCurso(saga.pasoActual)
                }
            }
        }
    }
}
