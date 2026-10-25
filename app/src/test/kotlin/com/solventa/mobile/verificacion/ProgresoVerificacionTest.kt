package com.solventa.mobile.verificacion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgresoVerificacionTest {
    @Test
    fun `la barra va de 55 por ciento en el frente a 80 por ciento en la prueba de vida`() {
        assertEquals(0.54f, progresoDeVerificacion(1), 0.01f)
        assertEquals(0.80f, progresoDeVerificacion(4), 0.001f)
    }

    @Test
    fun `la barra solo avanza`() {
        val valores = (1..4).map(::progresoDeVerificacion)

        assertEquals(valores.sorted(), valores)
        assertTrue(valores.all { it in 0f..1f })
    }
}
