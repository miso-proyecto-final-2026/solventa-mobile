package com.solventa.mobile.ui

import com.solventa.mobile.ui.componentes.fraccionDePasos
import org.junit.Assert.assertEquals
import org.junit.Test

class IndicadorPasosTest {
    @Test
    fun `la barra avanza proporcional al paso`() {
        assertEquals(1f / 3, fraccionDePasos(1, 3), 0.0001f)
        assertEquals(2f / 3, fraccionDePasos(2, 3), 0.0001f)
        assertEquals(1f, fraccionDePasos(3, 3), 0.0001f)
    }

    @Test
    fun `valores fuera de rango no rompen la barra`() {
        assertEquals(0f, fraccionDePasos(0, 3), 0f)
        assertEquals(1f, fraccionDePasos(5, 3), 0f)
        assertEquals(0f, fraccionDePasos(1, 0), 0f)
    }
}
