package com.solventa.mobile.polizas

import com.solventa.mobile.red.BilleteraResponse
import com.solventa.mobile.red.EnlaceCertificadoResponse
import com.solventa.mobile.red.FirmaApi
import com.solventa.mobile.red.PolizaResponse
import com.solventa.mobile.red.PolizasApi
import com.solventa.mobile.sesion.SesionActual
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class PolizasRepositoryTest {

    private val api = mockk<PolizasApi>()
    private val firmaApi = mockk<FirmaApi>()
    private val repository = PolizasRepository(api, firmaApi, SesionActual)
    private val poliza = PolizaResponse("p1", "s1", "SOL-0001", "microseguro_vida", "VIGENTE", "2026-01-01T00:00:00Z", "2027-01-01T00:00:00Z")
    private val pdf = byteArrayOf(0x25, 0x50, 0x44, 0x46)

    @Before
    fun abrirSesion() = SesionActual.iniciar("c1", "token-123")

    @After
    fun cerrarSesion() = SesionActual.cerrar()

    private fun <T> error(codigo: Int): Response<T> =
        Response.error(codigo, """{"error":{"code":"X","message":"x"}}""".toResponseBody("application/json".toMediaType()))

    private fun pdfOk() = Response.success("%PDF".toByteArray().toResponseBody("application/pdf".toMediaType()))

    @Test
    fun `sin sesion no llama al servidor`() = runTest {
        SesionActual.cerrar()

        assertEquals(PolizasRepository.Resultado.SinSesion, repository.billetera())
        assertEquals(PolizasRepository.Resultado.SinSesion, repository.enlaceCertificado("s1", "es-CO"))
        assertEquals(PolizasRepository.Resultado.SinSesion, repository.certificado("s1", "es-CO"))
        assertEquals(PolizasRepository.Resultado.SinSesion, repository.polizaFirmada("s1"))
        coVerify(exactly = 0) { api.billetera(any(), any()) }
    }

    @Test
    fun `la billetera pide las polizas del propio cliente con su token`() = runTest {
        coEvery { api.billetera("c1", "Bearer token-123") } returns Response.success(BilleteraResponse("c1", listOf(poliza)))

        val resultado = repository.billetera()

        assertEquals(PolizasRepository.Resultado.Exito(listOf(poliza)), resultado)
    }

    @Test
    fun `una billetera vacia es un exito con lista vacia`() = runTest {
        coEvery { api.billetera(any(), any()) } returns Response.success(BilleteraResponse("c1"))

        assertEquals(PolizasRepository.Resultado.Exito(emptyList<PolizaResponse>()), repository.billetera())
    }

    @Test
    fun `401 y 403 son sesion vencida, 404 no disponible y el resto error de servicio`() = runTest {
        coEvery { api.billetera(any(), any()) } returnsMany listOf(error(401), error(403), error(404), error(500), error(503))

        assertEquals(PolizasRepository.Resultado.SinSesion, repository.billetera())
        assertEquals(PolizasRepository.Resultado.SinSesion, repository.billetera())
        assertEquals(PolizasRepository.Resultado.NoDisponible, repository.billetera())
        assertEquals(PolizasRepository.Resultado.ErrorServicio(500), repository.billetera())
        assertEquals(PolizasRepository.Resultado.ErrorServicio(503), repository.billetera())
    }

    @Test
    fun `un corte de conexion o un tiempo de espera es sin conexion`() = runTest {
        coEvery { api.billetera(any(), any()) } throws IOException("sin red")
        coEvery { firmaApi.certificado(any(), any(), any()) } throws java.net.SocketTimeoutException("tarda")

        assertEquals(PolizasRepository.Resultado.SinConexion, repository.billetera())
        assertEquals(PolizasRepository.Resultado.SinConexion, repository.certificado("s1", "es-CO"))
    }

    @Test
    fun `el enlace del certificado va con el token y el idioma del telefono`() = runTest {
        val enlace = EnlaceCertificadoResponse("/v1/certificados/abc.def", "2026-11-09T12:00:00Z")
        coEvery { api.enlaceCertificado("s1", "Bearer token-123", "en-US") } returns Response.success(enlace)

        assertEquals(PolizasRepository.Resultado.Exito(enlace), repository.enlaceCertificado("s1", "en-US"))
    }

    @Test
    fun `una suscripcion ajena o sin poliza vigente es no disponible`() = runTest {
        coEvery { api.enlaceCertificado(any(), any(), any()) } returns error(404)

        assertEquals(PolizasRepository.Resultado.NoDisponible, repository.enlaceCertificado("s9", "es-CO"))
    }

    @Test
    fun `descarga el certificado y la poliza firmada como bytes`() = runTest {
        coEvery { firmaApi.certificado("s1", "Bearer token-123", "es-CO") } returns pdfOk()
        coEvery { firmaApi.polizaFirmada("s1", "Bearer token-123") } returns pdfOk()

        val certificado = repository.certificado("s1", "es-CO")
        val firmada = repository.polizaFirmada("s1")

        assertTrue(certificado is PolizasRepository.Resultado.Exito)
        assertArrayEquals("%PDF".toByteArray(), (certificado as PolizasRepository.Resultado.Exito).valor)
        assertArrayEquals("%PDF".toByteArray(), (firmada as PolizasRepository.Resultado.Exito).valor)
    }

    @Test
    fun `un documento que no existe todavia es no disponible`() = runTest {
        coEvery { firmaApi.polizaFirmada(any(), any()) } returns error(404)

        assertEquals(PolizasRepository.Resultado.NoDisponible, repository.polizaFirmada("s1"))
    }
}
