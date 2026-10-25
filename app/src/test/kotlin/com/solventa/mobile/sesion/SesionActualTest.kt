package com.solventa.mobile.sesion

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SesionActualTest {

    @After
    fun cerrar() = SesionActual.cerrar()

    @Test
    fun `la sesion guarda el nombre y lo entrega con las credenciales`() {
        SesionActual.iniciar("c1", "token", "Ana María")

        assertEquals("Ana María", SesionActual.nombre)
        assertEquals(Credenciales("c1", "token", "Ana María"), SesionActual.credenciales())
    }

    @Test
    fun `sin nombre la sesion sigue activa`() {
        SesionActual.iniciar("c1", "token")

        assertNull(SesionActual.nombre)
        assertEquals(true, SesionActual.activa)
    }

    @Test
    fun `cerrar la sesion olvida el nombre`() {
        SesionActual.iniciar("c1", "token", "Ana")

        SesionActual.cerrar()

        assertNull(SesionActual.nombre)
    }

    @Test
    fun `el saludo usa solo el primer nombre`() {
        assertEquals("Ana", primerNombre("Ana María"))
        assertEquals("Ana", primerNombre("  Ana   María  "))
        assertEquals("Ana", primerNombre("Ana"))
    }

    @Test
    fun `un nombre vacio o ausente no saluda`() {
        assertNull(primerNombre(null))
        assertNull(primerNombre(""))
        assertNull(primerNombre("   "))
    }
}
