package com.solventa.mobile.cotizacion

import com.solventa.mobile.red.CotizacionApi
import com.solventa.mobile.red.CotizacionResponse
import com.solventa.mobile.red.LatenciaClienteRequest
import com.solventa.mobile.red.SolicitudCotizacion
import com.solventa.mobile.sesion.SesionActual
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class CotizacionRepositoryTest {

    private val api = mockk<CotizacionApi>()
    private val repository = CotizacionRepository(api, SesionActual)
    private val datos = mapOf("edad" to JsonPrimitive(30L), "suma_asegurada" to JsonPrimitive(50_000_000L))

    private val cotizacion = CotizacionResponse(
        id = "q1",
        producto = "microseguro_vida",
        moneda = "COP",
        primaAnual = 175_000,
        primaMensual = 14_583,
        coberturas = emptyList(),
        vigenteHasta = "2026-11-17T23:49:58Z",
    )

    @Before
    fun abrirSesion() = SesionActual.iniciar("c1", "token-123")

    @After
    fun cerrarSesion() = SesionActual.cerrar()

    private fun error(codigo: Int, cuerpo: String = "{}") =
        Response.error<CotizacionResponse>(codigo, cuerpo.toResponseBody("application/json".toMediaType()))

    @Test
    fun `sin sesion no llama al servidor`() = runTest {
        SesionActual.cerrar()

        val resultado = repository.cotizar("microseguro_vida", datos)

        assertEquals(CotizacionRepository.Resultado.SinSesion, resultado)
        coVerify(exactly = 0) { api.cotizar(any()) }
    }

    @Test
    fun `envia el cliente de la sesion, el producto y los datos`() = runTest {
        val solicitud = slot<SolicitudCotizacion>()
        coEvery { api.cotizar(capture(solicitud)) } returns Response.success(201, cotizacion)

        val resultado = repository.cotizar("microseguro_vida", datos)

        assertEquals(CotizacionRepository.Resultado.Exito(cotizacion), resultado)
        assertEquals("c1", solicitud.captured.clienteId)
        assertEquals("microseguro_vida", solicitud.captured.producto)
        assertEquals(datos, solicitud.captured.datos)
    }

    @Test
    fun `un 422 devuelve el motivo de cada campo sin el prefijo datos`() = runTest {
        val cuerpo = """{"error":{"code":"VALIDATION_ERROR","message":"x","details":[
            {"campo":"datos.edad","motivo":"debe ser mayor o igual a 18"},
            {"campo":"datos.suma_asegurada","motivo":"es requerido"}]}}"""
        coEvery { api.cotizar(any()) } returns error(422, cuerpo)

        val resultado = repository.cotizar("microseguro_vida", datos)

        assertEquals(
            CotizacionRepository.Resultado.Validacion(
                mapOf("edad" to "debe ser mayor o igual a 18", "suma_asegurada" to "es requerido"),
            ),
            resultado,
        )
    }

    @Test
    fun `un 422 con cuerpo ilegible sigue siendo un error de validacion`() = runTest {
        coEvery { api.cotizar(any()) } returns error(422, "no es json")

        assertEquals(CotizacionRepository.Resultado.Validacion(emptyMap()), repository.cotizar("p", datos))
    }

    @Test
    fun `401 y 403 significan sesion vencida`() = runTest {
        coEvery { api.cotizar(any()) } returns error(401)
        assertEquals(CotizacionRepository.Resultado.SinSesion, repository.cotizar("p", datos))

        coEvery { api.cotizar(any()) } returns error(403)
        assertEquals(CotizacionRepository.Resultado.SinSesion, repository.cotizar("p", datos))
    }

    @Test
    fun `una falla del servicio conserva el codigo y no se reintenta`() = runTest {
        coEvery { api.cotizar(any()) } returns error(503)

        assertEquals(CotizacionRepository.Resultado.ErrorServicio(503), repository.cotizar("p", datos))
        coVerify(exactly = 1) { api.cotizar(any()) }
    }

    @Test
    fun `un corte de conexion se reintenta una vez de forma transparente`() = runTest {
        coEvery { api.cotizar(any()) } throws IOException("unexpected end of stream") andThen
            Response.success(201, cotizacion)

        val resultado = repository.cotizar("p", datos)

        assertEquals(CotizacionRepository.Resultado.Exito(cotizacion), resultado)
        coVerify(exactly = 2) { api.cotizar(any()) }
    }

    @Test
    fun `dos cortes seguidos terminan en sin conexion`() = runTest {
        coEvery { api.cotizar(any()) } throws IOException("Connection reset")

        assertEquals(CotizacionRepository.Resultado.SinConexion, repository.cotizar("p", datos))
        coVerify(exactly = 2) { api.cotizar(any()) }
    }

    @Test
    fun `superar el tiempo de espera no se reintenta y avisa al usuario`() = runTest {
        coEvery { api.cotizar(any()) } throws SocketTimeoutException("timeout")

        assertEquals(CotizacionRepository.Resultado.TiempoAgotado, repository.cotizar("p", datos))
        coVerify(exactly = 1) { api.cotizar(any()) }
    }

    @Test
    fun `registrar la latencia envia el tiempo observado`() = runTest {
        val cuerpo = slot<LatenciaClienteRequest>()
        coEvery { api.registrarLatencia("q1", capture(cuerpo)) } returns Response.success(Unit)

        repository.registrarLatencia("q1", 187.0)

        assertEquals(187.0, cuerpo.captured.latenciaClienteMs, 0.0)
    }

    @Test
    fun `registrar la latencia nunca falla aunque el servidor o la red fallen`() = runTest {
        coEvery { api.registrarLatencia(any(), any()) } throws IOException("sin red")
        repository.registrarLatencia("q1", 10.0)

        coEvery { api.registrarLatencia(any(), any()) } returns
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))
        repository.registrarLatencia("q1", 10.0)

        assertTrue(true)
    }
}
