package com.solventa.mobile.polizas

import com.solventa.mobile.red.CoberturaPolizaResponse
import com.solventa.mobile.red.EnlaceCertificadoResponse
import com.solventa.mobile.red.PolizaResponse
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PolizasViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<PolizasRepository>()
    private val ahora = Instant.parse("2026-11-08T12:00:00Z")

    private fun poliza(id: String, estado: String = "VIGENTE", vence: String = "2027-06-12T00:00:00Z") = PolizaResponse(
        id = "p-$id", suscripcionId = id, numeroPoliza = "SOL-$id", producto = "microseguro_vida", estado = estado,
        vigenciaDesde = "2026-06-12T00:00:00Z", fechaVencimiento = vence, fechaEmision = "2026-06-12T00:00:00Z",
        primaAnual = 175_000, coberturas = listOf(CoberturaPolizaResponse("fallecimiento", 50_000_000)),
    )

    private val vigente = poliza("s1")
    private val proxima = poliza("s2", vence = "2026-11-20T00:00:00Z")
    private val vencida = poliza("s3", vence = "2026-10-01T00:00:00Z")
    private val pendiente = poliza("s4", estado = "PENDIENTE_FIRMA")
    private val anulada = poliza("s5", estado = "ANULADA")

    private fun viewModel() = PolizasViewModel(
        repository = repository,
        reloj = { ahora },
        idioma = { "en-US" },
        baseUrl = "https://d1.cloudfront.net/",
    )

    private fun <T> exito(valor: T) = PolizasRepository.Resultado.Exito(valor)

    private fun billetera(vararg polizas: PolizaResponse) {
        coEvery { repository.billetera() } returns exito(polizas.toList())
    }

    private fun enlace(ruta: String = "/v1/certificados/abc.def") {
        coEvery { repository.enlaceCertificado(any(), any()) } returns exito(EnlaceCertificadoResponse(ruta, "2026-11-09T12:00:00Z"))
    }

    @Before
    fun preparar() = Dispatchers.setMain(dispatcher)

    @After
    fun limpiar() = Dispatchers.resetMain()

    // --- lista ---------------------------------------------------------------------------------------------------------

    @Test
    fun `la lista empieza cargando y muestra las polizas activas`() = runTest(dispatcher) {
        billetera(vigente, proxima, vencida, pendiente, anulada)
        val vm = viewModel()

        assertEquals(FasePolizas.CARGANDO, vm.uiState.value.fase)
        vm.iniciar()
        advanceUntilIdle()

        val estado = vm.uiState.value
        assertEquals(FasePolizas.LISTA, estado.fase)
        assertEquals(listOf("s1", "s2", "s4"), estado.visibles.map { it.suscripcionId })
    }

    @Test
    fun `la pestana vencidas muestra las vencidas y las anuladas`() = runTest(dispatcher) {
        billetera(vigente, proxima, vencida, pendiente, anulada)
        val vm = viewModel()
        vm.iniciar()
        advanceUntilIdle()

        vm.elegirFiltro(FiltroPolizas.VENCIDAS)

        assertEquals(listOf("s3", "s5"), vm.uiState.value.visibles.map { it.suscripcionId })
    }

    @Test
    fun `cada poliza muestra el estado que le corresponde`() = runTest(dispatcher) {
        billetera(vigente, proxima, vencida, pendiente, anulada)
        val vm = viewModel()
        vm.iniciar()
        advanceUntilIdle()

        val estados = vm.uiState.value.polizas.associate { it.suscripcionId to vm.uiState.value.estadoDe(it) }

        assertEquals(
            mapOf(
                "s1" to EstadoPoliza.VIGENTE, "s2" to EstadoPoliza.PROXIMA_A_VENCER, "s3" to EstadoPoliza.VENCIDA,
                "s4" to EstadoPoliza.PENDIENTE_FIRMA, "s5" to EstadoPoliza.ANULADA,
            ),
            estados,
        )
    }

    @Test
    fun `un cliente sin polizas ve el estado vacio`() = runTest(dispatcher) {
        billetera()
        val vm = viewModel()
        vm.iniciar()
        advanceUntilIdle()

        assertEquals(FasePolizas.LISTA, vm.uiState.value.fase)
        assertTrue(!vm.uiState.value.hayPolizas && vm.uiState.value.visibles.isEmpty())
    }

    @Test
    fun `sin conexion la primera carga muestra el error y reintentar la repite`() = runTest(dispatcher) {
        coEvery { repository.billetera() } returnsMany listOf(PolizasRepository.Resultado.SinConexion, exito(listOf(vigente)))
        val vm = viewModel()
        vm.iniciar()
        advanceUntilIdle()

        assertEquals(FasePolizas.ERROR, vm.uiState.value.fase)
        assertEquals(ErrorPolizas.SIN_CONEXION, vm.uiState.value.error)

        vm.recargar()
        assertEquals(FasePolizas.CARGANDO, vm.uiState.value.fase)
        advanceUntilIdle()

        assertEquals(FasePolizas.LISTA, vm.uiState.value.fase)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `un error del servidor y una sesion vencida se distinguen`() = runTest(dispatcher) {
        coEvery { repository.billetera() } returnsMany listOf(PolizasRepository.Resultado.ErrorServicio(500), PolizasRepository.Resultado.SinSesion)
        val vm = viewModel()

        vm.iniciar()
        advanceUntilIdle()
        assertEquals(ErrorPolizas.SERVICIO, vm.uiState.value.error)

        vm.recargar()
        advanceUntilIdle()
        assertEquals(ErrorPolizas.SESION_VENCIDA, vm.uiState.value.error)
    }

    @Test
    fun `volver a la pestana refresca en silencio y una compra nueva aparece`() = runTest(dispatcher) {
        coEvery { repository.billetera() } returnsMany listOf(exito(listOf(vigente)), exito(listOf(poliza("s9"), vigente)))
        val vm = viewModel()
        vm.iniciar()
        advanceUntilIdle()

        vm.iniciar() // segunda entrada a la pestaña
        assertEquals("la lista no vuelve a mostrar la carga", FasePolizas.LISTA, vm.uiState.value.fase)
        advanceUntilIdle()

        assertEquals(listOf("s9", "s1"), vm.uiState.value.visibles.map { it.suscripcionId })
    }

    @Test
    fun `un refresco silencioso que falla conserva la lista`() = runTest(dispatcher) {
        coEvery { repository.billetera() } returnsMany listOf(exito(listOf(vigente)), PolizasRepository.Resultado.SinConexion)
        val vm = viewModel()
        vm.iniciar()
        advanceUntilIdle()

        vm.iniciar()
        advanceUntilIdle()

        assertEquals(FasePolizas.LISTA, vm.uiState.value.fase)
        assertEquals(listOf("s1"), vm.uiState.value.visibles.map { it.suscripcionId })
    }

    // --- detalle y QR --------------------------------------------------------------------------------------------------

    @Test
    fun `abrir una poliza vigente pide el enlace y arma la URL absoluta del QR`() = runTest(dispatcher) {
        billetera(vigente)
        enlace()
        val vm = viewModel()
        vm.iniciar()
        advanceUntilIdle()

        vm.abrir(vigente)
        assertEquals(EnlaceQr.Cargando, vm.uiState.value.qr)
        advanceUntilIdle()

        assertEquals(vigente, vm.uiState.value.seleccionada)
        assertEquals(EnlaceQr.Listo("https://d1.cloudfront.net/v1/certificados/abc.def"), vm.uiState.value.qr)
        coVerify { repository.enlaceCertificado("s1", "en-US") }
    }

    @Test
    fun `solo las polizas que cubren tienen QR`() = runTest(dispatcher) {
        billetera(vencida, pendiente, anulada)
        enlace()
        val vm = viewModel()
        vm.iniciar()
        advanceUntilIdle()

        listOf(vencida, pendiente, anulada).forEach {
            vm.abrir(it)
            advanceUntilIdle()
            assertEquals("${it.suscripcionId} no pide enlace", EnlaceQr.NoAplica, vm.uiState.value.qr)
        }
        coVerify(exactly = 0) { repository.enlaceCertificado(any(), any()) }
    }

    @Test
    fun `si el enlace falla el QR queda fallido y reintentar lo pide de nuevo`() = runTest(dispatcher) {
        billetera(vigente)
        coEvery { repository.enlaceCertificado(any(), any()) } returnsMany listOf(
            PolizasRepository.Resultado.SinConexion,
            exito(EnlaceCertificadoResponse("/v1/certificados/xyz.123", "2026-11-09T12:00:00Z")),
        )
        val vm = viewModel()
        vm.iniciar()
        advanceUntilIdle()
        vm.abrir(vigente)
        advanceUntilIdle()
        assertEquals(EnlaceQr.Fallido, vm.uiState.value.qr)

        vm.reintentarQr()
        advanceUntilIdle()

        assertEquals(EnlaceQr.Listo("https://d1.cloudfront.net/v1/certificados/xyz.123"), vm.uiState.value.qr)
    }

    @Test
    fun `cada vez que se abre un detalle se pide un enlace nuevo`() = runTest(dispatcher) {
        billetera(vigente)
        enlace()
        val vm = viewModel()
        vm.iniciar()
        advanceUntilIdle()

        vm.abrir(vigente); advanceUntilIdle()
        vm.cerrarDetalle()
        assertNull(vm.uiState.value.seleccionada)
        assertEquals(EnlaceQr.NoAplica, vm.uiState.value.qr)
        vm.abrir(vigente); advanceUntilIdle()

        coVerify(exactly = 2) { repository.enlaceCertificado("s1", "en-US") }
    }

    @Test
    fun `una notificacion abre el detalle de su poliza aunque la lista aun no haya llegado`() = runTest(dispatcher) {
        billetera(vigente, proxima)
        enlace()
        val vm = viewModel()

        vm.abrirPorSuscripcion("s2")
        assertNull(vm.uiState.value.seleccionada)
        advanceUntilIdle()

        assertEquals("s2", vm.uiState.value.seleccionada?.suscripcionId)
        assertTrue(vm.uiState.value.qr is EnlaceQr.Listo)
    }

    @Test
    fun `una notificacion de una poliza que no esta en la lista no abre nada ni se queda pendiente`() = runTest(dispatcher) {
        billetera(vigente)
        val vm = viewModel()
        vm.abrirPorSuscripcion("s-ajena")
        advanceUntilIdle()
        assertNull(vm.uiState.value.seleccionada)

        vm.iniciar() // un refresco posterior no debe abrirla de repente
        advanceUntilIdle()

        assertNull(vm.uiState.value.seleccionada)
    }

    // --- documentos ----------------------------------------------------------------------------------------------------

    @Test
    fun `descargar el certificado deja el PDF listo para guardar y luego guardado`() = runTest(dispatcher) {
        billetera(vigente)
        enlace()
        coEvery { repository.certificado("s1", "en-US") } returns exito(byteArrayOf(1, 2, 3))
        val vm = viewModel()
        vm.iniciar(); advanceUntilIdle()
        vm.abrir(vigente); advanceUntilIdle()

        vm.descargar(DocumentoPoliza.CERTIFICADO)
        assertEquals(EstadoDescarga.DESCARGANDO, vm.uiState.value.descarga)
        advanceUntilIdle()

        assertEquals(EstadoDescarga.LISTO_PARA_GUARDAR, vm.uiState.value.descarga)
        assertEquals(DocumentoPoliza.CERTIFICADO, vm.uiState.value.documento)
        assertEquals(3, vm.uiState.value.pdf?.bytes?.size)

        vm.descargaGuardada(true)

        assertEquals(EstadoDescarga.GUARDADO, vm.uiState.value.descarga)
        assertNull(vm.uiState.value.pdf)
    }

    @Test
    fun `descargar la poliza firmada usa el endpoint de la firma`() = runTest(dispatcher) {
        billetera(vigente)
        enlace()
        coEvery { repository.polizaFirmada("s1") } returns exito(byteArrayOf(9))
        val vm = viewModel()
        vm.iniciar(); advanceUntilIdle()
        vm.abrir(vigente); advanceUntilIdle()

        vm.descargar(DocumentoPoliza.POLIZA_FIRMADA)
        advanceUntilIdle()

        coVerify { repository.polizaFirmada("s1") }
        assertEquals(EstadoDescarga.LISTO_PARA_GUARDAR, vm.uiState.value.descarga)
    }

    @Test
    fun `cancelar el selector vuelve a inactivo y un fallo al escribir es error`() = runTest(dispatcher) {
        billetera(vigente)
        val vm = viewModel()
        vm.iniciar(); advanceUntilIdle()

        vm.descargaGuardada(null)
        assertEquals(EstadoDescarga.INACTIVO, vm.uiState.value.descarga)

        vm.descargaGuardada(false)
        assertEquals(EstadoDescarga.ERROR, vm.uiState.value.descarga)
    }

    @Test
    fun `un documento que no existe y una descarga fallida se distinguen`() = runTest(dispatcher) {
        billetera(vigente)
        enlace()
        coEvery { repository.certificado(any(), any()) } returnsMany listOf(
            PolizasRepository.Resultado.NoDisponible,
            PolizasRepository.Resultado.SinConexion,
        )
        val vm = viewModel()
        vm.iniciar(); advanceUntilIdle()
        vm.abrir(vigente); advanceUntilIdle()

        vm.descargar(DocumentoPoliza.CERTIFICADO); advanceUntilIdle()
        assertEquals(EstadoDescarga.NO_DISPONIBLE, vm.uiState.value.descarga)

        vm.descargar(DocumentoPoliza.CERTIFICADO); advanceUntilIdle()
        assertEquals(EstadoDescarga.ERROR, vm.uiState.value.descarga)
    }

    @Test
    fun `no se descarga dos veces a la vez`() = runTest(dispatcher) {
        billetera(vigente)
        enlace()
        coEvery { repository.certificado(any(), any()) } returns exito(byteArrayOf(1))
        val vm = viewModel()
        vm.iniciar(); advanceUntilIdle()
        vm.abrir(vigente); advanceUntilIdle()

        vm.descargar(DocumentoPoliza.CERTIFICADO)
        vm.descargar(DocumentoPoliza.CERTIFICADO)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.certificado("s1", "en-US") }
    }
}
