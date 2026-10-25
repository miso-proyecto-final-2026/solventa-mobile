package com.solventa.mobile.compra

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

/** Lo que el cliente puede teclear en cada campo de la tarjeta (HU17, criterio 2). */
class EntradaTarjetaTest {

    @Test
    fun `el numero solo admite digitos y hasta 19`() {
        assertEquals("4111111111111111", EntradaTarjeta.numero("4111 1111-1111 1111"))
        assertEquals("41111111111111111111".take(19), EntradaTarjeta.numero("41111111111111111111999"))
        assertEquals("4111", EntradaTarjeta.numero("41ab11"))
        assertEquals("", EntradaTarjeta.numero("abc"))
    }

    @Test
    fun `el codigo de seguridad solo admite digitos y hasta 4`() {
        assertEquals("1234", EntradaTarjeta.cvv("123456"))
        assertEquals("12", EntradaTarjeta.cvv("1a2"))
    }

    @Test
    fun `el vencimiento se escribe como MM barra AA con un mes valido`() {
        assertEquals("1", EntradaTarjeta.vencimiento("1"))
        assertEquals("12", EntradaTarjeta.vencimiento("12"))
        assertEquals("12/2", EntradaTarjeta.vencimiento("122"))
        assertEquals("12/28", EntradaTarjeta.vencimiento("1228"))
        assertEquals("12/28", EntradaTarjeta.vencimiento("12/28"))
        assertEquals("12/28", EntradaTarjeta.vencimiento("122899"))
        assertEquals("", EntradaTarjeta.vencimiento("ab"))
    }

    @Test
    fun `un primer digito mayor que 1 completa el mes con cero`() {
        assertEquals("05", EntradaTarjeta.vencimiento("5"))
        assertEquals("09/2", EntradaTarjeta.vencimiento("92"))
        assertEquals("05/28", EntradaTarjeta.vencimiento("528"))
    }

    @Test
    fun `un mes imposible no se acepta`() {
        assertEquals("1", EntradaTarjeta.vencimiento("13"))
        assertEquals("1", EntradaTarjeta.vencimiento("19"))
        assertEquals("0", EntradaTarjeta.vencimiento("00"))
        assertEquals("0", EntradaTarjeta.vencimiento("0"))
    }

    @Test
    fun `el numero se muestra en grupos de 4 sin cambiar lo guardado`() {
        val visual = AgrupadorDeTarjeta.filter(AnnotatedString("4111111111111111"))

        assertEquals("4111 1111 1111 1111", visual.text.text)
    }

    @Test
    fun `el cursor se mantiene al agrupar`() {
        val visual = AgrupadorDeTarjeta.filter(AnnotatedString("4111111111111111"))
        val mapa = visual.offsetMapping

        assertEquals(0, mapa.originalToTransformed(0))
        assertEquals(4, mapa.originalToTransformed(4))
        assertEquals(6, mapa.originalToTransformed(5))
        assertEquals(19, mapa.originalToTransformed(16))
        assertEquals(0, mapa.transformedToOriginal(0))
        assertEquals(5, mapa.transformedToOriginal(6))
        assertEquals(16, mapa.transformedToOriginal(19))
    }

    @Test
    fun `un numero corto o vacio no se rompe al agrupar`() {
        assertEquals("", AgrupadorDeTarjeta.filter(AnnotatedString("")).text.text)
        assertEquals("411", AgrupadorDeTarjeta.filter(AnnotatedString("411")).text.text)
        assertEquals("4111 1", AgrupadorDeTarjeta.filter(AnnotatedString("41111")).text.text)
    }
}
