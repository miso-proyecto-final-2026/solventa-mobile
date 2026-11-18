package com.solventa.mobile.notificaciones

import com.solventa.mobile.red.NotificacionResponse
import com.solventa.mobile.red.PolizaResponse
import com.solventa.mobile.red.SolicitudDispositivo
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Respuestas con la forma real de MS Notificaciones y MS Suscripción (HU22): la app las entiende y tolera lo que no conoce. */
class ContratoNotificacionesTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun cargar(nombre: String): String =
        checkNotNull(javaClass.classLoader?.getResource("contrato/$nombre")) { "falta $nombre" }.readText()

    @Test
    fun `la bandeja trae la notificacion con su version publica y a que suscripcion pertenece`() {
        val (n) = json.decodeFromString<List<NotificacionResponse>>(cargar("notificacion.json"))

        assertEquals("Suscripción completada", n.titulo)
        assertEquals("ALTA", n.prioridad)
        assertEquals("fbc3fcd2-6d92-40e9-a853-b9366e6c99e7", n.suscripcionId)
        assertEquals("Solventa", n.tituloPublico)
        assertEquals("Tienes una notificación nueva.", n.textoPublico)
        assertFalse(n.recibida)
    }

    @Test
    fun `la indemnizacion pagada trae la poliza, no la suscripcion, y su version publica no lleva el monto`() {
        val (n) = json.decodeFromString<List<NotificacionResponse>>(cargar("notificacion_indemnizacion.json"))

        assertEquals("Indemnización pagada", n.titulo)
        assertEquals("ALTA", n.prioridad)
        assertEquals("90d7708a-1f26-4e2e-9c2d-722dca46c475", n.polizaId)
        assertNull(n.suscripcionId)
        assertTrue(n.texto.contains("1.500.000"))
        for (texto in listOf(n.tituloPublico, n.textoPublico)) {
            assertFalse(texto, texto.any(Char::isDigit))
        }
    }

    @Test
    fun `la version publica de la notificacion no repite el numero de poliza ni importes`() {
        val (n) = json.decodeFromString<List<NotificacionResponse>>(cargar("notificacion.json"))

        for (texto in listOf(n.tituloPublico, n.textoPublico)) {
            assertFalse(texto, texto.any(Char::isDigit))
        }
    }

    @Test
    fun `la poliza trae el numero, la vigencia, la prima y las coberturas`() {
        val poliza = json.decodeFromString<PolizaResponse>(cargar("poliza.json"))

        assertEquals("36GBQKV2CWMM3EBB8Q6Q", poliza.numeroPoliza)
        assertEquals("VIGENTE", poliza.estado)
        assertEquals(175_000L, poliza.primaAnual)
        assertEquals(listOf("muerte", "incapacidad_total"), poliza.coberturas.map { it.tipo })
        assertEquals(40_000_000L, poliza.coberturas.first().monto)
        assertEquals("2027-10-30T20:43:00Z", poliza.fechaVencimiento)
    }

    @Test
    fun `una poliza anterior sin coberturas ni importes se lee con valores por defecto`() {
        val minima = """{"id":"p","suscripcion_id":"s","numero_poliza":"N","producto":"x","estado":"VIGENTE","vigencia_desde":"2026-10-30T00:00:00Z","fecha_vencimiento":"2027-10-30T00:00:00Z"}"""

        val poliza = json.decodeFromString<PolizaResponse>(minima)

        assertEquals(emptyList<Any>(), poliza.coberturas)
        assertEquals("COP", poliza.moneda)
        assertEquals(0L, poliza.primaAnual)
    }

    @Test
    fun `el registro del dispositivo lleva su identificador y el idioma`() {
        val cuerpo = json.encodeToString(SolicitudDispositivo("app-1234", "en-US"))

        assertEquals("""{"dispositivo_id":"app-1234","idioma":"en-US"}""", cuerpo)
    }
}
