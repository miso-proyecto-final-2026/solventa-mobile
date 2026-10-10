package com.solventa.mobile.registro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValidadoresTest {

    @Test
    fun `nombre vacio es invalido`() {
        assertEquals("Este campo no puede estar vacío", Validadores.errorNombre("  "))
        assertNull(Validadores.errorNombre("Ana"))
    }

    @Test
    fun `email exige arroba y dominio`() {
        assertNull(Validadores.errorEmail("ana@example.com"))
        assertEquals(
            "El correo electrónico no tiene un formato válido",
            Validadores.errorEmail("no-es-correo"),
        )
    }

    @Test
    fun `celular exige diez digitos empezando en 3`() {
        assertNull(Validadores.errorCelular("3001234567"))
        assertEquals(
            "El celular debe tener 10 dígitos y empezar por 3",
            Validadores.errorCelular("2001234567"),
        )
    }

    @Test
    fun `documento acepta alfanumerico entre 5 y 15`() {
        assertNull(Validadores.errorDocumento("CC12345"))
        assertEquals(
            "El número de documento debe tener entre 5 y 15 caracteres alfanuméricos",
            Validadores.errorDocumento("123"),
        )
    }

    @Test
    fun `password exige ocho caracteres con letra y numero`() {
        assertNull(Validadores.errorPassword("Clave1234"))
        assertEquals(
            "La contraseña debe tener al menos 8 caracteres",
            Validadores.errorPassword("corta1"),
        )
        assertEquals(
            "La contraseña debe incluir al menos una letra",
            Validadores.errorPassword("12345678"),
        )
        assertEquals(
            "La contraseña debe incluir al menos un número",
            Validadores.errorPassword("solamenteletras"),
        )
    }
}
