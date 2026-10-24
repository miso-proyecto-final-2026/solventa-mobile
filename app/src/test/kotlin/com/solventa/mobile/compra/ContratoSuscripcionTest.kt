package com.solventa.mobile.compra

import com.solventa.mobile.red.ErrorSuscripcionResponse
import com.solventa.mobile.red.SolicitudSuscripcion
import com.solventa.mobile.red.SuscripcionResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Respuestas con la forma de MS Suscripción (HU15, `SuscripcionOut`): la app las entiende y tolera lo que no conoce. */
class ContratoSuscripcionTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun cargar(nombre: String): String =
        checkNotNull(javaClass.classLoader?.getResource("contrato/$nombre")) { "falta $nombre" }.readText()

    private fun saga(nombre: String) = json.decodeFromString<SuscripcionResponse>(cargar(nombre))

    @Test
    fun `la saga en progreso se lee y se interpreta como decision en curso`() {
        assertEquals(ProgresoCompra.EnCurso("DECISION"), ProgresoCompra.de(saga("suscripcion_en_progreso.json")))
    }

    @Test
    fun `aprobada con cobro pendiente sigue en curso en el cobro`() {
        assertEquals(ProgresoCompra.EnCurso("COBRO"), ProgresoCompra.de(saga("suscripcion_aprobada.json")))
    }

    @Test
    fun `derivada a revision asistida`() {
        assertEquals(ProgresoCompra.EnRevision(false), ProgresoCompra.de(saga("suscripcion_derivada.json")))
    }

    @Test
    fun `un estado, un paso y campos nuevos no rompen una app anterior`() {
        val nueva = saga("suscripcion_estado_nuevo.json")

        assertEquals(ProgresoCompra.EnCurso("ANTIFRAUDE"), ProgresoCompra.de(nueva))
    }

    @Test
    fun `el cuerpo de error expone el codigo`() {
        val error = json.decodeFromString<ErrorSuscripcionResponse>(cargar("suscripcion_error_vencida.json"))

        assertEquals("COTIZACION_VENCIDA", error.error.code)
    }

    @Test
    fun `la solicitud solo lleva la cotizacion, el token y la aceptacion`() {
        val cuerpo = Json.encodeToString(SolicitudSuscripcion("q1", "tok_sim_1", true))

        assertEquals("""{"cotizacion_id":"q1","metodo_pago":"tok_sim_1","acepta_terminos":true}""", cuerpo)
    }

    @Test
    fun `el estado de la pantalla no revela la tarjeta aunque se imprima`() {
        val estado = CompraUiState(tarjeta = DatosTarjeta("4111111111111111", "12/28", "123"))

        assertFalse("$estado".contains("4111"))
        assertFalse("$estado".contains("12/28"))
    }

    @Test
    fun `el vencimiento se escribe con la barra sola`() {
        assertEquals("1", formatearVencimiento("1"))
        assertEquals("12/2", formatearVencimiento("122"))
        assertEquals("12/28", formatearVencimiento("1228"))
        assertEquals("12/28", formatearVencimiento("12/28"))
        assertEquals("12/28", formatearVencimiento("1228999"))
    }
}
