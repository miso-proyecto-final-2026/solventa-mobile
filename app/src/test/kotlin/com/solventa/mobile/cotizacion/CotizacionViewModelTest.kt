package com.solventa.mobile.cotizacion

import com.solventa.mobile.red.CotizacionResponse
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
class CotizacionViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<CotizacionRepository>()
    private var ahora = 1_000L

    // Cada lectura avanza 187 ms: la diferencia entre la primera y la segunda es el tiempo observado.
    private fun viewModel() = CotizacionViewModel(repository, reloj = { ahora.also { ahora += 187 } })

    @Before
    fun antes() {
        Dispatchers.setMain(dispatcher)
        coEvery { repository.registrarLatencia(any(), any()) } returns Unit
    }

    @After
    fun despues() = Dispatchers.resetMain()

    private fun cotizacion(aproximada: Boolean = false, degradado: Boolean = false) = CotizacionResponse(
        id = "q1",
        producto = "microseguro_vida",
        moneda = "COP",
        primaAnual = 175_000,
        primaMensual = 14_583,
        coberturas = emptyList(),
        vigenteHasta = "2026-11-17T23:49:58Z",
        aproximada = aproximada,
        degradado = degradado,
    )

    private fun CotizacionViewModel.llenarVida(edad: String = "30", suma: String = "50000000") {
        elegirProducto("microseguro_vida")
        cambiarValor("edad", edad)
        cambiarValor("suma_asegurada", suma)
    }

    @Test
    fun `al iniciar se elige el producto y nada esta cargando`() {
        val estado = viewModel().uiState.value

        assertEquals(FaseCotizacion.ELEGIR_PRODUCTO, estado.fase)
        assertNull(estado.producto)
        assertNull(estado.error)
    }

    @Test
    fun `elegir un producto abre su formulario vacio`() {
        val vm = viewModel()

        vm.elegirProducto("proteccion_dispositivos")

        assertEquals(FaseCotizacion.FORMULARIO, vm.uiState.value.fase)
        assertEquals("proteccion_dispositivos", vm.uiState.value.producto?.id)
        assertEquals(emptyMap<String, String>(), vm.uiState.value.valores)
    }

    @Test
    fun `un producto inexistente se ignora`() {
        val vm = viewModel()

        vm.elegirProducto("inventado")

        assertEquals(FaseCotizacion.ELEGIR_PRODUCTO, vm.uiState.value.fase)
    }

    @Test
    fun `cada cambio valida solo su campo en linea`() {
        val vm = viewModel()
        vm.elegirProducto("microseguro_vida")

        vm.cambiarValor("edad", "17")

        assertEquals(ErrorCampo.MENOR_AL_MINIMO, vm.uiState.value.errores["edad"])
        assertFalse(vm.uiState.value.errores.containsKey("suma_asegurada"))

        vm.cambiarValor("edad", "30")
        assertFalse(vm.uiState.value.errores.containsKey("edad"))
    }

    @Test
    fun `solicitar con datos invalidos no llama al servidor`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.llenarVida(edad = "10", suma = "")

        vm.solicitar()
        advanceUntilIdle()

        assertEquals(FaseCotizacion.FORMULARIO, vm.uiState.value.fase)
        assertEquals(ErrorCampo.MENOR_AL_MINIMO, vm.uiState.value.errores["edad"])
        assertEquals(ErrorCampo.REQUERIDO, vm.uiState.value.errores["suma_asegurada"])
        coVerify(exactly = 0) { repository.cotizar(any(), any()) }
    }

    @Test
    fun `una cotizacion exitosa muestra el resultado y registra el tiempo observado`() = runTest(dispatcher) {
        coEvery { repository.cotizar("microseguro_vida", any()) } returns
            CotizacionRepository.Resultado.Exito(cotizacion())
        val vm = viewModel()
        vm.llenarVida()

        vm.solicitar()
        assertEquals(FaseCotizacion.CARGANDO, vm.uiState.value.fase)
        advanceUntilIdle()

        assertEquals(FaseCotizacion.RESULTADO, vm.uiState.value.fase)
        assertEquals("q1", vm.uiState.value.cotizacion?.id)
        assertFalse(vm.uiState.value.esEstimacion)
        assertFalse(vm.uiState.value.ofreceCompartirDatos)
        coVerify(exactly = 1) { repository.registrarLatencia("q1", 187.0) }
    }

    @Test
    fun `sin consentimiento es una estimacion y se ofrece compartir los datos`() = runTest(dispatcher) {
        coEvery { repository.cotizar(any(), any()) } returns
            CotizacionRepository.Resultado.Exito(cotizacion(aproximada = true))
        val vm = viewModel()
        vm.llenarVida()

        vm.solicitar()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.esEstimacion)
        assertTrue(vm.uiState.value.ofreceCompartirDatos)
    }

    @Test
    fun `un precio degradado es una estimacion pero compartir datos no lo arregla`() = runTest(dispatcher) {
        coEvery { repository.cotizar(any(), any()) } returns
            CotizacionRepository.Resultado.Exito(cotizacion(aproximada = true, degradado = true))
        val vm = viewModel()
        vm.llenarVida()

        vm.solicitar()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.esEstimacion)
        assertFalse(vm.uiState.value.ofreceCompartirDatos)
    }

    @Test
    fun `un 422 vuelve al formulario con el motivo por campo y los datos intactos`() = runTest(dispatcher) {
        coEvery { repository.cotizar(any(), any()) } returns
            CotizacionRepository.Resultado.Validacion(mapOf("edad" to "debe ser mayor o igual a 18"))
        val vm = viewModel()
        vm.llenarVida()

        vm.solicitar()
        advanceUntilIdle()

        assertEquals(FaseCotizacion.FORMULARIO, vm.uiState.value.fase)
        assertEquals("debe ser mayor o igual a 18", vm.uiState.value.erroresServidor["edad"])
        assertEquals("30", vm.uiState.value.valores["edad"])
        assertEquals("50000000", vm.uiState.value.valores["suma_asegurada"])
    }

    @Test
    fun `editar un campo limpia el error que venia del servidor`() = runTest(dispatcher) {
        coEvery { repository.cotizar(any(), any()) } returns
            CotizacionRepository.Resultado.Validacion(mapOf("edad" to "x"))
        val vm = viewModel()
        vm.llenarVida()
        vm.solicitar()
        advanceUntilIdle()

        vm.cambiarValor("edad", "31")

        assertFalse(vm.uiState.value.erroresServidor.containsKey("edad"))
    }

    @Test
    fun `cada falla tiene su error propio y conserva los datos para reintentar`() = runTest(dispatcher) {
        val casos = mapOf(
            CotizacionRepository.Resultado.TiempoAgotado to ErrorCotizacion.TIEMPO_AGOTADO,
            CotizacionRepository.Resultado.SinConexion to ErrorCotizacion.SIN_CONEXION,
            CotizacionRepository.Resultado.ErrorServicio(503) to ErrorCotizacion.SERVICIO,
            CotizacionRepository.Resultado.SinSesion to ErrorCotizacion.SESION_VENCIDA,
        )
        casos.forEach { (resultado, esperado) ->
            coEvery { repository.cotizar(any(), any()) } returns resultado
            val vm = viewModel()
            vm.llenarVida()

            vm.solicitar()
            advanceUntilIdle()

            assertEquals(esperado, vm.uiState.value.error)
            assertEquals(FaseCotizacion.FORMULARIO, vm.uiState.value.fase)
            assertEquals("30", vm.uiState.value.valores["edad"])
        }
    }

    @Test
    fun `reintentar repite la solicitud con los mismos datos y limpia el error`() = runTest(dispatcher) {
        coEvery { repository.cotizar(any(), any()) } returnsMany listOf(
            CotizacionRepository.Resultado.TiempoAgotado,
            CotizacionRepository.Resultado.Exito(cotizacion()),
        )
        val vm = viewModel()
        vm.llenarVida()
        vm.solicitar()
        advanceUntilIdle()

        vm.reintentar()
        advanceUntilIdle()

        assertEquals(FaseCotizacion.RESULTADO, vm.uiState.value.fase)
        assertNull(vm.uiState.value.error)
        coVerify(exactly = 2) { repository.cotizar("microseguro_vida", any()) }
    }

    @Test
    fun `no se envia dos veces mientras se espera la respuesta`() = runTest(dispatcher) {
        coEvery { repository.cotizar(any(), any()) } returns CotizacionRepository.Resultado.Exito(cotizacion())
        val vm = viewModel()
        vm.llenarVida()

        vm.solicitar()
        vm.solicitar()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.cotizar(any(), any()) }
    }

    @Test
    fun `una falla no registra latencia`() = runTest(dispatcher) {
        coEvery { repository.cotizar(any(), any()) } returns CotizacionRepository.Resultado.SinConexion
        val vm = viewModel()
        vm.llenarVida()

        vm.solicitar()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.registrarLatencia(any(), any()) }
    }

    @Test
    fun `cotizar de nuevo vuelve a elegir producto sin datos previos`() = runTest(dispatcher) {
        coEvery { repository.cotizar(any(), any()) } returns CotizacionRepository.Resultado.Exito(cotizacion())
        val vm = viewModel()
        vm.llenarVida()
        vm.solicitar()
        advanceUntilIdle()

        vm.nuevaCotizacion()

        assertEquals(FaseCotizacion.ELEGIR_PRODUCTO, vm.uiState.value.fase)
        assertNull(vm.uiState.value.cotizacion)
        assertEquals(emptyMap<String, String>(), vm.uiState.value.valores)
    }

    @Test
    fun `volver a productos desde el formulario descarta lo ingresado`() {
        val vm = viewModel()
        vm.llenarVida()

        vm.volverAProductos()

        assertEquals(FaseCotizacion.ELEGIR_PRODUCTO, vm.uiState.value.fase)
        assertEquals(emptyMap<String, String>(), vm.uiState.value.valores)
    }

    @Test
    fun `modificar datos vuelve al formulario con lo ingresado y sin el resultado`() = runTest {
        coEvery { repository.cotizar(any(), any()) } returns CotizacionRepository.Resultado.Exito(cotizacion())
        val vm = viewModel()
        vm.elegirProducto("microseguro_vida")
        vm.llenarVida()
        vm.solicitar()
        advanceUntilIdle()
        assertEquals(FaseCotizacion.RESULTADO, vm.uiState.value.fase)

        vm.modificarDatos()

        assertEquals(FaseCotizacion.FORMULARIO, vm.uiState.value.fase)
        assertNull(vm.uiState.value.cotizacion)
        assertEquals("30", vm.uiState.value.valores["edad"])
        assertEquals("microseguro_vida", vm.uiState.value.producto?.id)
    }

    @Test
    fun `modificar datos sin producto elegido no hace nada`() {
        val vm = viewModel()

        vm.modificarDatos()

        assertEquals(FaseCotizacion.ELEGIR_PRODUCTO, vm.uiState.value.fase)
    }
}
