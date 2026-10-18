package com.solventa.mobile.cotizacion

import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatoCotizacionTest {

    private val esCo = Locale("es", "CO")

    @Test
    fun `los importes usan la separacion de miles de la localizacion`() {
        assertTrue(FormatoCotizacion.moneda(1_234_567, "COP", esCo).contains("1.234.567"))
        assertTrue(FormatoCotizacion.moneda(1_234_567, "COP", Locale.US).contains("1,234,567"))
    }

    @Test
    fun `los importes en pesos no llevan decimales`() {
        assertFalse(FormatoCotizacion.moneda(175_000, "COP", esCo).contains(","))
        assertFalse(FormatoCotizacion.moneda(175_000, "COP", Locale.US).contains("."))
    }

    @Test
    fun `una moneda desconocida no rompe el formato`() {
        assertTrue(FormatoCotizacion.moneda(1_000, "XXX1", Locale.US).contains("1,000"))
    }

    @Test
    fun `la vigencia se muestra en la zona horaria del dispositivo`() {
        val texto = FormatoCotizacion.vigencia("2026-11-17T23:49:58.235442Z", esCo, ZoneId.of("America/Bogota"))

        assertTrue(texto, texto.contains("17"))
        assertTrue(texto, texto.contains("2026"))
    }

    @Test
    fun `una fecha ilegible se devuelve tal cual en lugar de fallar`() {
        assertTrue(FormatoCotizacion.vigencia("no-es-fecha", esCo, ZoneId.of("UTC")) == "no-es-fecha")
    }
}
