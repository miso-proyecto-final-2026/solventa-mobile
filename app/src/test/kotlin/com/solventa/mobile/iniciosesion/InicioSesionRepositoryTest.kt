package com.solventa.mobile.iniciosesion

import com.solventa.mobile.red.ClienteApi
import com.solventa.mobile.red.InicioSesionRequest
import com.solventa.mobile.red.RegistroResponse
import com.solventa.mobile.sesion.SesionActual
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class InicioSesionRepositoryTest {

    private val api = mockk<ClienteApi>()
    private val repository = InicioSesionRepository(api, SesionActual)

    private val respuesta = RegistroResponse(
        id = "c1", nombre = "Ana", apellido = "Pérez", email = "ana@example.com", estadoKyc = "VERIFICADO",
        accessToken = "token-nuevo", creadoEn = "2026-11-13T12:00:00Z",
    )

    @Before
    fun limpiar() = SesionActual.cerrar()

    @After
    fun cerrar() = SesionActual.cerrar()

    private fun error(codigo: Int, cabeceras: Map<String, String> = emptyMap()): Response<RegistroResponse> {
        val cuerpo = "{}".toResponseBody("application/json".toMediaType())
        val cruda = okhttp3.Response.Builder()
            .request(okhttp3.Request.Builder().url("http://localhost/").build())
            .protocol(okhttp3.Protocol.HTTP_1_1).code(codigo).message("x")
            .apply { cabeceras.forEach { (k, v) -> header(k, v) } }
            .build()
        return Response.error(cuerpo, cruda)
    }

    @Test
    fun `con credenciales correctas abre la sesion con el token nuevo y el correo`() = runTest {
        coEvery { api.iniciarSesion(InicioSesionRequest("ana@example.com", "Clave1234")) } returns Response.success(respuesta)

        val resultado = repository.iniciar("ana@example.com", "Clave1234")

        assertEquals(InicioSesionRepository.Resultado.Exito(respuesta), resultado)
        assertEquals("token-nuevo", SesionActual.accessToken)
        assertEquals("ana@example.com", SesionActual.email)
        assertEquals("Ana", SesionActual.nombre)
    }

    @Test
    fun `un 401 son credenciales invalidas y no abre sesion`() = runTest {
        coEvery { api.iniciarSesion(any()) } returns error(401)

        assertEquals(InicioSesionRepository.Resultado.CredencialesInvalidas, repository.iniciar("a@b.co", "x"))
        assertNull(SesionActual.accessToken)
    }

    @Test
    fun `un 429 es bloqueado con los segundos de Retry-After`() = runTest {
        coEvery { api.iniciarSesion(any()) } returns error(429, mapOf("Retry-After" to "840"))

        assertEquals(InicioSesionRepository.Resultado.Bloqueado(840), repository.iniciar("a@b.co", "x"))
    }

    @Test
    fun `un 429 sin cabecera asume la ventana completa`() = runTest {
        coEvery { api.iniciarSesion(any()) } returns error(429)

        assertEquals(InicioSesionRepository.Resultado.Bloqueado(BLOQUEO_POR_DEFECTO_S), repository.iniciar("a@b.co", "x"))
    }

    @Test
    fun `otros codigos son error de servicio`() = runTest {
        coEvery { api.iniciarSesion(any()) } returns error(503)

        assertEquals(InicioSesionRepository.Resultado.ErrorServicio(503), repository.iniciar("a@b.co", "x"))
    }

    @Test
    fun `un corte de conexion no se reintenta porque cada intento cuenta para el limite`() = runTest {
        coEvery { api.iniciarSesion(any()) } throws IOException()

        assertEquals(InicioSesionRepository.Resultado.SinConexion, repository.iniciar("a@b.co", "x"))
        coVerify(exactly = 1) { api.iniciarSesion(any()) }
    }

    @Test
    fun `superar el tiempo de espera es tiempo agotado`() = runTest {
        coEvery { api.iniciarSesion(any()) } throws SocketTimeoutException()

        assertEquals(InicioSesionRepository.Resultado.TiempoAgotado, repository.iniciar("a@b.co", "x"))
    }
}
