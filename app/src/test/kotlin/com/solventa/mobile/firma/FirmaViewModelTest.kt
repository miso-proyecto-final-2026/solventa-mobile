package com.solventa.mobile.firma

import com.solventa.mobile.red.DocumentoFirmaResponse
import com.solventa.mobile.red.FirmaResponse
import com.solventa.mobile.red.SolicitudFirma
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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
class FirmaViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<FirmaRepository>()
    private val documento = DocumentoFirmaResponse("s1", "POL1", "h".repeat(64), "PENDIENTE", "2026-10-31T20:43:00Z")
    private val firmada = FirmaResponse("s1", "p1", "EMITIDA", "h".repeat(64), "2026-10-30T20:43:21Z", "MOVIL", "OTP")

    private fun viewModel(maximoConsultas: Int = 4) = FirmaViewModel(
        repository = repository,
        dispositivo = { "Pixel 8 · Android 15" },
        segundosEntreCodigos = 3,
        intervaloDocumentoMs = 1_000,
        maximoConsultasDocumento = maximoConsultas,
    )

    private fun <T> exito(valor: T) = FirmaRepository.Resultado.Exito(valor)

    @Before
    fun antes() {
        Dispatchers.setMain(dispatcher)
        coEvery { repository.documento("s1") } returns exito(documento)
        coEvery { repository.pedirCodigo("s1") } returns exito(Unit)
    }

    @After
    fun despues() = Dispatchers.resetMain()

    /** Abre la firma y la deja lista para escribir el código. */
    private fun FirmaViewModel.abierto(): FirmaViewModel = apply {
        iniciar("s1")
        dispatcher.scheduler.advanceUntilIdle()
    }

    private fun FirmaViewModel.listoParaFirmar(codigo: String = "123456") {
        marcarRevisado(true)
        cambiarCodigo(codigo)
    }

    // --- cargar el documento ----------------------------------------------------------------------------------------

    @Test
    fun `al abrir carga el documento y pide el codigo una sola vez`() = runTest(dispatcher) {
        val vm = viewModel().abierto()

        assertEquals(FaseFirma.LISTA, vm.uiState.value.fase)
        assertEquals(documento, vm.uiState.value.documento)
        coVerify(exactly = 1) { repository.pedirCodigo("s1") }
    }

    @Test
    fun `abrir otra vez la misma firma no reinicia lo que el cliente escribio`() = runTest(dispatcher) {
        val vm = viewModel().abierto()
        vm.marcarRevisado(true)
        vm.cambiarCodigo("48")

        vm.iniciar("s1")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.revisado)
        assertEquals("48", vm.uiState.value.codigo)
        coVerify(exactly = 1) { repository.documento("s1") }
    }

    @Test
    fun `si el pdf aun no esta listo reintenta hasta que aparece`() = runTest(dispatcher) {
        coEvery { repository.documento("s1") } returnsMany listOf(
            FirmaRepository.Resultado.DocumentoNoListo,
            FirmaRepository.Resultado.DocumentoNoListo,
            exito(documento),
        )

        val vm = viewModel().abierto()

        assertEquals(FaseFirma.LISTA, vm.uiState.value.fase)
        coVerify(exactly = 3) { repository.documento("s1") }
    }

    @Test
    fun `si el pdf nunca aparece muestra un error con reintento`() = runTest(dispatcher) {
        coEvery { repository.documento("s1") } returns FirmaRepository.Resultado.DocumentoNoListo

        val vm = viewModel(maximoConsultas = 3).abierto()

        assertEquals(FaseFirma.ERROR, vm.uiState.value.fase)
        assertEquals(ErrorFirma.DOCUMENTO_NO_LISTO, vm.uiState.value.error)

        coEvery { repository.documento("s1") } returns exito(documento)
        vm.reintentar()
        advanceUntilIdle()
        assertEquals(FaseFirma.LISTA, vm.uiState.value.fase)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `un documento ya firmado en otro canal se muestra como firmado`() = runTest(dispatcher) {
        coEvery { repository.documento("s1") } returns exito(documento.copy(estado = "FIRMADA"))

        val vm = viewModel().abierto()

        assertEquals(FaseFirma.FIRMADA, vm.uiState.value.fase)
        coVerify(exactly = 0) { repository.pedirCodigo(any()) }
    }

    @Test
    fun `sin sesion o sin conexion al cargar cada falla tiene su error`() = runTest(dispatcher) {
        coEvery { repository.documento("s1") } returns FirmaRepository.Resultado.SinSesion
        assertEquals(ErrorFirma.SESION_VENCIDA, viewModel().abierto().uiState.value.error)

        coEvery { repository.documento("s1") } returns FirmaRepository.Resultado.SinConexion
        assertEquals(ErrorFirma.SIN_CONEXION, viewModel().abierto().uiState.value.error)

        coEvery { repository.documento("s1") } returns FirmaRepository.Resultado.FirmaNoDisponible
        assertEquals(ErrorFirma.NO_DISPONIBLE, viewModel().abierto().uiState.value.error)
    }

    @Test
    fun `una firma vencida o fallida al abrir termina en su pantalla`() = runTest(dispatcher) {
        coEvery { repository.documento("s1") } returns FirmaRepository.Resultado.Vencida
        assertEquals(FaseFirma.VENCIDA, viewModel().abierto().uiState.value.fase)

        coEvery { repository.documento("s1") } returns FirmaRepository.Resultado.Fallida
        assertEquals(FaseFirma.FALLIDA, viewModel().abierto().uiState.value.fase)
    }

    // --- el formulario ------------------------------------------------------------------------------------------------

    @Test
    fun `el codigo acepta solo digitos y a lo mas seis`() = runTest(dispatcher) {
        val vm = viewModel().abierto()

        vm.cambiarCodigo("48 1-9x0 72")
        assertEquals("481907", vm.uiState.value.codigo)

        vm.cambiarCodigo("abc")
        assertEquals("", vm.uiState.value.codigo)
    }

    @Test
    fun `firmar sin confirmar la lectura no envia nada y lo avisa`() = runTest(dispatcher) {
        val vm = viewModel().abierto()
        vm.cambiarCodigo("123456")

        vm.firmar()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.revisionPendiente)
        assertEquals(FaseFirma.LISTA, vm.uiState.value.fase)
        coVerify(exactly = 0) { repository.firmar(any(), any()) }

        vm.marcarRevisado(true)
        assertFalse(vm.uiState.value.revisionPendiente)
    }

    @Test
    fun `firmar con el codigo incompleto no envia nada y lo avisa`() = runTest(dispatcher) {
        val vm = viewModel().abierto()
        vm.marcarRevisado(true)
        vm.cambiarCodigo("1234")

        vm.firmar()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.codigoIncompleto)
        coVerify(exactly = 0) { repository.firmar(any(), any()) }

        vm.cambiarCodigo("12345")
        assertFalse(vm.uiState.value.codigoIncompleto)
    }

    // --- firmar ---------------------------------------------------------------------------------------------------------

    @Test
    fun `firmar envia el hash que vio el cliente, la revision, el canal y el dispositivo`() = runTest(dispatcher) {
        val enviada = slot<SolicitudFirma>()
        coEvery { repository.firmar("s1", capture(enviada)) } returns exito(firmada)
        val vm = viewModel().abierto()
        vm.listoParaFirmar()

        vm.firmar()
        advanceUntilIdle()

        assertEquals(SolicitudFirma("h".repeat(64), true, "123456", "MOVIL", "Pixel 8 · Android 15"), enviada.captured)
        assertEquals(FaseFirma.FIRMADA, vm.uiState.value.fase)
        assertEquals("", vm.uiState.value.codigo) // el codigo no se conserva
    }

    @Test
    fun `el codigo se borra del estado en cuanto se envia`() = runTest(dispatcher) {
        coEvery { repository.firmar(any(), any()) } returns exito(firmada)
        val vm = viewModel().abierto()
        vm.listoParaFirmar()

        vm.firmar()

        assertEquals(FaseFirma.FIRMANDO, vm.uiState.value.fase)
        assertEquals("", vm.uiState.value.codigo)
        advanceUntilIdle()
    }

    @Test
    fun `un codigo incorrecto vuelve al formulario con los intentos que quedan`() = runTest(dispatcher) {
        coEvery { repository.firmar(any(), any()) } returns FirmaRepository.Resultado.CodigoInvalido(2)
        val vm = viewModel().abierto()
        vm.listoParaFirmar("000000")

        vm.firmar()
        advanceUntilIdle()

        with(vm.uiState.value) {
            assertEquals(FaseFirma.LISTA, fase)
            assertEquals(AvisoFirma.CODIGO_INCORRECTO, aviso)
            assertEquals(2, intentosRestantes)
            assertEquals("", codigo)
            assertTrue(revisado) // no obliga a confirmar la lectura otra vez
        }
        vm.cambiarCodigo("1")
        assertNull(vm.uiState.value.aviso) // escribir de nuevo quita el aviso
    }

    @Test
    fun `agotar los codigos termina en la pantalla de firma fallida`() = runTest(dispatcher) {
        coEvery { repository.firmar(any(), any()) } returns FirmaRepository.Resultado.Fallida
        val vm = viewModel().abierto()
        vm.listoParaFirmar()

        vm.firmar()
        advanceUntilIdle()

        assertEquals(FaseFirma.FALLIDA, vm.uiState.value.fase)
    }

    @Test
    fun `firmar despues del plazo termina en la pantalla de firma vencida`() = runTest(dispatcher) {
        coEvery { repository.firmar(any(), any()) } returns FirmaRepository.Resultado.Vencida
        val vm = viewModel().abierto()
        vm.listoParaFirmar()

        vm.firmar()
        advanceUntilIdle()

        assertEquals(FaseFirma.VENCIDA, vm.uiState.value.fase)
    }

    @Test
    fun `si el documento cambio vuelve a leerlo y pide revisar de nuevo`() = runTest(dispatcher) {
        coEvery { repository.firmar(any(), any()) } returns FirmaRepository.Resultado.DocumentoAlterado
        val nuevo = documento.copy(documentoSha256 = "n".repeat(64))
        coEvery { repository.documento("s1") } returnsMany listOf(exito(documento), exito(nuevo))
        val vm = viewModel().abierto()
        vm.listoParaFirmar()

        vm.firmar()
        advanceUntilIdle()

        with(vm.uiState.value) {
            assertEquals(FaseFirma.LISTA, fase)
            assertEquals(nuevo, documento)
            assertFalse(revisado)
            assertEquals(AvisoFirma.DOCUMENTO_CAMBIO, aviso)
        }
    }

    @Test
    fun `sin conexion al firmar conserva el formulario y avisa`() = runTest(dispatcher) {
        coEvery { repository.firmar(any(), any()) } returns FirmaRepository.Resultado.SinConexion
        val vm = viewModel().abierto()
        vm.listoParaFirmar()

        vm.firmar()
        advanceUntilIdle()

        assertEquals(FaseFirma.LISTA, vm.uiState.value.fase)
        assertEquals(AvisoFirma.SIN_CONEXION, vm.uiState.value.aviso)
    }

    @Test
    fun `si no se sabe si se firmo consulta el estado antes de pedir otro codigo`() = runTest(dispatcher) {
        coEvery { repository.firmar(any(), any()) } returns FirmaRepository.Resultado.Desconocido
        coEvery { repository.documento("s1") } returnsMany listOf(exito(documento), exito(documento.copy(estado = "FIRMADA")))
        val vm = viewModel().abierto()
        vm.listoParaFirmar()

        vm.firmar()
        advanceUntilIdle()

        assertEquals(FaseFirma.FIRMADA, vm.uiState.value.fase)
        coVerify(exactly = 1) { repository.firmar(any(), any()) } // no se pidió otro código ni se firmó dos veces
    }

    @Test
    fun `si tras la duda sigue sin firmar vuelve al formulario con un aviso`() = runTest(dispatcher) {
        coEvery { repository.firmar(any(), any()) } returns FirmaRepository.Resultado.Desconocido
        val vm = viewModel().abierto()
        vm.listoParaFirmar()

        vm.firmar()
        advanceUntilIdle()

        assertEquals(FaseFirma.LISTA, vm.uiState.value.fase)
        assertEquals(AvisoFirma.SIN_CONEXION, vm.uiState.value.aviso)
    }

    // --- reenviar el codigo -----------------------------------------------------------------------------------------------

    @Test
    fun `tras pedir el codigo hay que esperar para pedir otro`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.iniciar("s1")
        dispatcher.scheduler.runCurrent() // carga el documento y pide el código, sin dejar correr la cuenta atrás
        assertEquals(3, vm.uiState.value.segundosParaReenviar)

        vm.reenviarCodigo()
        dispatcher.scheduler.runCurrent()
        coVerify(exactly = 1) { repository.pedirCodigo("s1") } // todavía en espera: no pide otro

        dispatcher.scheduler.advanceTimeBy(3_100)
        assertEquals(0, vm.uiState.value.segundosParaReenviar)
    }

    @Test
    fun `terminada la espera reenviar pide un codigo nuevo y reinicia la cuenta`() = runTest(dispatcher) {
        val vm = viewModel().abierto()
        dispatcher.scheduler.advanceTimeBy(3_100)

        vm.reenviarCodigo()
        advanceUntilIdle()

        coVerify(exactly = 2) { repository.pedirCodigo("s1") }
        assertTrue(vm.uiState.value.codigoReenviado)
    }

    @Test
    fun `si no se pudo pedir el codigo se avisa pero se puede seguir`() = runTest(dispatcher) {
        coEvery { repository.pedirCodigo("s1") } returns FirmaRepository.Resultado.SinConexion

        val vm = viewModel().abierto()

        assertEquals(FaseFirma.LISTA, vm.uiState.value.fase)
        assertEquals(AvisoFirma.SIN_CONEXION, vm.uiState.value.aviso)
        assertEquals(0, vm.uiState.value.segundosParaReenviar) // puede reintentar enseguida
    }

    // --- lector del PDF ------------------------------------------------------------------------------------------------------

    @Test
    fun `leer descarga el pdf y cerrar el lector lo descarta`() = runTest(dispatcher) {
        val bytes = "%PDF-1.4".toByteArray()
        coEvery { repository.pdf("s1") } returns exito(bytes)
        val vm = viewModel().abierto()

        vm.leer()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.pdf!!.bytes.contentEquals(bytes))
        assertFalse(vm.uiState.value.cargandoPdf)

        vm.cerrarLector()
        assertNull(vm.uiState.value.pdf)
    }

    @Test
    fun `si el pdf no se puede descargar se avisa sin sacar al cliente del formulario`() = runTest(dispatcher) {
        coEvery { repository.pdf("s1") } returns FirmaRepository.Resultado.SinConexion
        val vm = viewModel().abierto()

        vm.leer()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.errorPdf)
        assertNull(vm.uiState.value.pdf)
        assertEquals(FaseFirma.LISTA, vm.uiState.value.fase)
    }
}
