package com.solventa.mobile.registro

import com.solventa.mobile.red.RegistroResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegistroViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<RegistroRepository>()

    @Before
    fun antesDeCadaPrueba() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun despuesDeCadaPrueba() {
        Dispatchers.resetMain()
    }

    private fun estadoValido(viewModel: RegistroViewModel) {
        viewModel.onNombreChange("Ana")
        viewModel.onApellidoChange("Pérez")
        viewModel.onNumeroDocumentoChange("100200300")
        viewModel.onEmailChange("ana.perez@example.com")
        viewModel.onCelularChange("3001234567")
        viewModel.onPasswordChange("Clave1234")
        viewModel.onAceptoTerminosChange(true)
    }

    @Test
    fun `enviar con datos validos y respuesta exitosa marca registroExitoso`() = runTest {
        coEvery { repository.registrar(any()) } returns RegistroRepository.Resultado.Exito(
            RegistroResponse(
                id = "1",
                nombre = "Ana",
                apellido = "Pérez",
                email = "ana.perez@example.com",
                estadoKyc = "PENDIENTE",
                accessToken = "token",
                creadoEn = "2026-10-10T00:00:00Z",
            ),
        )
        val viewModel = RegistroViewModel(repository)
        estadoValido(viewModel)

        viewModel.enviar()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.registroExitoso)
        assertFalse(viewModel.uiState.value.cargando)
    }

    @Test
    fun `enviar con campos invalidos no llama al repositorio`() = runTest {
        val viewModel = RegistroViewModel(repository)
        viewModel.onEmailChange("no-es-correo")

        viewModel.enviar()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.errores.isNotEmpty())
        assertFalse(viewModel.uiState.value.registroExitoso)
    }

    @Test
    fun `enviar sin aceptar terminos marca el error de terminos`() = runTest {
        val viewModel = RegistroViewModel(repository)
        estadoValido(viewModel)
        viewModel.onAceptoTerminosChange(false)

        viewModel.enviar()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "Debes aceptar la política de tratamiento de datos para continuar",
            viewModel.uiState.value.errores["terminos"],
        )
    }

    @Test
    fun `enviar con cuenta duplicada muestra el mensaje generico del backend`() = runTest {
        coEvery { repository.registrar(any()) } returns RegistroRepository.Resultado.Duplicado(
            "No fue posible completar el registro con los datos proporcionados",
        )
        val viewModel = RegistroViewModel(repository)
        estadoValido(viewModel)

        viewModel.enviar()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.registroExitoso)
        assertEquals(
            "No fue posible completar el registro con los datos proporcionados",
            viewModel.uiState.value.mensajeError,
        )
    }

    @Test
    fun `enviar con error de red muestra mensaje de reintento`() = runTest {
        coEvery { repository.registrar(any()) } returns RegistroRepository.Resultado.ErrorRed(
            "No hay conexión. Verifica tu red e intenta de nuevo.",
        )
        val viewModel = RegistroViewModel(repository)
        estadoValido(viewModel)

        viewModel.enviar()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "No hay conexión. Verifica tu red e intenta de nuevo.",
            viewModel.uiState.value.mensajeError,
        )
    }
}
