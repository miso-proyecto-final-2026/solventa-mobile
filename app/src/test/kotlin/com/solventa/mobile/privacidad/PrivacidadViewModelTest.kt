package com.solventa.mobile.privacidad

import com.solventa.mobile.red.ConsentimientoResponse
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
class PrivacidadViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<PrivacidadRepository>()

    private val otorgado = ConsentimientoResponse(
        clienteId = "c1",
        tipo = TIPO_CONSENTIMIENTO,
        estado = ESTADO_OTORGADO,
        versionTexto = "v1",
        fechaOtorgamiento = "2026-10-20T15:00:00Z",
    )
    private val revocado = otorgado.copy(estado = ESTADO_REVOCADO, fechaRevocacion = "2026-10-22T18:30:00Z")
    private val sinOtorgar = ConsentimientoResponse(clienteId = "c1", tipo = TIPO_CONSENTIMIENTO, estado = ESTADO_NO_OTORGADO)

    @Before
    fun antes() = Dispatchers.setMain(dispatcher)

    @After
    fun despues() = Dispatchers.resetMain()

    private fun viewModel(inicial: PrivacidadRepository.Resultado = PrivacidadRepository.Resultado.Exito(otorgado)): PrivacidadViewModel {
        coEvery { repository.consultar() } returns inicial
        return PrivacidadViewModel(repository)
    }

    @Test
    fun `al abrir consulta el estado y lo muestra`() = runTest {
        val vm = viewModel()
        assertEquals(FasePrivacidad.CARGANDO, vm.uiState.value.fase)

        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(FasePrivacidad.ESTADO, vm.uiState.value.fase)
        assertEquals(otorgado, vm.uiState.value.consentimiento)
        assertTrue(vm.uiState.value.puedeRevocar)
        assertFalse(vm.uiState.value.puedeCompartir)
    }

    @Test
    fun `revocado o sin otorgar ofrece compartir y no revocar`() = runTest {
        for (estado in listOf(revocado, sinOtorgar)) {
            val vm = viewModel(PrivacidadRepository.Resultado.Exito(estado))
            dispatcher.scheduler.advanceUntilIdle()

            assertFalse(vm.uiState.value.puedeRevocar)
            assertTrue(vm.uiState.value.puedeCompartir)
            vm.pedirRevocar()
            assertEquals(FasePrivacidad.ESTADO, vm.uiState.value.fase)
        }
    }

    @Test
    fun `pedir revocar solo abre la confirmacion y no toca al servidor`() = runTest {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        vm.pedirRevocar()

        assertEquals(FasePrivacidad.CONFIRMANDO, vm.uiState.value.fase)
        coVerify(exactly = 0) { repository.revocar() }
    }

    @Test
    fun `cancelar la confirmacion deja el consentimiento como estaba`() = runTest {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        vm.pedirRevocar()

        vm.cancelar()

        assertEquals(FasePrivacidad.ESTADO, vm.uiState.value.fase)
        assertEquals(otorgado, vm.uiState.value.consentimiento)
        coVerify(exactly = 0) { repository.revocar() }
    }

    @Test
    fun `confirmar sin haber pedido la revocacion no hace nada`() = runTest {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        vm.confirmarRevocar()
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { repository.revocar() }
        assertEquals(otorgado, vm.uiState.value.consentimiento)
    }

    @Test
    fun `al confirmar muestra el progreso y luego el estado revocado confirmado`() = runTest {
        coEvery { repository.revocar() } returns PrivacidadRepository.Resultado.Exito(revocado)
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        vm.pedirRevocar()

        vm.confirmarRevocar()
        assertEquals(FasePrivacidad.REVOCANDO, vm.uiState.value.fase)
        assertEquals("el estado no se adelanta al servidor", otorgado, vm.uiState.value.consentimiento)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(FasePrivacidad.ESTADO, vm.uiState.value.fase)
        assertEquals(revocado, vm.uiState.value.consentimiento)
        assertTrue(vm.uiState.value.recienRevocado)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `un doble toque en confirmar revoca una sola vez`() = runTest {
        coEvery { repository.revocar() } returns PrivacidadRepository.Resultado.Exito(revocado)
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        vm.pedirRevocar()

        vm.confirmarRevocar()
        vm.confirmarRevocar()
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.revocar() }
    }

    @Test
    fun `sin confirmacion del servidor no se muestra como revocado y se ofrece reintentar`() = runTest {
        coEvery { repository.revocar() } returns PrivacidadRepository.Resultado.SinConexion
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        vm.pedirRevocar()

        vm.confirmarRevocar()
        dispatcher.scheduler.advanceUntilIdle()

        val estado = vm.uiState.value
        assertEquals(otorgado, estado.consentimiento)
        assertTrue(estado.puedeRevocar)
        assertFalse(estado.recienRevocado)
        assertEquals(ErrorPrivacidad.SIN_CONEXION, estado.error)
        assertEquals(AccionPrivacidad.REVOCAR, estado.pendiente)
    }

    @Test
    fun `cada falla tiene su propio error`() = runTest {
        val casos = mapOf(
            PrivacidadRepository.Resultado.SinConexion to ErrorPrivacidad.SIN_CONEXION,
            PrivacidadRepository.Resultado.TiempoAgotado to ErrorPrivacidad.TIEMPO_AGOTADO,
            PrivacidadRepository.Resultado.SinSesion to ErrorPrivacidad.SESION_VENCIDA,
            PrivacidadRepository.Resultado.ErrorServicio(500) to ErrorPrivacidad.SERVICIO,
        )
        for ((resultado, esperado) in casos) {
            coEvery { repository.revocar() } returns resultado
            val vm = viewModel()
            dispatcher.scheduler.advanceUntilIdle()
            vm.pedirRevocar()
            vm.confirmarRevocar()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(esperado, vm.uiState.value.error)
        }
    }

    @Test
    fun `reintentar la revocacion no vuelve a pedir confirmacion y funciona`() = runTest {
        coEvery { repository.revocar() } returnsMany listOf(
            PrivacidadRepository.Resultado.SinConexion,
            PrivacidadRepository.Resultado.Exito(revocado),
        )
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        vm.pedirRevocar()
        vm.confirmarRevocar()
        dispatcher.scheduler.advanceUntilIdle()

        vm.reintentar()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(revocado, vm.uiState.value.consentimiento)
        assertNull(vm.uiState.value.error)
        assertNull(vm.uiState.value.pendiente)
        coVerify(exactly = 2) { repository.revocar() }
    }

    @Test
    fun `si falla la consulta inicial no hay estado y reintentar la vuelve a pedir`() = runTest {
        coEvery { repository.consultar() } returnsMany listOf(
            PrivacidadRepository.Resultado.TiempoAgotado,
            PrivacidadRepository.Resultado.Exito(otorgado),
        )
        val vm = PrivacidadViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.uiState.value.consentimiento)
        assertEquals(ErrorPrivacidad.TIEMPO_AGOTADO, vm.uiState.value.error)
        assertEquals(AccionPrivacidad.CONSULTAR, vm.uiState.value.pendiente)

        vm.reintentar()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(otorgado, vm.uiState.value.consentimiento)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `reintentar sin nada pendiente no hace nada`() = runTest {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        vm.reintentar()
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.consultar() }
        coVerify(exactly = 0) { repository.revocar() }
    }

    @Test
    fun `revocar sin consentimiento previo termina sin compartir y sin aviso de exito`() = runTest {
        coEvery { repository.revocar() } returns PrivacidadRepository.Resultado.Exito(sinOtorgar)
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        vm.pedirRevocar()

        vm.confirmarRevocar()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(sinOtorgar, vm.uiState.value.consentimiento)
        assertFalse(vm.uiState.value.recienRevocado)
        assertTrue(vm.uiState.value.puedeCompartir)
    }
}
