package com.solventa.mobile.resultado

import com.solventa.mobile.red.EstadoKycResponse
import com.solventa.mobile.resultado.ResultadoRepository.Consulta
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResultadoViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<ResultadoRepository>()
    private var sesionesPerdidas = 0

    @Before
    fun antes() = Dispatchers.setMain(dispatcher)

    @After
    fun despues() = Dispatchers.resetMain()

    private fun viewModel() = ResultadoViewModel(repository, alPerderSesion = { sesionesPerdidas++ })

    private fun estado(estadoKyc: String, intentos: Int = 3, bloqueadoHasta: String? = null) =
        Consulta.Exito(EstadoKycResponse("c1", estadoKyc, null, intentos, bloqueadoHasta, null))

    private fun mostrando(vm: ResultadoViewModel) = vm.uiState.value as ResultadoUiState.Mostrando

    @Test
    fun `empieza cargando y muestra el resultado que devuelve el servidor`() = runTest {
        coEvery { repository.estado() } returns estado("VERIFICADO")
        val vm = viewModel()

        assertEquals(ResultadoUiState.Cargando, vm.uiState.value)
        vm.actualizar()
        advanceUntilIdle()

        assertEquals(PantallaResultado.Aprobada, mostrando(vm).pantalla)
    }

    // -- criterio 1: aprobada → consentimiento o paso siguiente --------------------------------

    @Test
    fun `continuar sin consentimiento lleva al consentimiento y luego al inicio`() = runTest {
        coEvery { repository.estado() } returns estado("VERIFICADO")
        coEvery { repository.consentimientoOtorgado() } returns Consulta.Exito(false)
        val vm = viewModel()
        vm.actualizar()
        advanceUntilIdle()

        vm.continuar()
        advanceUntilIdle()

        assertEquals(Destino.CONSENTIMIENTO_TRAS_APROBAR, vm.destino.value)
    }

    @Test
    fun `continuar con el consentimiento ya otorgado lleva al inicio`() = runTest {
        coEvery { repository.estado() } returns estado("VERIFICADO")
        coEvery { repository.consentimientoOtorgado() } returns Consulta.Exito(true)
        val vm = viewModel()
        vm.actualizar()
        advanceUntilIdle()

        vm.continuar()
        advanceUntilIdle()

        assertEquals(Destino.INICIO, vm.destino.value)
        assertTrue(!mostrando(vm).procesando)
    }

    @Test
    fun `si falla la consulta del consentimiento se queda en la pantalla con el error`() = runTest {
        coEvery { repository.estado() } returns estado("VERIFICADO")
        coEvery { repository.consentimientoOtorgado() } returns Consulta.Fallo(ErrorConsulta.SIN_CONEXION)
        val vm = viewModel()
        vm.actualizar()
        advanceUntilIdle()

        vm.continuar()
        advanceUntilIdle()

        assertNull(vm.destino.value)
        assertEquals(ErrorConsulta.SIN_CONEXION, mostrando(vm).errorAccion)
        assertEquals(PantallaResultado.Aprobada, mostrando(vm).pantalla)
    }

    @Test
    fun `continuar solo aplica a una verificacion aprobada`() = runTest {
        coEvery { repository.estado() } returns estado("EN_REVISION")
        val vm = viewModel()
        vm.actualizar()
        advanceUntilIdle()

        vm.continuar()
        advanceUntilIdle()

        assertNull(vm.destino.value)
        coVerify(exactly = 0) { repository.consentimientoOtorgado() }
    }

    // -- criterios 2 y 3: reintentos -----------------------------------------------------------

    @Test
    fun `reintentar con intentos disponibles vuelve a la verificacion`() = runTest {
        coEvery { repository.estado() } returns estado("RECHAZADO", intentos = 2)
        val vm = viewModel()
        vm.actualizar()
        advanceUntilIdle()

        vm.reintentar()

        assertEquals(Destino.VERIFICACION, vm.destino.value)
    }

    @Test
    fun `sin intentos no se puede reintentar aunque se invoque`() = runTest {
        coEvery { repository.estado() } returns estado("RECHAZADO", intentos = 0, bloqueadoHasta = "2026-10-16T12:30:00Z")
        val vm = viewModel()
        vm.actualizar()
        advanceUntilIdle()

        vm.reintentar()

        assertTrue(mostrando(vm).pantalla is PantallaResultado.RechazadaSinReintentos)
        assertNull(vm.destino.value)
    }

    // -- criterios 4 y 5: estado vigente -------------------------------------------------------

    @Test
    fun `al volver a la app un caso en revision resuelto muestra el estado nuevo`() = runTest {
        coEvery { repository.estado() } returnsMany listOf(estado("EN_REVISION"), estado("VERIFICADO"))
        val vm = viewModel()
        vm.actualizar()
        advanceUntilIdle()
        assertEquals(PantallaResultado.EnRevision, mostrando(vm).pantalla)

        vm.actualizar()
        advanceUntilIdle()

        assertEquals(PantallaResultado.Aprobada, mostrando(vm).pantalla)
    }

    @Test
    fun `mientras refresca mantiene el resultado anterior en pantalla`() = runTest {
        val respuesta = CompletableDeferred<Consulta<EstadoKycResponse>>()
        coEvery { repository.estado() } returns estado("EN_REVISION") coAndThen { respuesta.await() }
        val vm = viewModel()
        vm.actualizar()
        advanceUntilIdle()

        vm.actualizar()
        advanceUntilIdle()

        assertEquals(PantallaResultado.EnRevision, mostrando(vm).pantalla)
        respuesta.complete(estado("VERIFICADO"))
        advanceUntilIdle()
        assertEquals(PantallaResultado.Aprobada, mostrando(vm).pantalla)
    }

    @Test
    fun `un fallo al refrescar no tapa el resultado ya mostrado`() = runTest {
        coEvery { repository.estado() } returnsMany listOf(estado("EN_REVISION"), Consulta.Fallo(ErrorConsulta.SIN_CONEXION))
        val vm = viewModel()
        vm.actualizar()
        advanceUntilIdle()

        vm.actualizar()
        advanceUntilIdle()

        assertEquals(PantallaResultado.EnRevision, mostrando(vm).pantalla)
    }

    @Test
    fun `no lanza dos consultas a la vez`() = runTest {
        val respuesta = CompletableDeferred<Consulta<EstadoKycResponse>>()
        coEvery { repository.estado() } coAnswers { respuesta.await() }
        val vm = viewModel()

        vm.actualizar()
        vm.actualizar()
        advanceUntilIdle()
        respuesta.complete(estado("VERIFICADO"))
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.estado() }
    }

    // -- sin resultado todavía -----------------------------------------------------------------

    @Test
    fun `pendiente con consentimiento lleva a verificar`() = runTest {
        coEvery { repository.estado() } returns estado("PENDIENTE")
        coEvery { repository.consentimientoOtorgado() } returns Consulta.Exito(true)
        val vm = viewModel()

        vm.actualizar()
        advanceUntilIdle()

        assertEquals(Destino.VERIFICACION, vm.destino.value)
    }

    @Test
    fun `pendiente sin consentimiento lleva primero al consentimiento`() = runTest {
        coEvery { repository.estado() } returns estado("PENDIENTE")
        coEvery { repository.consentimientoOtorgado() } returns Consulta.Exito(false)
        val vm = viewModel()

        vm.actualizar()
        advanceUntilIdle()

        assertEquals(Destino.CONSENTIMIENTO_ANTES_DE_VERIFICAR, vm.destino.value)
    }

    // -- errores -------------------------------------------------------------------------------

    @Test
    fun `sin conexion y sin resultado previo muestra el error para reintentar la consulta`() = runTest {
        coEvery { repository.estado() } returnsMany listOf(Consulta.Fallo(ErrorConsulta.SIN_CONEXION), estado("VERIFICADO"))
        val vm = viewModel()

        vm.actualizar()
        advanceUntilIdle()
        assertEquals(ResultadoUiState.Error(ErrorConsulta.SIN_CONEXION), vm.uiState.value)

        vm.actualizar()
        advanceUntilIdle()
        assertEquals(PantallaResultado.Aprobada, mostrando(vm).pantalla)
    }

    @Test
    fun `un estado desconocido es un error inesperado`() = runTest {
        coEvery { repository.estado() } returns estado("OTRO")
        val vm = viewModel()

        vm.actualizar()
        advanceUntilIdle()

        assertEquals(ResultadoUiState.Error(ErrorConsulta.INESPERADO), vm.uiState.value)
    }

    @Test
    fun `una sesion invalida se borra y vuelve al registro`() = runTest {
        coEvery { repository.estado() } returns Consulta.SinSesion
        val vm = viewModel()

        vm.actualizar()
        advanceUntilIdle()

        assertEquals(1, sesionesPerdidas)
        assertEquals(Destino.REGISTRO, vm.destino.value)
    }

    @Test
    fun `atender el destino lo limpia para no navegar dos veces`() = runTest {
        coEvery { repository.estado() } returns estado("RECHAZADO", intentos = 2)
        val vm = viewModel()
        vm.actualizar()
        advanceUntilIdle()
        vm.reintentar()

        vm.destinoAtendido()

        assertNull(vm.destino.value)
    }
}
