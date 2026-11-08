package com.solventa.mobile.polizas

import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatoPolizasTest {

    private val bogota = ZoneId.of("America/Bogota")

    // El formato exacto (`12 jun 2027` en un teléfono, `12/06/2027` en la JVM de las pruebas) lo decide la plataforma:
    // aquí se comprueba lo que depende de este código (idioma y zona), no los datos de localización del sistema.
    @Test
    fun `la fecha cambia con el idioma y conserva el dia y el ano`() {
        val es = FormatoPolizas.fecha("2027-06-12T15:00:00Z", Locale.forLanguageTag("es-CO"), bogota)
        val en = FormatoPolizas.fecha("2027-06-12T15:00:00Z", Locale.forLanguageTag("en-US"), bogota)

        assertNotEquals(es, en)
        listOf(es, en).forEach { assertTrue(it, it.contains("12") && it.contains("2027")) }
    }

    @Test
    fun `la fecha usa la zona del telefono, no la UTC`() {
        // 02:00 UTC del 12 de junio son las 21:00 del 11 en Bogotá.
        val enBogota = FormatoPolizas.fecha("2027-06-12T02:00:00Z", Locale.forLanguageTag("es-CO"), bogota)
        val enUtc = FormatoPolizas.fecha("2027-06-12T02:00:00Z", Locale.forLanguageTag("es-CO"), ZoneId.of("UTC"))

        assertTrue(enBogota, enBogota.contains("11") && !enBogota.contains("12"))
        assertTrue(enUtc, enUtc.contains("12"))
    }

    @Test
    fun `una fecha ilegible se muestra tal cual en vez de fallar`() {
        assertEquals("pronto", FormatoPolizas.fecha("pronto", Locale.forLanguageTag("es-CO"), bogota))
    }

    @Test
    fun `el tipo de cobertura se muestra legible`() {
        assertEquals("Fallecimiento", FormatoPolizas.tipoCobertura("fallecimiento"))
        assertEquals("Dano accidental", FormatoPolizas.tipoCobertura("dano_accidental"))
        assertEquals("Incapacidad total", FormatoPolizas.tipoCobertura(" incapacidad_total "))
    }
}
