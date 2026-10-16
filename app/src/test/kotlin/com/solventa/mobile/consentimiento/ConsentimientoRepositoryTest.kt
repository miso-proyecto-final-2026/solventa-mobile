package com.solventa.mobile.consentimiento

import com.solventa.mobile.red.ConsentimientoApi
import com.solventa.mobile.red.ConsentimientoRequest
import com.solventa.mobile.red.ConsentimientoResponse
import com.solventa.mobile.sesion.SesionActual
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class ConsentimientoRepositoryTest {

    private val api = mockk<ConsentimientoApi>()
    private val repository = ConsentimientoRepository(api, SesionActual)

    private val otorgado = ConsentimientoResponse(
        clienteId = "c1",
        tipo = "OPEN_FINANCE_OPEN_DATA",
        estado = "OTORGADO",
        versionTexto = "v1",
        fechaOtorgamiento = "2026-10-15T10:00:00Z",
    )

    @Before
    fun abrirSesion() = SesionActual.iniciar("c1", "token-123")

    @After
    fun cerrarSesion() = SesionActual.cerrar()

    @Test
    fun `sin sesion no llama al servidor`() = runTest {
        SesionActual.cerrar()

        val resultado = repository.otorgar()

        assertEquals(ConsentimientoRepository.Resultado.SinSesion, resultado)
        coVerify(exactly = 0) { api.otorgar(any(), any(), any()) }
    }

    @Test
    fun `envia el token de la sesion, la version del texto y el canal movil`() = runTest {
        val solicitud = slot<ConsentimientoRequest>()
        coEvery { api.otorgar("c1", "Bearer token-123", capture(solicitud)) } returns
            Response.success(201, otorgado)

        val resultado = repository.otorgar()

        assertTrue(resultado is ConsentimientoRepository.Resultado.Otorgado)
        assertEquals("v1", solicitud.captured.versionTexto)
        assertEquals("MOVIL", solicitud.captured.canal)
        assertEquals("OPEN_FINANCE_OPEN_DATA", solicitud.captured.tipo)
    }

    @Test
    fun `un 200 por reintento ya otorgado tambien es exito`() = runTest {
        coEvery { api.otorgar(any(), any(), any()) } returns Response.success(200, otorgado)

        assertTrue(repository.otorgar() is ConsentimientoRepository.Resultado.Otorgado)
    }

    @Test
    fun `reintenta una vez ante un corte de conexion`() = runTest {
        coEvery { api.otorgar(any(), any(), any()) } throws IOException("reset") andThen
            Response.success(201, otorgado)

        val resultado = repository.otorgar()

        assertTrue(resultado is ConsentimientoRepository.Resultado.Otorgado)
        coVerify(exactly = 2) { api.otorgar(any(), any(), any()) }
    }

    @Test
    fun `si el reintento tambien falla informa error de red`() = runTest {
        coEvery { api.otorgar(any(), any(), any()) } throws IOException("sin red")

        val resultado = repository.otorgar()

        assertTrue(resultado is ConsentimientoRepository.Resultado.ErrorRed)
        coVerify(exactly = 2) { api.otorgar(any(), any(), any()) }
    }

    @Test
    fun `401 y 403 se tratan como sesion invalida`() = runTest {
        for (codigo in listOf(401, 403)) {
            coEvery { api.otorgar(any(), any(), any()) } returns
                Response.error(codigo, "{}".toResponseBody())

            assertEquals(ConsentimientoRepository.Resultado.SinSesion, repository.otorgar())
        }
    }

    @Test
    fun `otros codigos son error inesperado`() = runTest {
        coEvery { api.otorgar(any(), any(), any()) } returns
            Response.error(500, "{}".toResponseBody())

        assertTrue(repository.otorgar() is ConsentimientoRepository.Resultado.ErrorRed)
    }
}
