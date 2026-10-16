package com.solventa.mobile.consentimiento

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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConsentimientoViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<ConsentimientoRepository>()

    @Before
    fun antes() = Dispatchers.setMain(dispatcher)

    @After
    fun despues() = Dispatchers.resetMain()

    private fun otorgado() = ConsentimientoRepository.Resultado.Otorgado(
        ConsentimientoResponse(
            clienteId = "c1",
            tipo = "OPEN_FINANCE_OPEN_DATA",
            estado = "OTORGADO",
            versionTexto = "v1",
            fechaOtorgamiento = "2026-10-15T10:00:00Z",
        ),
    )

    @Test
    fun `al iniciar no hay decision: la aceptacion nunca es implicita`() {
        val estado = ConsentimientoViewModel(repository).uiState.value

        assertNull(estado.decision)
        assertFalse(estado.continuar)
        assertFalse(estado.cargando)
    }

    @Test
    fun `aceptar registra el consentimiento con version y fecha`() = runTest {
        coEvery { repository.otorgar() } returns otorgado()
        val viewModel = ConsentimientoViewModel(repository)

        viewModel.aceptar()
        advanceUntilIdle()

        assertEquals(
            DecisionConsentimiento.Otorgado("v1", "2026-10-15T10:00:00Z"),
            viewModel.uiState.value.decision,
        )
        assertFalse(viewModel.uiState.value.cargando)
    }

    @Test
    fun `rechazar no llama al servidor y deja el consentimiento sin otorgar`() = runTest {
        val viewModel = ConsentimientoViewModel(repository)

        viewModel.rechazar()
        advanceUntilIdle()

        assertEquals(DecisionConsentimiento.Rechazado, viewModel.uiState.value.decision)
        coVerify(exactly = 0) { repository.otorgar() }
    }

    @Test
    fun `tras rechazar se puede continuar sin compartir datos`() = runTest {
        val viewModel = ConsentimientoViewModel(repository)

        viewModel.rechazar()
        viewModel.continuar()

        assertTrue(viewModel.uiState.value.continuar)
    }

    @Test
    fun `un error de red muestra el mensaje y permite reintentar sin decision`() = runTest {
        coEvery { repository.otorgar() } returns
            ConsentimientoRepository.Resultado.ErrorRed("No hay conexión.") andThen otorgado()
        val viewModel = ConsentimientoViewModel(repository)

        viewModel.aceptar()
        advanceUntilIdle()
        assertEquals("No hay conexión.", viewModel.uiState.value.mensajeError)
        assertNull(viewModel.uiState.value.decision)
        assertFalse(viewModel.uiState.value.cargando)

        viewModel.aceptar()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.decision)
        assertNull(viewModel.uiState.value.mensajeError)
    }

    @Test
    fun `sin sesion informa el problema y no otorga`() = runTest {
        coEvery { repository.otorgar() } returns ConsentimientoRepository.Resultado.SinSesion
        val viewModel = ConsentimientoViewModel(repository)

        viewModel.aceptar()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.mensajeError)
        assertNull(viewModel.uiState.value.decision)
    }

    @Test
    fun `un doble toque en aceptar envia una sola solicitud`() = runTest {
        coEvery { repository.otorgar() } returns otorgado()
        val viewModel = ConsentimientoViewModel(repository)

        viewModel.aceptar()
        viewModel.aceptar()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.otorgar() }
    }

    @Test
    fun `una vez decidido no se puede cambiar la decision en la misma pantalla`() = runTest {
        coEvery { repository.otorgar() } returns otorgado()
        val viewModel = ConsentimientoViewModel(repository)
        viewModel.aceptar()
        advanceUntilIdle()

        viewModel.rechazar()

        assertTrue(viewModel.uiState.value.decision is DecisionConsentimiento.Otorgado)
    }

    @Test
    fun `alternar el detalle lo expande y lo oculta sin decidir nada`() {
        val viewModel = ConsentimientoViewModel(repository)

        viewModel.alternarDetalle()
        assertTrue(viewModel.uiState.value.detalleExpandido)
        viewModel.alternarDetalle()

        assertFalse(viewModel.uiState.value.detalleExpandido)
        assertNull(viewModel.uiState.value.decision)
    }
}
