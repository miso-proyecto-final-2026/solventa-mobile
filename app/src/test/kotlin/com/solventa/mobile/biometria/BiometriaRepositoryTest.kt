package com.solventa.mobile.biometria

import com.solventa.mobile.red.ActivarBiometriaRequest
import com.solventa.mobile.red.BiometriaApi
import com.solventa.mobile.red.BiometriaResponse
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

class BiometriaRepositoryTest {

    private val api = mockk<BiometriaApi>()
    private val repository = BiometriaRepository(api, SesionActual)

    private val activa = BiometriaResponse(clienteId = "c1", activo = true, metodo = "HUELLA", activadoEn = "2026-11-13T15:00:00Z")

    @Before
    fun abrirSesion() = SesionActual.iniciar("c1", "token-123")

    @After
    fun cerrarSesion() = SesionActual.cerrar()

    private fun <T> error(codigo: Int, cuerpo: String = "{}") =
        Response.error<T>(codigo, cuerpo.toResponseBody("application/json".toMediaType()))

    @Test
    fun `sin sesion no llama al servidor`() = runTest {
        SesionActual.cerrar()

        assertEquals(BiometriaRepository.Resultado.SinSesion, repository.consultar())
        assertEquals(BiometriaRepository.Resultado.SinSesion, repository.activar(MetodoBiometrico.HUELLA, "x"))
        assertEquals(BiometriaRepository.Resultado.SinSesion, repository.desactivar())
        coVerify(exactly = 0) { api.consultar(any(), any()) }
    }

    @Test
    fun `activar envia el metodo y la contrasena con el token de la sesion`() = runTest {
        coEvery { api.activar("c1", "Bearer token-123", ActivarBiometriaRequest("FACIAL", "Clave1234")) } returns Response.success(activa)

        val resultado = repository.activar(MetodoBiometrico.FACIAL, "Clave1234")

        assertEquals(BiometriaRepository.Resultado.Exito(activa), resultado)
    }

    @Test
    fun `un 403 con KYC_NO_APROBADO es KycNoAprobado`() = runTest {
        coEvery { api.activar(any(), any(), any()) } returns
            error(403, """{"detail":{"codigo":"KYC_NO_APROBADO","mensaje":"x"}}""")

        assertEquals(BiometriaRepository.Resultado.KycNoAprobado, repository.activar(MetodoBiometrico.HUELLA, "x"))
    }

    @Test
    fun `un 403 con CONTRASENA_INCORRECTA es ContrasenaIncorrecta`() = runTest {
        coEvery { api.activar(any(), any(), any()) } returns
            error(403, """{"detail":{"codigo":"CONTRASENA_INCORRECTA","mensaje":"x"}}""")

        assertEquals(BiometriaRepository.Resultado.ContrasenaIncorrecta, repository.activar(MetodoBiometrico.HUELLA, "x"))
    }

    @Test
    fun `un 403 sin codigo o un 401 es sesion vencida`() = runTest {
        coEvery { api.consultar(any(), any()) } returns error(403, """{"detail":"No autorizado para este cliente"}""")
        assertEquals(BiometriaRepository.Resultado.SinSesion, repository.consultar())

        coEvery { api.consultar(any(), any()) } returns error(401)
        assertEquals(BiometriaRepository.Resultado.SinSesion, repository.consultar())
    }

    @Test
    fun `desactivar acepta el 204 sin cuerpo`() = runTest {
        coEvery { api.desactivar("c1", "Bearer token-123") } returns Response.success(204, Unit)

        assertEquals(BiometriaRepository.Resultado.Exito(null), repository.desactivar())
    }

    @Test
    fun `otros codigos son error de servicio con su codigo`() = runTest {
        coEvery { api.consultar(any(), any()) } returns error(503)

        assertEquals(BiometriaRepository.Resultado.ErrorServicio(503), repository.consultar())
    }

    @Test
    fun `un corte de conexion se reintenta una vez y luego es sin conexion`() = runTest {
        coEvery { api.consultar(any(), any()) } throws IOException()

        assertEquals(BiometriaRepository.Resultado.SinConexion, repository.consultar())
        coVerify(exactly = 2) { api.consultar(any(), any()) }
    }

    @Test
    fun `superar el tiempo de espera no se reintenta`() = runTest {
        coEvery { api.consultar(any(), any()) } throws SocketTimeoutException()

        assertEquals(BiometriaRepository.Resultado.TiempoAgotado, repository.consultar())
        coVerify(exactly = 1) { api.consultar(any(), any()) }
    }

    @Test
    fun `el codigo de negocio se lee solo de un detail con forma de objeto`() {
        assertEquals("KYC_NO_APROBADO", codigoDeError("""{"detail":{"codigo":"KYC_NO_APROBADO"}}"""))
        assertNull(codigoDeError("""{"detail":"texto"}"""))
        assertNull(codigoDeError("no es json"))
        assertNull(codigoDeError(null))
    }
}
