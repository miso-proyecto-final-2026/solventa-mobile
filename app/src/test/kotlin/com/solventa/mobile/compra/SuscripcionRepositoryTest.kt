package com.solventa.mobile.compra

import com.solventa.mobile.red.SolicitudSuscripcion
import com.solventa.mobile.red.SuscripcionApi
import com.solventa.mobile.red.SuscripcionResponse
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

class SuscripcionRepositoryTest {

    private val api = mockk<SuscripcionApi>()
    private val repository = SuscripcionRepository(api, SesionActual)

    private val saga = SuscripcionResponse(
        id = "s1",
        cotizacionId = "q1",
        estadoSaga = "EN_PROGRESO",
        pasoActual = "DECISION",
    )

    @Before
    fun abrirSesion() = SesionActual.iniciar("c1", "token-123")

    @After
    fun cerrarSesion() = SesionActual.cerrar()

    private fun error(codigo: Int, code: String = "") = Response.error<SuscripcionResponse>(
        codigo,
        """{"error":{"code":"$code","message":"x","details":[]}}""".toResponseBody("application/json".toMediaType()),
    )

    @Test
    fun `sin sesion no llama al servidor`() = runTest {
        SesionActual.cerrar()

        val resultado = repository.iniciar("q1", "tok_1", "clave-1")

        assertEquals(SuscripcionRepository.Resultado.SinSesion, resultado)
        coVerify(exactly = 0) { api.iniciar(any(), any(), any()) }
    }

    @Test
    fun `envia el token y la clave de idempotencia, nunca datos de tarjeta`() = runTest {
        val solicitud = slot<SolicitudSuscripcion>()
        val clave = slot<String>()
        val autorizacion = slot<String>()
        coEvery { api.iniciar(capture(autorizacion), capture(clave), capture(solicitud)) } returns Response.success(202, saga)

        val resultado = repository.iniciar("q1", "tok_1", "clave-1")

        assertEquals(SuscripcionRepository.Resultado.Exito(saga), resultado)
        assertEquals("Bearer token-123", autorizacion.captured)
        assertEquals("clave-1", clave.captured)
        assertEquals(SolicitudSuscripcion("q1", "tok_1", aceptaTerminos = true), solicitud.captured)
    }

    @Test
    fun `un corte de conexion se reintenta una vez con la misma clave`() = runTest {
        val claves = mutableListOf<String>()
        var llamadas = 0
        coEvery { api.iniciar(any(), capture(claves), any()) } answers {
            if (llamadas++ == 0) throw IOException("unexpected end of stream") else Response.success(200, saga)
        }

        val resultado = repository.iniciar("q1", "tok_1", "clave-1")

        assertEquals(SuscripcionRepository.Resultado.Exito(saga), resultado)
        assertEquals(listOf("clave-1", "clave-1"), claves)
    }

    @Test
    fun `si el reintento tambien se corta el resultado es desconocido`() = runTest {
        coEvery { api.iniciar(any(), any(), any()) } throws IOException("reset")

        val resultado = repository.iniciar("q1", "tok_1", "clave-1")

        assertEquals(SuscripcionRepository.Resultado.ResultadoDesconocido, resultado)
        coVerify(exactly = 2) { api.iniciar(any(), any(), any()) }
    }

    @Test
    fun `superar el tiempo de espera no se reintenta y deja el resultado desconocido`() = runTest {
        coEvery { api.iniciar(any(), any(), any()) } throws SocketTimeoutException("timeout")

        val resultado = repository.iniciar("q1", "tok_1", "clave-1")

        assertEquals(SuscripcionRepository.Resultado.ResultadoDesconocido, resultado)
        coVerify(exactly = 1) { api.iniciar(any(), any(), any()) }
    }

    @Test
    fun `los errores del servidor se traducen por su codigo`() = runTest {
        val casos = mapOf(
            (409 to "COTIZACION_VENCIDA") to SuscripcionRepository.Resultado.OfertaVencida,
            (409 to "COTIZACION_YA_USADA") to SuscripcionRepository.Resultado.OfertaYaUsada,
            (404 to "COTIZACION_NO_ENCONTRADA") to SuscripcionRepository.Resultado.OfertaVencida,
            (401 to "NO_AUTENTICADO") to SuscripcionRepository.Resultado.SinSesion,
            (503 to "DEPENDENCIA_NO_DISPONIBLE") to SuscripcionRepository.Resultado.ServicioNoDisponible,
        )
        casos.forEach { (entrada, esperado) ->
            coEvery { api.iniciar(any(), any(), any()) } returns error(entrada.first, entrada.second)

            assertEquals("$entrada", esperado, repository.iniciar("q1", "tok_1", "clave-1"))
        }
    }

    @Test
    fun `un error desconocido conserva el codigo http`() = runTest {
        coEvery { api.iniciar(any(), any(), any()) } returns error(500)

        assertEquals(SuscripcionRepository.Resultado.ErrorServicio(500), repository.iniciar("q1", "tok_1", "clave-1"))
    }

    @Test
    fun `consultar devuelve el estado de la saga`() = runTest {
        coEvery { api.consultar("s1", "Bearer token-123") } returns Response.success(saga)

        assertEquals(SuscripcionRepository.Resultado.Exito(saga), repository.consultar("s1"))
    }

    @Test
    fun `consultar no reintenta solo ante un corte`() = runTest {
        coEvery { api.consultar(any(), any()) } throws IOException("reset")

        assertTrue(repository.consultar("s1") is SuscripcionRepository.Resultado.SinConexion)
    }
}
