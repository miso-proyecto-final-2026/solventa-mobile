package com.solventa.mobile.notificaciones

import com.solventa.mobile.red.NotificacionResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SondeadorNotificacionesTest {

    private val repository = mockk<NotificacionesRepository>()
    private val mostradas = mutableListOf<String>()
    private var permiso = true
    private var fallaAlMostrar: Set<String> = emptySet()
    private val avisos = object : AvisosSistema {
        override fun puedeMostrar() = permiso

        override fun mostrar(notificacion: NotificacionResponse) {
            if (notificacion.id in fallaAlMostrar) error("el sistema no la pudo mostrar")
            mostradas += notificacion.id
        }
    }
    private val dispositivo = object : IdentificadorDispositivo {
        override fun obtener() = "app-dispositivo-1"
    }
    private val sondeador = SondeadorNotificaciones(repository, avisos, dispositivo)

    private fun notificacion(id: String, creada: String = "2026-10-30T20:43:22Z") =
        NotificacionResponse(id, "POLIZA_EMITIDA", "Suscripción completada", "Tu póliza ya está activa.", "Solventa", "Tienes una notificación nueva.", "ALTA", "es-CO", "s-$id", creada)

    private fun exito() = NotificacionesRepository.Resultado.Exito(Unit)

    @Before
    fun antes() {
        coEvery { repository.confirmarRecibida(any()) } returns exito()
        coEvery { repository.registrarDispositivo(any(), any()) } returns exito()
    }

    // --- registro ---------------------------------------------------------------------------------------------------

    @Test
    fun `registra el dispositivo una sola vez por cliente e idioma`() = runTest {
        sondeador.registrar("c1", "es-CO")
        sondeador.registrar("c1", "es-CO")

        coVerify(exactly = 1) { repository.registrarDispositivo("app-dispositivo-1", "es-CO") }
    }

    @Test
    fun `si cambia el idioma o el cliente en el mismo telefono se registra de nuevo`() = runTest {
        sondeador.registrar("c1", "es-CO")
        sondeador.registrar("c1", "en-US")
        sondeador.registrar("c2", "en-US")

        coVerify(exactly = 1) { repository.registrarDispositivo("app-dispositivo-1", "es-CO") }
        coVerify(exactly = 2) { repository.registrarDispositivo("app-dispositivo-1", "en-US") }
    }

    @Test
    fun `si el registro falla se reintenta en la siguiente llamada`() = runTest {
        coEvery { repository.registrarDispositivo(any(), any()) } returnsMany
            listOf(NotificacionesRepository.Resultado.SinConexion, exito())

        sondeador.registrar("c1", "es-CO")
        sondeador.registrar("c1", "es-CO")
        sondeador.registrar("c1", "es-CO")

        coVerify(exactly = 2) { repository.registrarDispositivo(any(), any()) }
    }

    // --- sondeo (criterio 2) ------------------------------------------------------------------------------------------

    @Test
    fun `muestra cada notificacion nueva y despues la confirma al servidor`() = runTest {
        coEvery { repository.nuevas() } returns NotificacionesRepository.Resultado.Exito(listOf(notificacion("n1")))

        val cuantas = sondeador.sondear()

        assertEquals(1, cuantas)
        assertEquals(listOf("n1"), mostradas)
        coVerifyOrder { repository.nuevas(); repository.confirmarRecibida("n1") }
    }

    @Test
    fun `las muestra de la mas antigua a la mas reciente`() = runTest {
        coEvery { repository.nuevas() } returns NotificacionesRepository.Resultado.Exito(
            listOf(notificacion("nueva", "2026-10-30T21:00:00Z"), notificacion("vieja", "2026-10-30T20:00:00Z")),
        )

        sondeador.sondear()

        assertEquals(listOf("vieja", "nueva"), mostradas)
    }

    @Test
    fun `sin notificaciones nuevas no muestra ni confirma nada`() = runTest {
        coEvery { repository.nuevas() } returns NotificacionesRepository.Resultado.Exito(emptyList())

        assertEquals(0, sondeador.sondear())
        coVerify(exactly = 0) { repository.confirmarRecibida(any()) }
    }

    @Test
    fun `sin permiso de notificaciones no consulta nada y las deja como nuevas para mostrarlas despues`() = runTest {
        permiso = false

        assertEquals(0, sondeador.sondear())

        coVerify(exactly = 0) { repository.nuevas() }
        coVerify(exactly = 0) { repository.confirmarRecibida(any()) }
    }

    @Test
    fun `si el sistema no puede mostrar una no la confirma pero sigue con las demas`() = runTest {
        fallaAlMostrar = setOf("n1")
        coEvery { repository.nuevas() } returns
            NotificacionesRepository.Resultado.Exito(listOf(notificacion("n1", "2026-10-30T20:00:00Z"), notificacion("n2", "2026-10-30T21:00:00Z")))

        val cuantas = sondeador.sondear()

        assertEquals(1, cuantas)
        assertEquals(listOf("n2"), mostradas)
        coVerify(exactly = 0) { repository.confirmarRecibida("n1") }
        coVerify(exactly = 1) { repository.confirmarRecibida("n2") }
    }

    @Test
    fun `si no se pudo confirmar igual cuenta como mostrada y no rompe el sondeo`() = runTest {
        coEvery { repository.nuevas() } returns NotificacionesRepository.Resultado.Exito(listOf(notificacion("n1")))
        coEvery { repository.confirmarRecibida(any()) } returns NotificacionesRepository.Resultado.SinConexion

        assertEquals(1, sondeador.sondear())
    }

    @Test
    fun `sin red o sin sesion el sondeo no muestra nada ni lanza`() = runTest {
        coEvery { repository.nuevas() } returns NotificacionesRepository.Resultado.SinConexion
        assertEquals(0, sondeador.sondear())

        coEvery { repository.nuevas() } returns NotificacionesRepository.Resultado.SinSesion
        assertEquals(0, sondeador.sondear())

        assertEquals(emptyList<String>(), mostradas)
    }
}
