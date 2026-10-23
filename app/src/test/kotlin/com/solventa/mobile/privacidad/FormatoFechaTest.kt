package com.solventa.mobile.privacidad

import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatoFechaTest {

    private val bogota = ZoneId.of("America/Bogota")
    private val iso = "2026-10-22T18:30:00Z"

    @Test
    fun `usa la zona del dispositivo`() {
        val texto = FormatoFecha.fechaHora(iso, Locale.US, bogota)

        assertTrue(texto, texto.contains("1:30"))
    }

    @Test
    fun `cambia con el idioma`() {
        val es = FormatoFecha.fechaHora(iso, Locale.forLanguageTag("es-CO"), bogota)
        val en = FormatoFecha.fechaHora(iso, Locale.US, bogota)

        // El formato exacto depende de los datos de localización de la plataforma; lo que importa es que sigue al idioma.
        assertNotEquals(es, en)
        assertTrue(es, es.contains("2026"))
        assertTrue(en, en.contains("2026"))
    }

    @Test
    fun `una fecha ilegible se muestra tal cual y no rompe la pantalla`() {
        assertEquals("ayer", FormatoFecha.fechaHora("ayer", Locale.US, bogota))
    }
}
