package com.solventa.mobile.registro

import com.solventa.mobile.red.ClienteApi
import com.solventa.mobile.red.RegistroResponse
import com.solventa.mobile.sesion.SesionActual
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

class RegistroRepositoryTest {

    private val api = mockk<ClienteApi>()
    private val repository = RegistroRepository(api, SesionActual)

    @After
    fun cerrar() = SesionActual.cerrar()

    @Test
    fun `al registrarse la sesion queda abierta con el nombre del cliente`() = runTest {
        val respuesta = RegistroResponse("c1", "Ana María", "Rojas", "ana@example.com", "PENDIENTE", "token-123", "2026-10-25T10:00:00Z")
        coEvery { api.registrar(any(), any()) } returns Response.success(201, respuesta)

        repository.registrar(RegistroUiState().aSolicitud())

        assertEquals("c1", SesionActual.clienteId)
        assertEquals("Ana María", SesionActual.nombre)
    }
}
