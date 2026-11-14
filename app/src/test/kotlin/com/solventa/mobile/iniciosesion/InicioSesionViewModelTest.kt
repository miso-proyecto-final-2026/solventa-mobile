package com.solventa.mobile.iniciosesion

import com.solventa.mobile.red.RegistroResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InicioSesionViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<InicioSesionRepository>()
    private val respuesta = RegistroResponse("c1", "Ana", "Pérez", "ana@example.com", "VERIFICADO", "t", "2026-11-13T12:00:00Z")

    @Before
    fun antes() = Dispatchers.setMain(dispatcher)

    @After
    fun despues() = Dispatchers.resetMain()

    private fun vm(email: String = "") = InicioSesionViewModel(email, repository)

    @Test
    fun `el correo guardado llega prellenado`() {
        assertEquals("ana@example.com", vm("ana@example.com").uiState.value.email)
    }

    @Test
    fun `con campos vacios no llama al servidor`() = runTest(dispatcher) {
        val vm = vm()
        vm.onEmailChange("ana@example.com")

        vm.enviar()
        advanceUntilIdle()

        assertEquals(ErrorInicioSesion.CAMPOS_VACIOS, vm.uiState.value.error)
        coVerify(exactly = 0) { repository.iniciar(any(), any()) }
    }

    @Test
    fun `entrar con credenciales correctas inicia la sesion y no conserva la contrasena`() = runTest(dispatcher) {
        coEvery { repository.iniciar("ana@example.com", "Clave1234") } returns InicioSesionRepository.Resultado.Exito(respuesta)
        val vm = vm("  ana@example.com ")
        vm.onPasswordChange("Clave1234")

        vm.enviar()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.sesionIniciada)
        assertEquals("", vm.uiState.value.password)
        assertFalse(vm.uiState.value.cargando)
    }

    @Test
    fun `credenciales incorrectas muestran un mensaje que no dice cual de las dos falla`() = runTest(dispatcher) {
        coEvery { repository.iniciar(any(), any()) } returns InicioSesionRepository.Resultado.CredencialesInvalidas
        val vm = vm("ana@example.com")
        vm.onPasswordChange("mala")

        vm.enviar()
        advanceUntilIdle()

        assertEquals(ErrorInicioSesion.CREDENCIALES, vm.uiState.value.error)
        assertFalse(vm.uiState.value.sesionIniciada)
        assertEquals("mala", vm.uiState.value.password) // se conserva para corregirla
    }

    @Test
    fun `el bloqueo informa los minutos redondeados hacia arriba`() = runTest(dispatcher) {
        coEvery { repository.iniciar(any(), any()) } returns InicioSesionRepository.Resultado.Bloqueado(61)
        val vm = vm("ana@example.com")
        vm.onPasswordChange("x")

        vm.enviar()
        advanceUntilIdle()

        assertEquals(ErrorInicioSesion.BLOQUEADO, vm.uiState.value.error)
        assertEquals(2, vm.uiState.value.minutosBloqueo)
    }

    @Test
    fun `un bloqueo de pocos segundos se muestra como un minuto`() = runTest(dispatcher) {
        coEvery { repository.iniciar(any(), any()) } returns InicioSesionRepository.Resultado.Bloqueado(5)
        val vm = vm("ana@example.com")
        vm.onPasswordChange("x")

        vm.enviar()
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.minutosBloqueo)
    }

    @Test
    fun `cada falla de red tiene su mensaje`() = runTest(dispatcher) {
        val casos = mapOf(
            InicioSesionRepository.Resultado.SinConexion to ErrorInicioSesion.SIN_CONEXION,
            InicioSesionRepository.Resultado.TiempoAgotado to ErrorInicioSesion.TIEMPO_AGOTADO,
            InicioSesionRepository.Resultado.ErrorServicio(500) to ErrorInicioSesion.SERVICIO,
        )
        casos.forEach { (resultado, esperado) ->
            coEvery { repository.iniciar(any(), any()) } returns resultado
            val vm = vm("ana@example.com")
            vm.onPasswordChange("x")

            vm.enviar()
            advanceUntilIdle()

            assertEquals("$resultado", esperado, vm.uiState.value.error)
        }
    }

    @Test
    fun `escribir despues de un error lo quita`() = runTest(dispatcher) {
        coEvery { repository.iniciar(any(), any()) } returns InicioSesionRepository.Resultado.CredencialesInvalidas
        val vm = vm("ana@example.com")
        vm.onPasswordChange("mala")
        vm.enviar()
        advanceUntilIdle()

        vm.onPasswordChange("otra")

        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `no se envia dos veces mientras carga`() = runTest(dispatcher) {
        coEvery { repository.iniciar(any(), any()) } returns InicioSesionRepository.Resultado.Exito(respuesta)
        val vm = vm("ana@example.com")
        vm.onPasswordChange("Clave1234")

        vm.enviar()
        vm.enviar()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.iniciar(any(), any()) }
    }
}
