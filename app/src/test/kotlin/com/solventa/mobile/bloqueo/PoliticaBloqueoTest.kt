package com.solventa.mobile.bloqueo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PoliticaBloqueoTest {

    private val minuto = 60_000L

    @Test
    fun `el plazo en segundo plano es de cinco minutos y tres los intentos de biometria`() {
        assertEquals(5 * minuto, PLAZO_SEGUNDO_PLANO_MS)
        assertEquals(3, MAX_INTENTOS_BIOMETRIA)
    }

    @Test
    fun `si nunca salio de primer plano no se bloquea por segundo plano`() {
        assertFalse(debeBloquearTrasSegundoPlano(null, ahoraMs = 10 * minuto))
    }

    @Test
    fun `antes de cinco minutos no se vuelve a pedir`() {
        assertFalse(debeBloquearTrasSegundoPlano(salioEnMs = 0, ahoraMs = 5 * minuto - 1))
        assertFalse(debeBloquearTrasSegundoPlano(salioEnMs = 1_000, ahoraMs = 30_000))
    }

    @Test
    fun `a los cinco minutos o mas si se vuelve a pedir`() {
        assertTrue(debeBloquearTrasSegundoPlano(salioEnMs = 0, ahoraMs = 5 * minuto))
        assertTrue(debeBloquearTrasSegundoPlano(salioEnMs = 0, ahoraMs = 60 * minuto))
    }

    @Test
    fun `el plazo se puede cambiar`() {
        assertTrue(debeBloquearTrasSegundoPlano(salioEnMs = 0, ahoraMs = minuto, plazoMs = minuto))
        assertFalse(debeBloquearTrasSegundoPlano(salioEnMs = 0, ahoraMs = minuto - 1, plazoMs = minuto))
    }
}
