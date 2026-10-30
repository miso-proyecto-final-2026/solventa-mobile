package com.solventa.mobile.notificaciones

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class IdiomaDelTelefonoTest {
    @Test
    fun `un telefono en ingles pide los textos en en-US`() {
        assertEquals("en-US", IdiomaDelTelefono.de(Locale.US))
        assertEquals("en-US", IdiomaDelTelefono.de(Locale.UK))
        assertEquals("en-US", IdiomaDelTelefono.de(Locale.forLanguageTag("en")))
    }

    @Test
    fun `cualquier otro idioma cae a es-CO`() {
        assertEquals("es-CO", IdiomaDelTelefono.de(Locale.forLanguageTag("es-CO")))
        assertEquals("es-CO", IdiomaDelTelefono.de(Locale.forLanguageTag("es-419")))
        assertEquals("es-CO", IdiomaDelTelefono.de(Locale.FRANCE))
    }
}
