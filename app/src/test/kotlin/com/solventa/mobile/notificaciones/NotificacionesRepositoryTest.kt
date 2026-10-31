package com.solventa.mobile.notificaciones

import com.solventa.mobile.red.NotificacionResponse
import com.solventa.mobile.red.NotificacionesApi
import com.solventa.mobile.red.SolicitudDispositivo
import com.solventa.mobile.sesion.SesionActual
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class NotificacionesRepositoryTest {

    private val api = mockk<NotificacionesApi>()
    private val repository = NotificacionesRepository(api, SesionActual)
    private val notificacion = NotificacionResponse("n1", "POLIZA_EMITIDA", "t", "x", "Solventa", "Hay algo nuevo", "ALTA", "es-CO", "s1", "2026-10-30T20:43:22Z")

    @Before
    fun abrirSesion() = SesionActual.iniciar("c1", "token-123")

    @After
    fun cerrarSesion() = SesionActual.cerrar()

    private fun <T> error(codigo: Int): Response<T> = Response.error(codigo, "{}".toResponseBody("application/json".toMediaType()))

    @Test
    fun `sin sesion no llama al servidor`() = runTest {
        SesionActual.cerrar()

        assertEquals(NotificacionesRepository.Resultado.SinSesion, repository.nuevas())
        assertEquals(NotificacionesRepository.Resultado.SinSesion, repository.registrarDispositivo("app-1", "es-CO"))
        coVerify(exactly = 0) { api.nuevas(any(), any()) }
    }

    @Test
    fun `registra el dispositivo con el token de la sesion y el idioma`() = runTest {
        val autorizacion = slot<String>()
        val solicitud = slot<SolicitudDispositivo>()
        coEvery { api.registrarDispositivo(capture(autorizacion), capture(solicitud)) } returns
            Response.success(com.solventa.mobile.red.DispositivoResponse("app-1", "en-US"))

        val resultado = repository.registrarDispositivo("app-1", "en-US")

        assertEquals(NotificacionesRepository.Resultado.Exito(Unit), resultado)
        assertEquals("Bearer token-123", autorizacion.captured)
        assertEquals(SolicitudDispositivo("app-1", "en-US"), solicitud.captured)
    }

    @Test
    fun `pide solo las nuevas`() = runTest {
        coEvery { api.nuevas(any(), true) } returns Response.success(listOf(notificacion))

        assertEquals(NotificacionesRepository.Resultado.Exito(listOf(notificacion)), repository.nuevas())
    }

    @Test
    fun `confirmar acepta el 204 sin cuerpo`() = runTest {
        coEvery { api.confirmarRecibida("n1", any()) } returns Response.success(204, Unit)

        assertEquals(NotificacionesRepository.Resultado.Exito(Unit), repository.confirmarRecibida("n1"))
    }

    @Test
    fun `una sesion rechazada se distingue de un error del servidor`() = runTest {
        coEvery { api.nuevas(any(), any()) } returns error(401)
        assertEquals(NotificacionesRepository.Resultado.SinSesion, repository.nuevas())

        coEvery { api.nuevas(any(), any()) } returns error(500)
        assertEquals(NotificacionesRepository.Resultado.Error(500), repository.nuevas())
    }

    @Test
    fun `sin red se informa sin conexion y no se lanza`() = runTest {
        coEvery { api.nuevas(any(), any()) } throws IOException("sin red")

        assertEquals(NotificacionesRepository.Resultado.SinConexion, repository.nuevas())
    }
}
