package com.solventa.mobile.polizas

import com.solventa.mobile.red.PolizaResponse
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EstadoPolizaTest {

    private val ahora = Instant.parse("2026-11-08T12:00:00Z")

    private fun poliza(estado: String = "VIGENTE", vence: String) = PolizaResponse(
        "p1", "s1", "SOL-0001", "microseguro_vida", estado, "2026-01-01T00:00:00Z", vence,
    )

    @Test
    fun `una poliza que vence en mas de 30 dias esta vigente`() {
        assertEquals(EstadoPoliza.VIGENTE, EstadosPoliza.de(poliza(vence = "2026-12-09T12:00:01Z"), ahora))
        assertEquals(EstadoPoliza.VIGENTE, EstadosPoliza.de(poliza(vence = "2027-06-12T00:00:00Z"), ahora))
    }

    @Test
    fun `a 30 dias o menos esta proxima a vencer`() {
        // Justo 30 días entra (el umbral es inclusivo); un segundo más no.
        assertEquals(EstadoPoliza.PROXIMA_A_VENCER, EstadosPoliza.de(poliza(vence = "2026-12-08T12:00:00Z"), ahora))
        assertEquals(EstadoPoliza.PROXIMA_A_VENCER, EstadosPoliza.de(poliza(vence = "2026-11-09T00:00:00Z"), ahora))
        assertEquals(EstadoPoliza.VIGENTE, EstadosPoliza.de(poliza(vence = "2026-12-08T12:00:01Z"), ahora))
    }

    @Test
    fun `desde el instante del vencimiento esta vencida`() {
        assertEquals(EstadoPoliza.VENCIDA, EstadosPoliza.de(poliza(vence = "2026-11-08T12:00:00Z"), ahora))
        assertEquals(EstadoPoliza.VENCIDA, EstadosPoliza.de(poliza(vence = "2025-01-01T00:00:00Z"), ahora))
    }

    @Test
    fun `la fecha con desfase horario se lee igual que la UTC`() {
        assertEquals(EstadoPoliza.VENCIDA, EstadosPoliza.de(poliza(vence = "2026-11-08T06:59:59-05:00"), ahora))
        assertEquals(EstadoPoliza.PROXIMA_A_VENCER, EstadosPoliza.de(poliza(vence = "2026-11-08T07:00:01-05:00"), ahora))
    }

    @Test
    fun `la firma pendiente y la anulada salen del estado del servidor, no de la fecha`() {
        assertEquals(EstadoPoliza.PENDIENTE_FIRMA, EstadosPoliza.de(poliza("PENDIENTE_FIRMA", "2025-01-01T00:00:00Z"), ahora))
        assertEquals(EstadoPoliza.ANULADA, EstadosPoliza.de(poliza("ANULADA", "2027-01-01T00:00:00Z"), ahora))
    }

    @Test
    fun `una fecha ilegible deja la poliza como vigente en vez de esconderla`() {
        assertEquals(EstadoPoliza.VIGENTE, EstadosPoliza.de(poliza(vence = "pronto"), ahora))
    }

    @Test
    fun `solo vigente, proxima y pendiente van en activas`() {
        val activas = EstadoPoliza.entries.filter { it.activa }.toSet()

        assertEquals(setOf(EstadoPoliza.VIGENTE, EstadoPoliza.PROXIMA_A_VENCER, EstadoPoliza.PENDIENTE_FIRMA), activas)
    }

    @Test
    fun `los documentos existen mientras la poliza fue emitida y el QR solo mientras cubre`() {
        assertEquals(
            setOf(EstadoPoliza.VIGENTE, EstadoPoliza.PROXIMA_A_VENCER, EstadoPoliza.VENCIDA),
            EstadoPoliza.entries.filter { it.tieneDocumentos }.toSet(),
        )
        assertEquals(setOf(EstadoPoliza.VIGENTE, EstadoPoliza.PROXIMA_A_VENCER), EstadoPoliza.entries.filter { it.conQr }.toSet())
        assertTrue(EstadoPoliza.PROXIMA_A_VENCER.conQr)
        assertFalse(EstadoPoliza.VENCIDA.conQr)
        assertFalse(EstadoPoliza.PENDIENTE_FIRMA.tieneDocumentos)
    }
}
