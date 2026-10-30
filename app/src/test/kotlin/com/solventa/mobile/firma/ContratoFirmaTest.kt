package com.solventa.mobile.firma

import com.solventa.mobile.red.DocumentoFirmaResponse
import com.solventa.mobile.red.ErrorSuscripcionResponse
import com.solventa.mobile.red.FirmaResponse
import com.solventa.mobile.red.SolicitudFirma
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Respuestas con la forma real de MS Suscripción (HU21): la app las entiende y tolera lo que no conoce. */
class ContratoFirmaTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun cargar(nombre: String): String =
        checkNotNull(javaClass.classLoader?.getResource("contrato/$nombre")) { "falta $nombre" }.readText()

    @Test
    fun `el documento por firmar trae el hash, el numero y el vencimiento`() {
        val documento = json.decodeFromString<DocumentoFirmaResponse>(cargar("firma_documento.json"))

        assertEquals("36GBQKV2CWMM3EBB8Q6Q", documento.numeroPoliza)
        assertEquals(64, documento.documentoSha256.length)
        assertEquals("PENDIENTE", documento.estado)
        assertEquals("2026-10-31T20:43:00.437375Z", documento.venceEn)
        assertEquals("OTP", documento.autenticacion) // y un campo nuevo del servidor no rompe la lectura
    }

    @Test
    fun `la firma devuelve la saga emitida y el canal`() {
        val firma = json.decodeFromString<FirmaResponse>(cargar("firma_ok.json"))

        assertEquals("EMITIDA", firma.estadoSaga)
        assertEquals("MOVIL", firma.canal)
    }

    @Test
    fun `el error de codigo trae los intentos restantes en el detalle`() {
        val error = json.decodeFromString<ErrorSuscripcionResponse>(cargar("firma_error_otp.json")).error

        assertEquals("OTP_INVALIDO", error.code)
        assertEquals("intentos restantes: 2", error.details.single().motivo)
    }

    @Test
    fun `la firma fallida llega con su codigo y sin detalles`() {
        val error = json.decodeFromString<ErrorSuscripcionResponse>(cargar("firma_error_fallida.json")).error

        assertEquals("FIRMA_FALLIDA", error.code)
        assertEquals(emptyList<Any>(), error.details)
    }

    @Test
    fun `la solicitud de firma lleva el hash, la revision, el codigo y el canal, y nada de tarjeta`() {
        val cuerpo = json.encodeToString(SolicitudFirma("a".repeat(64), true, "123456", "MOVIL", "Pixel 8"))

        assertEquals(
            """{"documento_sha256":"${"a".repeat(64)}","documento_revisado":true,"codigo":"123456","canal":"MOVIL","dispositivo":"Pixel 8"}""",
            cuerpo,
        )
        assertFalse(cuerpo.contains("tarjeta", ignoreCase = true))
    }
}
