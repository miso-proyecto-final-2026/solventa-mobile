package com.solventa.mobile.compra

import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PagoTest {
    private val hoy = YearMonth.of(2026, 10)
    private fun tarjeta(numero: String = "4111111111111111", vence: String = "12/28", cvv: String = "123") =
        DatosTarjeta(numero, vence, cvv)

    @Test
    fun `una tarjeta de prueba valida no tiene errores`() {
        assertTrue(ValidadorTarjeta.validar(tarjeta(), hoy).isEmpty())
    }

    @Test
    fun `numero con digito de control incorrecto o longitud invalida`() {
        assertEquals(setOf(ErrorTarjeta.NUMERO), ValidadorTarjeta.validar(tarjeta(numero = "4111111111111112"), hoy))
        assertEquals(setOf(ErrorTarjeta.NUMERO), ValidadorTarjeta.validar(tarjeta(numero = "4111"), hoy))
        assertEquals(setOf(ErrorTarjeta.NUMERO), ValidadorTarjeta.validar(tarjeta(numero = "4111a11111111111"), hoy))
    }

    @Test
    fun `el vencimiento debe tener formato MM-AA y no estar vencido`() {
        assertTrue(ValidadorTarjeta.validar(tarjeta(vence = "10/26"), hoy).isEmpty())
        listOf("09/26", "13/28", "1228", "12/2028", "ab/cd").forEach {
            assertEquals(it, setOf(ErrorTarjeta.VENCIMIENTO), ValidadorTarjeta.validar(tarjeta(vence = it), hoy))
        }
    }

    @Test
    fun `el codigo de seguridad tiene tres o cuatro digitos`() {
        assertTrue(ValidadorTarjeta.validar(tarjeta(cvv = "1234"), hoy).isEmpty())
        listOf("12", "12345", "12a").forEach {
            assertEquals(it, setOf(ErrorTarjeta.CVV), ValidadorTarjeta.validar(tarjeta(cvv = it), hoy))
        }
    }

    @Test
    fun `los datos de la tarjeta no se revelan en texto`() {
        val texto = "${tarjeta()}"

        assertFalse(texto.contains("4111"))
        assertFalse(texto.contains("123"))
    }

    @Test
    fun `el token simulado no contiene nada de la tarjeta`() = runTest {
        val token = TokenizadorSimulado { "abc" }.tokenizar(tarjeta())

        assertEquals("tok_sim_abc", token)
    }

    @Test
    fun `las tarjetas de prueba eligen el modo de la pasarela simulada por el prefijo del token`() = runTest {
        val tokenizador = TokenizadorSimulado { "abc" }

        assertEquals("tok_sim_rechazar_abc", tokenizador.tokenizar(tarjeta(numero = "4000000000000002")))
        assertEquals("tok_sim_perder_abc", tokenizador.tokenizar(tarjeta(numero = "4000000000000119")))
        assertEquals("tok_sim_tardar_abc", tokenizador.tokenizar(tarjeta(numero = "4000000000000259")))
        assertEquals("tok_sim_abc", tokenizador.tokenizar(tarjeta(numero = "4111111111111111")))
    }

    @Test
    fun `el token con prefijo de modo tampoco contiene datos de la tarjeta`() = runTest {
        val token = TokenizadorSimulado { "abc" }.tokenizar(tarjeta(numero = "4000000000000002", cvv = "987"))

        assertFalse(token.contains("4000000000000002"))
        assertFalse(token.contains("987"))
    }

    @Test
    fun `las tarjetas de prueba pasan la validacion local`() {
        listOf("4000000000000002", "4000000000000119", "4000000000000259").forEach {
            assertTrue(it, ValidadorTarjeta.validar(tarjeta(numero = it), hoy).isEmpty())
        }
    }
}
