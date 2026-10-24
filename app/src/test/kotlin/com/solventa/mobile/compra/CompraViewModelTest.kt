package com.solventa.mobile.compra

import com.solventa.mobile.red.CotizacionResponse
import com.solventa.mobile.red.DecisionResponse
import com.solventa.mobile.red.SuscripcionResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompraViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<SuscripcionRepository>()
    private val tokenizador = mockk<TokenizadorPago>()
    private var claves = 0
    private val hoy = Instant.parse("2026-10-24T12:00:00Z")

    private fun viewModel(maximoConsultas: Int = 5) = CompraViewModel(
        repository = repository,
        tokenizador = tokenizador,
        nuevaClave = { "clave-${++claves}" },
        ahora = { hoy },
        intervaloConsultaMs = 1_000,
        maximoConsultas = maximoConsultas,
    )

    private val cotizacion = CotizacionResponse(
        id = "q1",
        producto = "microseguro_vida",
        moneda = "COP",
        primaAnual = 175_000,
        primaMensual = 14_583,
        coberturas = emptyList(),
        vigenteHasta = "2026-11-17T23:49:58Z",
    )

    private fun saga(estado: String, paso: String = "DECISION", decision: String? = null, motivos: List<String> = emptyList()) =
        SuscripcionResponse("s1", "q1", estado, paso, decision = decision?.let { DecisionResponse(it, motivos) })

    private fun exito(saga: SuscripcionResponse) = SuscripcionRepository.Resultado.Exito(saga)

    @Before
    fun antes() {
        Dispatchers.setMain(dispatcher)
        coEvery { tokenizador.tokenizar(any()) } returns "tok_1"
    }

    @After
    fun despues() = Dispatchers.resetMain()

    /** Lleva el flujo hasta el envío con una tarjeta de prueba válida. */
    private fun VM.pagarConTarjetaValida() {
        iniciar(cotizacion)
        aceptarTerminos(true)
        continuarAPago()
        cambiarTarjeta("4111 1111 1111 1111", "12/28", "123")
        pagar()
    }

    private fun VM.estado() = uiState.value

    @Test
    fun `muestra la oferta y exige aceptar los terminos antes de pagar`() {
        val vm = viewModel()
        vm.iniciar(cotizacion)

        vm.continuarAPago()

        assertEquals(FaseCompra.OFERTA, vm.estado().fase)
        assertTrue(vm.estado().terminosPendientes)

        vm.aceptarTerminos(true)
        vm.continuarAPago()

        assertEquals(FaseCompra.PAGO, vm.estado().fase)
    }

    @Test
    fun `una oferta vencida no deja pagar`() {
        val vm = CompraViewModel(repository, tokenizador, ahora = { Instant.parse("2026-12-01T00:00:00Z") })

        vm.iniciar(cotizacion)

        assertEquals(FaseCompra.OFERTA_VENCIDA, vm.estado().fase)
    }

    @Test
    fun `una tarjeta invalida marca los campos y no llama al servidor`() = runTest {
        val vm = viewModel()
        vm.iniciar(cotizacion)
        vm.aceptarTerminos(true)
        vm.continuarAPago()
        vm.cambiarTarjeta("4111111111111112", "01/20", "1")

        vm.pagar()
        advanceUntilIdle()

        assertEquals(setOf(ErrorTarjeta.NUMERO, ErrorTarjeta.VENCIMIENTO, ErrorTarjeta.CVV), vm.estado().erroresTarjeta)
        assertEquals(FaseCompra.PAGO, vm.estado().fase)
        coVerify(exactly = 0) { tokenizador.tokenizar(any()) }
        coVerify(exactly = 0) { repository.iniciar(any(), any(), any()) }
    }

    @Test
    fun `solo el token viaja y la tarjeta se borra del estado`() = runTest {
        coEvery { repository.iniciar("q1", "tok_1", "clave-1") } returns exito(saga("COBRO_REALIZADO", "FIRMA", "APROBADA"))
        val vm = viewModel()

        vm.pagarConTarjetaValida()
        advanceUntilIdle()

        assertEquals(FaseCompra.EXITOSA, vm.estado().fase)
        assertEquals("", vm.estado().tarjeta.numero)
        assertEquals("", vm.estado().tarjeta.cvv)
        coVerify(exactly = 1) { repository.iniciar("q1", "tok_1", "clave-1") }
    }

    @Test
    fun `sigue la saga hasta el resultado mostrando el paso actual`() = runTest {
        coEvery { repository.iniciar(any(), any(), any()) } returns exito(saga("EN_PROGRESO"))
        coEvery { repository.consultar("s1") } returnsMany listOf(
            exito(saga("APROBADA", "COBRO", "APROBADA")),
            exito(saga("COBRO_REALIZADO", "FIRMA", "APROBADA")),
        )
        val vm = viewModel()

        vm.pagarConTarjetaValida()
        dispatcher.scheduler.runCurrent()
        assertEquals(FaseCompra.PROGRESO, vm.estado().fase)
        assertEquals("DECISION", vm.estado().paso)

        dispatcher.scheduler.advanceTimeBy(1_001)
        dispatcher.scheduler.runCurrent()
        assertEquals("COBRO", vm.estado().paso)

        advanceUntilIdle()
        assertEquals(FaseCompra.EXITOSA, vm.estado().fase)
    }

    @Test
    fun `una decision de rechazo muestra los motivos sin cobro`() = runTest {
        coEvery { repository.iniciar(any(), any(), any()) } returns
            exito(saga("RECHAZADA", "DECISION", "RECHAZADA", listOf("PERFIL_NO_ELEGIBLE")))
        val vm = viewModel()

        vm.pagarConTarjetaValida()
        advanceUntilIdle()

        assertEquals(FaseCompra.DECISION_RECHAZADA, vm.estado().fase)
        assertEquals(listOf("PERFIL_NO_ELEGIBLE"), vm.estado().motivos)
    }

    @Test
    fun `derivada a revision asistida es un resultado distinto`() = runTest {
        coEvery { repository.iniciar(any(), any(), any()) } returns exito(saga("EN_PROGRESO", "DECISION", "DERIVADA"))
        val vm = viewModel()

        vm.pagarConTarjetaValida()
        advanceUntilIdle()

        assertEquals(FaseCompra.EN_REVISION, vm.estado().fase)
        assertFalse(vm.estado().ajustada)
    }

    @Test
    fun `un cobro rechazado permite otro medio de pago con una clave nueva sin cotizar de nuevo`() = runTest {
        coEvery { repository.iniciar("q1", "tok_1", "clave-1") } returns exito(saga("RECHAZADA", "COBRO", "APROBADA"))
        coEvery { repository.iniciar("q1", "tok_2", "clave-2") } returns exito(saga("COBRO_REALIZADO", "FIRMA", "APROBADA"))
        coEvery { tokenizador.tokenizar(any()) } returnsMany listOf("tok_1", "tok_2")
        val vm = viewModel()
        vm.pagarConTarjetaValida()
        advanceUntilIdle()
        assertEquals(FaseCompra.COBRO_RECHAZADO, vm.estado().fase)

        vm.usarOtroMedioDePago()
        assertEquals(FaseCompra.PAGO, vm.estado().fase)
        assertEquals("q1", vm.estado().cotizacion?.id)
        assertTrue(vm.estado().aceptaTerminos)
        vm.cambiarTarjeta("5555555555554444", "11/29", "321")
        vm.pagar()
        advanceUntilIdle()

        assertEquals(FaseCompra.EXITOSA, vm.estado().fase)
        coVerify { repository.iniciar("q1", "tok_2", "clave-2") }
    }

    @Test
    fun `si no se sabe el resultado el reintento usa la misma clave y el mismo token`() = runTest {
        coEvery { repository.iniciar("q1", "tok_1", "clave-1") } returnsMany listOf(
            SuscripcionRepository.Resultado.ResultadoDesconocido,
            exito(saga("COBRO_REALIZADO", "FIRMA", "APROBADA")),
        )
        val vm = viewModel()
        vm.pagarConTarjetaValida()
        advanceUntilIdle()
        assertEquals(ErrorCompra.RESULTADO_DESCONOCIDO, vm.estado().error)

        vm.reintentar()
        advanceUntilIdle()

        assertEquals(FaseCompra.EXITOSA, vm.estado().fase)
        coVerify(exactly = 2) { repository.iniciar("q1", "tok_1", "clave-1") }
        coVerify(exactly = 1) { tokenizador.tokenizar(any()) }
        assertEquals(1, claves)
    }

    @Test
    fun `la oferta vencida segun el servidor lleva a cotizar de nuevo`() = runTest {
        coEvery { repository.iniciar(any(), any(), any()) } returns SuscripcionRepository.Resultado.OfertaVencida
        val vm = viewModel()

        vm.pagarConTarjetaValida()
        advanceUntilIdle()

        assertEquals(FaseCompra.OFERTA_VENCIDA, vm.estado().fase)
    }

    @Test
    fun `los errores de red y de sesion tienen su mensaje`() = runTest {
        val casos = mapOf(
            SuscripcionRepository.Resultado.SinConexion to ErrorCompra.SIN_CONEXION,
            SuscripcionRepository.Resultado.SinSesion to ErrorCompra.SESION_VENCIDA,
            SuscripcionRepository.Resultado.OfertaYaUsada to ErrorCompra.OFERTA_YA_USADA,
            SuscripcionRepository.Resultado.ServicioNoDisponible to ErrorCompra.SERVICIO,
            SuscripcionRepository.Resultado.ErrorServicio(500) to ErrorCompra.SERVICIO,
        )
        casos.forEach { (resultado, esperado) ->
            coEvery { repository.iniciar(any(), any(), any()) } returns resultado
            val vm = viewModel()
            vm.pagarConTarjetaValida()
            advanceUntilIdle()
            assertEquals("$resultado", esperado, vm.estado().error)
        }
    }

    @Test
    fun `si la saga tarda demasiado ofrece consultar de nuevo sin volver a cobrar`() = runTest {
        coEvery { repository.iniciar(any(), any(), any()) } returns exito(saga("APROBADA", "COBRO", "APROBADA"))
        coEvery { repository.consultar("s1") } returns exito(saga("APROBADA", "COBRO", "APROBADA"))
        val vm = viewModel(maximoConsultas = 3)

        vm.pagarConTarjetaValida()
        advanceUntilIdle()

        assertEquals(ErrorCompra.SEGUIMIENTO_LENTO, vm.estado().error)
        vm.reintentar()
        advanceUntilIdle()
        assertEquals(ErrorCompra.SEGUIMIENTO_LENTO, vm.estado().error)
        coVerify(exactly = 1) { repository.iniciar(any(), any(), any()) }
    }

    @Test
    fun `tres fallos de red seguidos al consultar detienen el seguimiento`() = runTest {
        coEvery { repository.iniciar(any(), any(), any()) } returns exito(saga("EN_PROGRESO"))
        coEvery { repository.consultar("s1") } returns SuscripcionRepository.Resultado.SinConexion
        val vm = viewModel()

        vm.pagarConTarjetaValida()
        advanceUntilIdle()

        assertEquals(ErrorCompra.SIN_CONEXION, vm.estado().error)
    }
}

private typealias VM = CompraViewModel
