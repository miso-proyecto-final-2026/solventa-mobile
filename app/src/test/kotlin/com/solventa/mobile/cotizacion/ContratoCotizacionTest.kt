package com.solventa.mobile.cotizacion

import com.solventa.mobile.red.CotizacionResponse
import com.solventa.mobile.red.ErrorCotizacionResponse
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El cliente contra respuestas reales de MS Cotización (HU12/HU13), capturadas del servicio desplegado en
 * staging. `cotizacion_degradada.json` está derivada de la respuesta con consentimiento: no se pudo provocar
 * una caída de proveedores en staging; el backend marca `degradado` y `aproximada` con el mismo aviso.
 */
class ContratoCotizacionTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun leer(nombre: String) = File("src/test/resources/contrato/$nombre").readText()

    private fun cotizacion(nombre: String) = json.decodeFromString<CotizacionResponse>(leer(nombre))

    @Test
    fun `con consentimiento trae prima, coberturas y vigencia`() {
        val c = cotizacion("cotizacion_con_consentimiento.json")

        assertEquals("microseguro_vida", c.producto)
        assertEquals("COP", c.moneda)
        assertEquals(175_000L, c.primaAnual)
        assertEquals(14_583L, c.primaMensual)
        assertEquals(listOf("muerte", "incapacidad_total"), c.coberturas.map { it.tipo })
        assertEquals(50_000_000L, c.coberturas.first().monto)
        assertEquals(0L, c.coberturas.first().deducible)
        assertTrue(c.vigenteHasta.startsWith("2026-11-17"))
        assertFalse(c.aproximada)
        assertFalse(c.degradado)
        assertNull(c.aviso)
    }

    @Test
    fun `sin consentimiento es aproximada y no degradada`() {
        val c = cotizacion("cotizacion_sin_consentimiento.json")

        assertTrue(c.aproximada)
        assertFalse(c.degradado)
        assertNotNull(c.aviso)
        assertEquals(listOf("dano_accidental", "robo"), c.coberturas.map { it.tipo })
        assertEquals(250_000L, c.coberturas.first().deducible)
    }

    @Test
    fun `degradada es aproximada y degradada`() {
        val c = cotizacion("cotizacion_degradada.json")

        assertTrue(c.degradado)
        assertTrue(c.aproximada)
    }

    @Test
    fun `el 422 trae un motivo por campo con el prefijo datos`() {
        val error = json.decodeFromString<ErrorCotizacionResponse>(leer("cotizacion_validacion_422.json")).error

        assertEquals("VALIDATION_ERROR", error.code)
        assertEquals(listOf("datos.edad", "datos.suma_asegurada"), error.details.map { it.campo })
        assertEquals("es requerido", error.details.last().motivo)
    }
}
