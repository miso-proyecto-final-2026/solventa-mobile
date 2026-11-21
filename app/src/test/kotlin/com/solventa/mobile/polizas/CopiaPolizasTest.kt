package com.solventa.mobile.polizas

import com.solventa.mobile.red.CoberturaPolizaResponse
import com.solventa.mobile.red.PolizaResponse
import java.time.Instant
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** HU36: qué se guarda para ver las pólizas sin conexión y cómo lo del servidor reemplaza a la copia. */
class CopiaPolizasTest {

    private val momento = Instant.parse("2026-11-14T15:30:00Z")

    private fun poliza(id: String) = PolizaResponse(
        id = "p-$id", suscripcionId = id, numeroPoliza = "SOL-$id", producto = "microseguro_vida", estado = "VIGENTE",
        vigenciaDesde = "2026-06-12T00:00:00Z", fechaVencimiento = "2027-06-12T00:00:00Z", fechaEmision = "2026-06-12T00:00:00Z",
        primaAnual = 175_000, primaMensual = 15_000, periodicidadPago = "MENSUAL",
        coberturas = listOf(CoberturaPolizaResponse("fallecimiento", 50_000_000, 1_000_000)),
    )

    @Test
    fun `guarda numero, estado, producto, coberturas y vigencia (criterio 1)`() {
        val guardada = poliza("s1").aGuardada()

        assertEquals("SOL-s1", guardada.numeroPoliza)
        assertEquals("VIGENTE", guardada.estado)
        assertEquals("microseguro_vida", guardada.producto)
        assertEquals("2026-06-12T00:00:00Z", guardada.vigenciaDesde)
        assertEquals("2027-06-12T00:00:00Z", guardada.fechaVencimiento)
        assertEquals(listOf(CoberturaGuardada("fallecimiento", 50_000_000, 1_000_000)), guardada.coberturas)
    }

    @Test
    fun `no guarda la prima ni la fecha de emision (criterio 7, lo minimo)`() {
        val copia = CopiasPolizas.desde("c1", listOf(poliza("s1")), momento, anterior = null)

        val json = Json.encodeToString(CopiaPolizas.serializer(), copia)

        assertFalse(json.contains("prima"))
        assertFalse(json.contains("175000"))
        assertFalse(json.contains("fecha_emision"))
        assertFalse(json.contains("periodicidad"))
    }

    @Test
    fun `la poliza guardada vuelve a mostrarse con los mismos datos y sin prima`() {
        val original = poliza("s1")

        val vuelta = original.aGuardada().aRespuesta()

        assertEquals(original.copy(fechaEmision = null, primaAnual = 0, primaMensual = 0, periodicidadPago = "ANUAL"), vuelta)
    }

    @Test
    fun `lo del servidor reemplaza a la copia anterior (criterio 8)`() {
        val anterior = CopiasPolizas.desde("c1", listOf(poliza("s1"), poliza("s2")), Instant.parse("2026-11-01T00:00:00Z"), null)

        val nueva = CopiasPolizas.desde("c1", listOf(poliza("s3")), momento, anterior)

        assertEquals(listOf("s3"), nueva.polizas.map { it.suscripcionId })
        assertEquals("2026-11-14T15:30:00Z", nueva.sincronizadoEn)
    }

    @Test
    fun `conserva los documentos descargados de las polizas que siguen y olvida los demas`() {
        val anterior = CopiasPolizas.desde("c1", listOf(poliza("s1"), poliza("s2")), momento, null)
            .conDocumento(claveDocumento("s1", DocumentoPoliza.CERTIFICADO), "content://docs/s1-certificado")
            .conDocumento(claveDocumento("s2", DocumentoPoliza.POLIZA_FIRMADA), "content://docs/s2-firmada")

        val nueva = CopiasPolizas.desde("c1", listOf(poliza("s1")), momento, anterior)

        assertEquals(mapOf("s1/CERTIFICADO" to "content://docs/s1-certificado"), nueva.documentos)
    }

    @Test
    fun `los documentos de otro cliente no pasan a la copia nueva`() {
        val deOtro = CopiasPolizas.desde("otro", listOf(poliza("s1")), momento, null)
            .conDocumento(claveDocumento("s1", DocumentoPoliza.CERTIFICADO), "content://docs/ajeno")

        val nueva = CopiasPolizas.desde("c1", listOf(poliza("s1")), momento, deOtro)

        assertTrue(nueva.documentos.isEmpty())
    }
}
