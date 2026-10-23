package com.solventa.mobile.privacidad

import com.solventa.mobile.red.ConsentimientoApi
import com.solventa.mobile.red.ConsentimientoResponse
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class PrivacidadRepositoryTest {

    private val api = mockk<ConsentimientoApi>()
    private val repository = PrivacidadRepository(api, SesionActual)

    private val otorgado = ConsentimientoResponse(
        clienteId = "c1",
        tipo = TIPO_CONSENTIMIENTO,
        estado = ESTADO_OTORGADO,
        versionTexto = "v1",
        fechaOtorgamiento = "2026-10-20T15:00:00Z",
    )
    private val revocado = otorgado.copy(estado = ESTADO_REVOCADO, fechaRevocacion = "2026-10-22T18:30:00Z")

    @Before
    fun abrirSesion() = SesionActual.iniciar("c1", "token-123")

    @After
    fun cerrarSesion() = SesionActual.cerrar()

    private fun error(codigo: Int) =
        Response.error<ConsentimientoResponse>(codigo, "{}".toResponseBody("application/json".toMediaType()))

    @Test
    fun `sin sesion ni consulta ni revoca llama al servidor`() = runTest {
        SesionActual.cerrar()

        assertEquals(PrivacidadRepository.Resultado.SinSesion, repository.consultar())
        assertEquals(PrivacidadRepository.Resultado.SinSesion, repository.revocar())
        coVerify(exactly = 0) { api.consultar(any(), any(), any()) }
        coVerify(exactly = 0) { api.revocar(any(), any(), any(), any()) }
    }

    @Test
    fun `consultar usa el cliente, el tipo y el token de la sesion`() = runTest {
        coEvery { api.consultar("c1", "OPEN_FINANCE_OPEN_DATA", "Bearer token-123") } returns Response.success(otorgado)

        val resultado = repository.consultar()

        assertEquals(PrivacidadRepository.Resultado.Exito(otorgado), resultado)
    }

    @Test
    fun `revocar envia el canal movil y devuelve el estado confirmado por el servidor`() = runTest {
        coEvery { api.revocar("c1", "OPEN_FINANCE_OPEN_DATA", "Bearer token-123", "MOVIL") } returns Response.success(revocado)

        val resultado = repository.revocar()

        assertEquals(PrivacidadRepository.Resultado.Exito(revocado), resultado)
    }

    @Test
    fun `revocar sin consentimiento previo (404) deja al cliente sin compartir nada`() = runTest {
        coEvery { api.revocar(any(), any(), any(), any()) } returns error(404)

        val resultado = repository.revocar() as PrivacidadRepository.Resultado.Exito

        assertEquals(ESTADO_NO_OTORGADO, resultado.consentimiento.estado)
        assertEquals("c1", resultado.consentimiento.clienteId)
    }

    @Test
    fun `consultar un cliente que el servidor no encuentra (404) es un error del servicio`() = runTest {
        coEvery { api.consultar(any(), any(), any()) } returns error(404)

        assertEquals(PrivacidadRepository.Resultado.ErrorServicio(404), repository.consultar())
    }

    @Test
    fun `401 y 403 significan sesion vencida`() = runTest {
        coEvery { api.revocar(any(), any(), any(), any()) } returns error(401)
        assertEquals(PrivacidadRepository.Resultado.SinSesion, repository.revocar())

        coEvery { api.revocar(any(), any(), any(), any()) } returns error(403)
        assertEquals(PrivacidadRepository.Resultado.SinSesion, repository.revocar())
    }

    @Test
    fun `un error del servidor no se confunde con una revocacion`() = runTest {
        coEvery { api.revocar(any(), any(), any(), any()) } returns error(500)

        assertEquals(PrivacidadRepository.Resultado.ErrorServicio(500), repository.revocar())
    }

    @Test
    fun `un corte de conexion se reintenta una vez y se recupera`() = runTest {
        var llamadas = 0
        coEvery { api.revocar(any(), any(), any(), any()) } answers {
            if (++llamadas == 1) throw IOException("reset") else Response.success(revocado)
        }

        val resultado = repository.revocar()

        assertEquals(PrivacidadRepository.Resultado.Exito(revocado), resultado)
        assertEquals(2, llamadas)
    }

    @Test
    fun `dos cortes seguidos son sin conexion`() = runTest {
        coEvery { api.revocar(any(), any(), any(), any()) } throws IOException("sin red")

        assertEquals(PrivacidadRepository.Resultado.SinConexion, repository.revocar())
        coVerify(exactly = 2) { api.revocar(any(), any(), any(), any()) }
    }

    @Test
    fun `superar el tiempo de espera no se reintenta solo`() = runTest {
        coEvery { api.revocar(any(), any(), any(), any()) } throws SocketTimeoutException("lento")

        assertTrue(repository.revocar() is PrivacidadRepository.Resultado.TiempoAgotado)
        coVerify(exactly = 1) { api.revocar(any(), any(), any(), any()) }
    }

    @Test
    fun `una respuesta 200 sin cuerpo no cuenta como revocada`() = runTest {
        coEvery { api.revocar(any(), any(), any(), any()) } returns Response.success<ConsentimientoResponse>(200, null)

        assertEquals(PrivacidadRepository.Resultado.ErrorServicio(200), repository.revocar())
    }
}
