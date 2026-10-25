package com.solventa.mobile.compra

import com.solventa.mobile.red.DecisionResponse
import com.solventa.mobile.red.SuscripcionResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgresoCompraTest {
    private fun saga(
        estado: String,
        paso: String = "DECISION",
        decision: String? = null,
        motivos: List<String> = emptyList(),
        revision: Boolean = false,
    ) = SuscripcionResponse(
        id = "s1",
        cotizacionId = "q1",
        estadoSaga = estado,
        pasoActual = paso,
        decision = decision?.let { DecisionResponse(it, motivos) },
        revisionAsistida = revision,
    )

    @Test
    fun `decidiendo es en curso en el paso de decision`() {
        assertEquals(ProgresoCompra.EnCurso("DECISION"), ProgresoCompra.de(saga("EN_PROGRESO")))
    }

    @Test
    fun `aprobada sin cobro todavia es el cobro en proceso`() {
        assertEquals(ProgresoCompra.EnCurso("COBRO"), ProgresoCompra.de(saga("APROBADA", "COBRO", "APROBADA")))
    }

    @Test
    fun `cobro realizado y los estados posteriores son compra exitosa`() {
        listOf("COBRO_REALIZADO", "FIRMADA", "EMITIDA").forEach {
            assertEquals(it, ProgresoCompra.Exitosa(null, null), ProgresoCompra.de(saga(it, "FIRMA", "APROBADA")))
        }
    }

    @Test
    fun `rechazada en la decision lleva los motivos`() {
        val saga = saga("RECHAZADA", "DECISION", "RECHAZADA", listOf("PERFIL_NO_ELEGIBLE"))

        assertEquals(ProgresoCompra.DecisionRechazada(listOf("PERFIL_NO_ELEGIBLE")), ProgresoCompra.de(saga))
    }

    @Test
    fun `rechazada en el cobro es cobro rechazado`() {
        assertEquals(ProgresoCompra.CobroRechazado(null, null), ProgresoCompra.de(saga("RECHAZADA", "COBRO", "APROBADA")))
    }

    @Test
    fun `derivada a revision asistida no cobra`() {
        assertEquals(ProgresoCompra.EnRevision(false), ProgresoCompra.de(saga("EN_PROGRESO", decision = "DERIVADA", revision = true)))
    }

    @Test
    fun `ajustada se distingue de la revision`() {
        assertEquals(ProgresoCompra.EnRevision(true), ProgresoCompra.de(saga("EN_PROGRESO", decision = "AJUSTADA")))
    }

    @Test
    fun `compensada es una falla`() {
        assertEquals(ProgresoCompra.Fallida, ProgresoCompra.de(saga("COMPENSADA", "COBRO", "APROBADA")))
    }

    @Test
    fun `un estado nuevo del servidor se trata como en curso`() {
        assertEquals(ProgresoCompra.EnCurso("VERIFICACION_FRAUDE"), ProgresoCompra.de(saga("EN_ANALISIS", "VERIFICACION_FRAUDE")))
    }
}
