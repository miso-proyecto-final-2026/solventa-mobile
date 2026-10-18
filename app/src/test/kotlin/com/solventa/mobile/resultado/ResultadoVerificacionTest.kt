package com.solventa.mobile.resultado

import com.solventa.mobile.R
import com.solventa.mobile.red.EstadoKycResponse
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mapeo estado → pantalla y acción, y conteo de reintentos (esquema de pruebas de HU03). */
class ResultadoVerificacionTest {

    private fun respuesta(
        estado: String,
        intentos: Int = 3,
        motivo: String? = null,
        bloqueadoHasta: String? = null,
    ) = EstadoKycResponse("c1", estado, motivo, intentos, bloqueadoHasta, null)

    // -- criterio 1 --------------------------------------------------------------------------

    @Test
    fun `verificado se muestra como aprobada con la accion continuar`() {
        val pantalla = mapearResultado(respuesta("VERIFICADO"))

        assertEquals(PantallaResultado.Aprobada, pantalla)
        assertEquals(AccionResultado.CONTINUAR, pantalla?.accion)
    }

    @Test
    fun `verificado gana aunque no queden intentos en la ventana`() {
        assertEquals(PantallaResultado.Aprobada, mapearResultado(respuesta("VERIFICADO", intentos = 0)))
    }

    // -- criterio 2 --------------------------------------------------------------------------

    @Test
    fun `rechazado con intentos ofrece reintentar y conserva cuantos quedan`() {
        val pantalla = mapearResultado(respuesta("RECHAZADO", intentos = 2, motivo = "VERIFICACION_NO_SUPERADA"))

        assertEquals(PantallaResultado.RechazadaConReintentos(2, "VERIFICACION_NO_SUPERADA"), pantalla)
        assertEquals(AccionResultado.REINTENTAR, pantalla?.accion)
    }

    @Test
    fun `con un solo intento restante todavia se puede reintentar`() {
        val pantalla = mapearResultado(respuesta("RECHAZADO", intentos = 1))

        assertTrue(pantalla is PantallaResultado.RechazadaConReintentos)
        assertEquals(1, (pantalla as PantallaResultado.RechazadaConReintentos).intentosRestantes)
    }

    @Test
    fun `el motivo generico se traduce a una guia sin criterios del proveedor`() {
        assertEquals(R.string.resultado_rechazada_guia, guiaDeCorreccion("VERIFICACION_NO_SUPERADA"))
        // Un código nuevo que la app no conoce tampoco revela nada: recibe la guía genérica.
        assertEquals(R.string.resultado_rechazada_guia, guiaDeCorreccion("OTRO_CODIGO"))
        assertEquals(R.string.resultado_rechazada_guia, guiaDeCorreccion(null))
    }

    // -- criterio 3 --------------------------------------------------------------------------

    @Test
    fun `sin intentos no se ofrece reintento y se informa desde cuando se puede volver a intentar`() {
        val pantalla = mapearResultado(respuesta("RECHAZADO", intentos = 0, bloqueadoHasta = "2026-10-16T12:30:00Z"))

        assertEquals(PantallaResultado.RechazadaSinReintentos(Instant.parse("2026-10-16T12:30:00Z")), pantalla)
        assertEquals(AccionResultado.ENTENDIDO, pantalla?.accion)
    }

    @Test
    fun `sin intentos y sin hora de fin igual se bloquea el reintento`() {
        assertEquals(
            PantallaResultado.RechazadaSinReintentos(null),
            mapearResultado(respuesta("RECHAZADO", intentos = 0)),
        )
    }

    @Test
    fun `intentos negativos se tratan como agotados`() {
        assertTrue(mapearResultado(respuesta("RECHAZADO", intentos = -1)) is PantallaResultado.RechazadaSinReintentos)
    }

    // -- criterio 4 --------------------------------------------------------------------------

    @Test
    fun `en revision informa que esta en proceso con la accion entendido`() {
        val pantalla = mapearResultado(respuesta("EN_REVISION", motivo = "PROVEEDOR_NO_DISPONIBLE"))

        assertEquals(PantallaResultado.EnRevision, pantalla)
        assertEquals(AccionResultado.ENTENDIDO, pantalla?.accion)
    }

    // -- sin resultado -----------------------------------------------------------------------

    @Test
    fun `pendiente no es un resultado y no tiene accion`() {
        val pantalla = mapearResultado(respuesta("PENDIENTE"))

        assertEquals(PantallaResultado.Pendiente, pantalla)
        assertNull(pantalla?.accion)
    }

    @Test
    fun `un estado desconocido no se interpreta en el cliente`() {
        assertNull(mapearResultado(respuesta("OTRO")))
    }

    // -- fechas ------------------------------------------------------------------------------

    @Test
    fun `parsea la fecha del servidor con zona, con desfase y sin zona`() {
        val esperado = Instant.parse("2026-10-16T12:30:00Z")

        assertEquals(esperado, parsearInstante("2026-10-16T12:30:00Z"))
        assertEquals(esperado, parsearInstante("2026-10-16T07:30:00-05:00"))
        assertEquals(esperado, parsearInstante("2026-10-16T12:30:00"))
        assertEquals(Instant.parse("2026-10-16T12:30:00.123456Z"), parsearInstante("2026-10-16T12:30:00.123456+00:00"))
    }

    @Test
    fun `una fecha invalida o ausente no rompe la pantalla`() {
        assertNull(parsearInstante("mañana"))
        assertNull(parsearInstante(null))
    }

    @Test
    fun `la hora de fin del bloqueo se formatea segun el idioma`() {
        val instante = Instant.parse("2026-10-16T20:30:00Z")
        val bogota = ZoneId.of("America/Bogota")

        val ingles = formatearHora(instante, Locale.US, bogota)
        val espanol = formatearHora(instante, Locale.forLanguageTag("es-CO"), bogota)

        assertTrue(ingles, ingles.startsWith("3:30") && ingles.contains("PM"))
        assertTrue(espanol, espanol.startsWith("3:30") && espanol.contains("p"))
    }
}
