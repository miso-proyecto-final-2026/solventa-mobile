package com.solventa.mobile.firma

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatoFirmaTest {
    @Test
    fun `la cuenta atras se escribe como minutos y segundos`() {
        assertEquals("0:42", FormatoFirma.cuentaAtras(42))
        assertEquals("1:00", FormatoFirma.cuentaAtras(60))
        assertEquals("0:05", FormatoFirma.cuentaAtras(5))
        assertEquals("0:00", FormatoFirma.cuentaAtras(-3))
    }

    @Test
    fun `las casillas muestran los digitos escritos y dejan huecos hasta seis`() {
        assertEquals(listOf('4', '8', '1', null, null, null), FormatoFirma.casillas("481"))
        assertEquals(List(6) { null }, FormatoFirma.casillas(""))
        assertEquals(listOf('1', '2', '3', '4', '5', '6'), FormatoFirma.casillas("123456"))
    }
}
