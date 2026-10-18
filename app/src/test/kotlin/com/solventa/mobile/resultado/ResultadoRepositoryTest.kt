package com.solventa.mobile.resultado

import com.solventa.mobile.red.ConsentimientoApi
import com.solventa.mobile.red.ConsentimientoResponse
import com.solventa.mobile.red.EstadoKycResponse
import com.solventa.mobile.red.VerificacionApi
import com.solventa.mobile.resultado.ResultadoRepository.Consulta
import com.solventa.mobile.sesion.SesionActual
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class ResultadoRepositoryTest {

    private val verificacionApi = mockk<VerificacionApi>()
    private val consentimientoApi = mockk<ConsentimientoApi>()
    private val repository = ResultadoRepository(verificacionApi, consentimientoApi, SesionActual)

    private val verificado = EstadoKycResponse("c1", "VERIFICADO", null, 3, null, "v1")

    private fun consentimiento(estado: String) = ConsentimientoResponse("c1", "OPEN_FINANCE_OPEN_DATA", estado)

    private fun <T> error(codigo: Int): Response<T> = Response.error(codigo, "{}".toResponseBody())

    @Before
    fun abrirSesion() = SesionActual.iniciar("c1", "token-123")

    @After
    fun cerrarSesion() = SesionActual.cerrar()

    @Test
    fun `consulta el estado con el token de la sesion`() = runTest {
        coEvery { verificacionApi.consultarEstado("c1", "Bearer token-123") } returns Response.success(verificado)

        assertEquals(Consulta.Exito(verificado), repository.estado())
    }

    @Test
    fun `sin sesion no llama al servidor`() = runTest {
        SesionActual.cerrar()

        assertEquals(Consulta.SinSesion, repository.estado())
        coVerify(exactly = 0) { verificacionApi.consultarEstado(any(), any()) }
    }

    @Test
    fun `un corte de conexion se reintenta una vez`() = runTest {
        coEvery { verificacionApi.consultarEstado(any(), any()) } throws IOException("corte") andThen
            Response.success(verificado)

        assertEquals(Consulta.Exito(verificado), repository.estado())
        coVerify(exactly = 2) { verificacionApi.consultarEstado(any(), any()) }
    }

    @Test
    fun `dos cortes seguidos son sin conexion`() = runTest {
        coEvery { verificacionApi.consultarEstado(any(), any()) } throws IOException("corte")

        assertEquals(Consulta.Fallo(ErrorConsulta.SIN_CONEXION), repository.estado())
    }

    @Test
    fun `token invalido o cliente inexistente invalidan la sesion`() = runTest {
        for (codigo in listOf(401, 403, 404)) {
            coEvery { verificacionApi.consultarEstado(any(), any()) } returns error(codigo)
            assertEquals("código $codigo", Consulta.SinSesion, repository.estado())
        }
    }

    @Test
    fun `un error del servidor es inesperado`() = runTest {
        coEvery { verificacionApi.consultarEstado(any(), any()) } returns error(500)

        assertEquals(Consulta.Fallo(ErrorConsulta.INESPERADO), repository.estado())
    }

    @Test
    fun `el consentimiento solo cuenta si esta otorgado`() = runTest {
        coEvery { consentimientoApi.consultar("c1", "OPEN_FINANCE_OPEN_DATA", "Bearer token-123") } returnsMany listOf(
            Response.success(consentimiento("OTORGADO")),
            Response.success(consentimiento("NO_OTORGADO")),
            Response.success(consentimiento("REVOCADO")),
        )

        assertEquals(Consulta.Exito(true), repository.consentimientoOtorgado())
        assertEquals(Consulta.Exito(false), repository.consentimientoOtorgado())
        assertEquals(Consulta.Exito(false), repository.consentimientoOtorgado())
    }

    @Test
    fun `los errores del consentimiento se propagan`() = runTest {
        coEvery { consentimientoApi.consultar(any(), any(), any()) } returns error(401)

        assertEquals(Consulta.SinSesion, repository.consentimientoOtorgado())
    }
}
