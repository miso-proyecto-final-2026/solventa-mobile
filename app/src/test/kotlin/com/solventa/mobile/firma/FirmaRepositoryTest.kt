package com.solventa.mobile.firma

import com.solventa.mobile.red.DocumentoFirmaResponse
import com.solventa.mobile.red.FirmaApi
import com.solventa.mobile.red.FirmaResponse
import com.solventa.mobile.red.PolizaResponse
import com.solventa.mobile.red.SolicitudFirma
import com.solventa.mobile.sesion.SesionActual
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class FirmaRepositoryTest {

    private val api = mockk<FirmaApi>()
    private val repository = FirmaRepository(api, SesionActual)
    private val documento = DocumentoFirmaResponse("s1", "POL1", "h".repeat(64), "PENDIENTE", "2026-10-31T20:43:00Z")
    private val firma = FirmaResponse("s1", "p1", "EMITIDA", "h".repeat(64), "2026-10-30T20:43:21Z", "MOVIL", "OTP")
    private val solicitud = SolicitudFirma("h".repeat(64), true, "123456", "MOVIL", "Pixel 8")

    @Before
    fun abrirSesion() = SesionActual.iniciar("c1", "token-123")

    @After
    fun cerrarSesion() = SesionActual.cerrar()

    private fun <T> error(codigo: Int, code: String = "", motivo: String = ""): Response<T> = Response.error(
        codigo,
        """{"error":{"code":"$code","message":"x","details":[{"campo":"codigo","motivo":"$motivo"}]}}"""
            .toResponseBody("application/json".toMediaType()),
    )

    @Test
    fun `sin sesion no llama al servidor`() = runTest {
        SesionActual.cerrar()

        assertEquals(FirmaRepository.Resultado.SinSesion, repository.documento("s1"))
        assertEquals(FirmaRepository.Resultado.SinSesion, repository.firmar("s1", solicitud))
        coVerify(exactly = 0) { api.documento(any(), any()) }
    }

    @Test
    fun `el documento se pide con el token de la sesion`() = runTest {
        val autorizacion = slot<String>()
        coEvery { api.documento("s1", capture(autorizacion)) } returns Response.success(documento)

        assertEquals(FirmaRepository.Resultado.Exito(documento), repository.documento("s1"))
        assertEquals("Bearer token-123", autorizacion.captured)
    }

    @Test
    fun `la poliza sin generar todavia se distingue de un error`() = runTest {
        coEvery { api.documento(any(), any()) } returns error(409, "POLIZA_NO_LISTA")
        assertEquals(FirmaRepository.Resultado.DocumentoNoListo, repository.documento("s1"))

        coEvery { api.documento(any(), any()) } returns error(404, "POLIZA_NO_ENCONTRADA")
        assertEquals(FirmaRepository.Resultado.DocumentoNoListo, repository.documento("s1"))
    }

    @Test
    fun `cada desenlace de la firma tiene su resultado`() = runTest {
        val esperados = mapOf(
            "FIRMA_VENCIDA" to FirmaRepository.Resultado.Vencida,
            "FIRMA_FALLIDA" to FirmaRepository.Resultado.Fallida,
            "DOCUMENTO_ALTERADO" to FirmaRepository.Resultado.DocumentoAlterado,
            "FIRMA_NO_DISPONIBLE" to FirmaRepository.Resultado.FirmaNoDisponible,
        )
        esperados.forEach { (codigo, resultado) ->
            coEvery { api.firmar(any(), any(), any()) } returns error(409, codigo)
            assertEquals(codigo, resultado, repository.firmar("s1", solicitud))
        }
    }

    @Test
    fun `el codigo incorrecto trae los intentos que quedan`() = runTest {
        coEvery { api.firmar(any(), any(), any()) } returns error(403, "OTP_INVALIDO", "intentos restantes: 2")
        assertEquals(FirmaRepository.Resultado.CodigoInvalido(2), repository.firmar("s1", solicitud))

        coEvery { api.firmar(any(), any(), any()) } returns error(403, "OTP_INVALIDO", "")
        assertEquals(FirmaRepository.Resultado.CodigoInvalido(null), repository.firmar("s1", solicitud))
    }

    @Test
    fun `una sesion rechazada se distingue del codigo incorrecto`() = runTest {
        coEvery { api.firmar(any(), any(), any()) } returns error(401)
        assertEquals(FirmaRepository.Resultado.SinSesion, repository.firmar("s1", solicitud))
    }

    @Test
    fun `un fallo del servidor conserva el codigo http`() = runTest {
        coEvery { api.documento(any(), any()) } returns error(500)
        assertEquals(FirmaRepository.Resultado.ErrorServicio(500), repository.documento("s1"))
    }

    @Test
    fun `sin red al consultar el documento se informa sin conexion`() = runTest {
        coEvery { api.documento(any(), any()) } throws IOException("sin red")
        assertEquals(FirmaRepository.Resultado.SinConexion, repository.documento("s1"))
    }

    @Test
    fun `un corte al firmar se repite una vez y devuelve la firma`() = runTest {
        var llamadas = 0
        coEvery { api.firmar(any(), any(), any()) } answers {
            if (++llamadas == 1) throw IOException("corte") else Response.success(firma)
        }

        assertEquals(FirmaRepository.Resultado.Exito(firma), repository.firmar("s1", solicitud))
        assertEquals(2, llamadas)
    }

    @Test
    fun `si el reintento tambien se corta no se sabe si se firmo`() = runTest {
        coEvery { api.firmar(any(), any(), any()) } throws IOException("corte")
        assertEquals(FirmaRepository.Resultado.Desconocido, repository.firmar("s1", solicitud))
        coVerify(exactly = 2) { api.firmar(any(), any(), any()) }
    }

    @Test
    fun `un tiempo de espera al firmar no se repite porque la solicitud pudo llegar`() = runTest {
        coEvery { api.firmar(any(), any(), any()) } throws SocketTimeoutException("lento")
        assertEquals(FirmaRepository.Resultado.Desconocido, repository.firmar("s1", solicitud))
        coVerify(exactly = 1) { api.firmar(any(), any(), any()) }
    }

    @Test
    fun `el pdf se descarga completo`() = runTest {
        val contenido = "%PDF-1.4 prueba".toByteArray()
        coEvery { api.pdf(any(), any()) } returns Response.success(contenido.toResponseBody("application/pdf".toMediaType()))

        val resultado = repository.pdf("s1")

        assertTrue(resultado is FirmaRepository.Resultado.Exito)
        assertEquals("%PDF-1.4 prueba", String((resultado as FirmaRepository.Resultado.Exito).valor))
    }

    @Test
    fun `pedir el codigo acepta el 202 sin cuerpo`() = runTest {
        coEvery { api.pedirCodigo(any(), any()) } returns Response.success(202, Unit)
        assertEquals(FirmaRepository.Resultado.Exito(Unit), repository.pedirCodigo("s1"))
    }

    // --- certificado (HU22) -------------------------------------------------------------------------------------------

    @Test
    fun `la poliza se pide con el token de la sesion`() = runTest {
        val poliza = PolizaResponse("p1", "s1", "36GBQKV2CWMM3EBB8Q6Q", "microseguro_vida", "VIGENTE", "2026-10-30T20:00:00Z", "2027-10-30T20:00:00Z")
        val autorizacion = slot<String>()
        coEvery { api.poliza("s1", capture(autorizacion)) } returns Response.success(poliza)

        assertEquals(FirmaRepository.Resultado.Exito(poliza), repository.poliza("s1"))
        assertEquals("Bearer token-123", autorizacion.captured)
    }

    @Test
    fun `el certificado se descarga en el idioma que se pide`() = runTest {
        val idioma = slot<String>()
        val contenido = "%PDF-1.4 certificado".toByteArray()
        coEvery { api.certificado("s1", any(), capture(idioma)) } returns
            Response.success(contenido.toResponseBody("application/pdf".toMediaType()))

        val resultado = repository.certificado("s1", "en-US")

        assertEquals("en-US", idioma.captured)
        assertEquals("%PDF-1.4 certificado", String((resultado as FirmaRepository.Resultado.Exito).valor))
    }

    @Test
    fun `un certificado de una poliza sin emitir tiene su propio resultado`() = runTest {
        coEvery { api.certificado(any(), any(), any()) } returns error(409, "CERTIFICADO_NO_DISPONIBLE")

        assertEquals(FirmaRepository.Resultado.CertificadoNoDisponible, repository.certificado("s1", "es-CO"))
    }

    @Test
    fun `sin sesion o sin red el certificado no se descarga`() = runTest {
        coEvery { api.certificado(any(), any(), any()) } throws IOException("sin red")
        assertEquals(FirmaRepository.Resultado.SinConexion, repository.certificado("s1", "es-CO"))

        SesionActual.cerrar()
        assertEquals(FirmaRepository.Resultado.SinSesion, repository.certificado("s1", "es-CO"))
        assertEquals(FirmaRepository.Resultado.SinSesion, repository.poliza("s1"))
    }
}
