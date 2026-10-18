package com.solventa.mobile.resultado

import com.solventa.mobile.red.EstadoKycResponse
import java.time.Instant
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Contrato con MS Identidad: `src/test/resources/contrato/` guarda respuestas reales de
 * `GET /v1/clientes/{id}/verificacion`, generadas con la suite de solventa-backend (proveedor
 * simulado de HU02). El parser es estricto a propósito: un campo nuevo, renombrado o con otro tipo
 * hace fallar esta prueba antes que la app.
 */
class ContratoEstadoKycTest {

    private val estricto = Json { ignoreUnknownKeys = false }

    private fun leer(nombre: String): EstadoKycResponse {
        val recurso = javaClass.classLoader!!.getResource("contrato/$nombre")
        assertNotNull("Falta el fixture $nombre", recurso)
        return estricto.decodeFromString(EstadoKycResponse.serializer(), recurso!!.readText())
    }

    @Test
    fun `pendiente`() {
        val r = leer("estado_pendiente.json")

        assertEquals("PENDIENTE", r.estadoKyc)
        assertEquals(PantallaResultado.Pendiente, mapearResultado(r))
    }

    @Test
    fun `verificado`() {
        assertEquals(PantallaResultado.Aprobada, mapearResultado(leer("estado_verificado.json")))
    }

    @Test
    fun `rechazado con intentos`() {
        val r = leer("estado_rechazado.json")

        assertEquals(
            PantallaResultado.RechazadaConReintentos(2, "VERIFICACION_NO_SUPERADA"),
            mapearResultado(r),
        )
    }

    @Test
    fun `rechazado sin intentos con la hora de fin del bloqueo`() {
        val r = leer("estado_bloqueado.json")

        assertEquals(
            PantallaResultado.RechazadaSinReintentos(Instant.parse("2026-10-16T12:30:00Z")),
            mapearResultado(r),
        )
    }

    @Test
    fun `en revision`() {
        assertEquals(PantallaResultado.EnRevision, mapearResultado(leer("estado_en_revision.json")))
    }
}
