package com.solventa.mobile.polizas

import com.solventa.mobile.red.CoberturaPolizaResponse
import com.solventa.mobile.red.EnlaceCertificadoResponse
import com.solventa.mobile.red.PolizaResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

/** HU36: «Mis pólizas» sin conexión, la sincronización y el borrado de la copia local. */
@OptIn(ExperimentalCoroutinesApi::class)
class PolizasSinConexionTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<PolizasRepository>()
    private var ahora = Instant.parse("2026-11-14T12:00:00Z")

    /** Almacén en memoria: lo que la app guardaría cifrado en el teléfono. */
    private class AlmacenEnMemoria(var copia: CopiaPolizas? = null) : AlmacenPolizas {
        var borrados = 0
        private val transaccion = Mutex()

        /** Si no es `null`, la próxima lectura espera aquí antes de devolver: así se fuerza el cruce de dos escrituras. */
        var pausaTrasLeer: CompletableDeferred<Unit>? = null

        override suspend fun leer(clienteId: String): CopiaPolizas? {
            val leida = copia?.takeIf { it.clienteId == clienteId }
            pausaTrasLeer?.let { pausaTrasLeer = null; it.await() }
            return leida
        }
        override suspend fun guardar(copia: CopiaPolizas) {
            this.copia = copia
        }

        /** Igual que `updateData` de DataStore: una transacción a la vez. */
        override suspend fun actualizar(clienteId: String, cambio: suspend (CopiaPolizas?) -> CopiaPolizas?) =
            transaccion.withLock {
                cambio(leer(clienteId))?.also { copia = it }
            }

        override suspend fun borrar() {
            copia = null
            borrados++
        }
    }

    private val almacen = AlmacenEnMemoria()

    private fun poliza(id: String, estado: String = "VIGENTE", vence: String = "2027-06-12T00:00:00Z") = PolizaResponse(
        id = "p-$id", suscripcionId = id, numeroPoliza = "SOL-$id", producto = "microseguro_vida", estado = estado,
        vigenciaDesde = "2026-06-12T00:00:00Z", fechaVencimiento = vence, fechaEmision = "2026-06-12T00:00:00Z",
        primaAnual = 175_000, coberturas = listOf(CoberturaPolizaResponse("fallecimiento", 50_000_000)),
    )

    private fun viewModel(cliente: String? = "c1") = PolizasViewModel(
        repository = repository,
        reloj = { ahora },
        idioma = { "es-CO" },
        baseUrl = "https://d1.cloudfront.net/",
        almacen = almacen,
        clienteId = { cliente },
    )

    private fun billetera(vararg polizas: PolizaResponse) {
        coEvery { repository.billetera() } returns PolizasRepository.Resultado.Exito(polizas.toList())
    }

    private fun sinRed() {
        coEvery { repository.billetera() } returns PolizasRepository.Resultado.SinConexion
    }

    private fun copiaGuardada(vararg polizas: PolizaResponse, sincronizado: String = "2026-11-10T09:15:00Z") {
        almacen.copia = CopiasPolizas.desde("c1", polizas.toList(), Instant.parse(sincronizado), null)
    }

    @Before
    fun preparar() = Dispatchers.setMain(dispatcher)

    @After
    fun limpiar() = Dispatchers.resetMain()

    // --- criterios 1 y 5: sin red se ve la copia, con aviso y fecha -------------------------------------------------------

    @Test
    fun `sin conexion muestra la copia guardada con la fecha de la ultima sincronizacion`() = runTest(dispatcher) {
        copiaGuardada(poliza("s1"), poliza("s2"))
        sinRed()
        val vm = viewModel()

        vm.iniciar(); advanceUntilIdle()

        val estado = vm.uiState.value
        assertEquals(FasePolizas.LISTA, estado.fase)
        assertTrue(estado.sinConexion)
        assertEquals(Instant.parse("2026-11-10T09:15:00Z"), estado.sincronizadoEn)
        assertEquals(listOf("SOL-s1", "SOL-s2"), estado.polizas.map { it.numeroPoliza })
        assertEquals(listOf("fallecimiento"), estado.polizas.first().coberturas.map { it.tipo })
    }

    @Test
    fun `sin conexion y sin copia se informa el error de conexion`() = runTest(dispatcher) {
        sinRed()
        val vm = viewModel()

        vm.iniciar(); advanceUntilIdle()

        assertEquals(FasePolizas.ERROR, vm.uiState.value.fase)
        assertEquals(ErrorPolizas.SIN_CONEXION, vm.uiState.value.error)
    }

    @Test
    fun `si la lista ya estaba en pantalla y se pierde la red se conserva marcada como copia`() = runTest(dispatcher) {
        billetera(poliza("s1"))
        val vm = viewModel()
        vm.iniciar(); advanceUntilIdle()
        assertFalse(vm.uiState.value.sinConexion)

        sinRed()
        vm.iniciar(); advanceUntilIdle()

        assertEquals(FasePolizas.LISTA, vm.uiState.value.fase)
        assertTrue(vm.uiState.value.sinConexion)
        assertEquals(listOf("s1"), vm.uiState.value.polizas.map { it.suscripcionId })
    }

    // --- criterio 6: una póliza que venció sin red se ve vencida ---------------------------------------------------------

    @Test
    fun `una poliza que vencio mientras no habia red se ve vencida segun la fecha`() = runTest(dispatcher) {
        // Se sincronizó el 10 de noviembre, cuando aún estaba vigente; vence el 12 y se abre sin red el 14.
        copiaGuardada(poliza("s1", vence = "2026-11-12T00:00:00Z"))
        sinRed()
        val vm = viewModel()

        vm.iniciar(); advanceUntilIdle()

        val estado = vm.uiState.value
        assertEquals(EstadoPoliza.VENCIDA, estado.estadoDe(estado.polizas.single()))
    }

    @Test
    fun `el estado se recalcula con la hora de cada apertura, no con la de la sincronizacion`() = runTest(dispatcher) {
        copiaGuardada(poliza("s1", vence = "2026-11-20T00:00:00Z"))
        sinRed()
        val vm = viewModel()
        vm.iniciar(); advanceUntilIdle()
        assertEquals(EstadoPoliza.PROXIMA_A_VENCER, vm.uiState.value.let { it.estadoDe(it.polizas.single()) })

        ahora = Instant.parse("2026-11-21T08:00:00Z")
        vm.iniciar(); advanceUntilIdle()

        assertEquals(EstadoPoliza.VENCIDA, vm.uiState.value.let { it.estadoDe(it.polizas.single()) })
    }

    // --- criterios 4 y 8: sincronización y el servidor gana --------------------------------------------------------------

    @Test
    fun `sincronizar guarda lo del servidor y reemplaza la copia anterior`() = runTest(dispatcher) {
        copiaGuardada(poliza("viejo"))
        billetera(poliza("s1"), poliza("s2"))
        val vm = viewModel()

        vm.sincronizar(); advanceUntilIdle()

        val copia = almacen.copia!!
        assertEquals(listOf("s1", "s2"), copia.polizas.map { it.suscripcionId })
        assertEquals("2026-11-14T12:00:00Z", copia.sincronizadoEn)
        assertFalse(vm.uiState.value.sinConexion)
        assertEquals(ahora, vm.uiState.value.sincronizadoEn)
    }

    @Test
    fun `sincronizar sin conexion no muestra error ni toca la copia`() = runTest(dispatcher) {
        copiaGuardada(poliza("s1"))
        val antes = almacen.copia
        sinRed()
        val vm = viewModel()

        vm.sincronizar(); advanceUntilIdle()

        assertNull(vm.uiState.value.error)
        assertEquals(antes, almacen.copia)
    }

    @Test
    fun `sin cliente en sesion no se guarda copia`() = runTest(dispatcher) {
        billetera(poliza("s1"))
        val vm = viewModel(cliente = null)

        vm.sincronizar(); advanceUntilIdle()

        assertNull(almacen.copia)
    }

    // --- criterio 7: la copia no sobrevive a la sesión -------------------------------------------------------------------

    @Test
    fun `si la sesion dejo de valer se borra la copia`() = runTest(dispatcher) {
        copiaGuardada(poliza("s1"))
        coEvery { repository.billetera() } returns PolizasRepository.Resultado.SinSesion
        val vm = viewModel()

        vm.sincronizar(); advanceUntilIdle()

        assertNull(almacen.copia)
        assertEquals(1, almacen.borrados)
        assertEquals(ErrorPolizas.SESION_VENCIDA, vm.uiState.value.error)
    }

    // --- criterios 2 y 3: documentos y lo que requiere conexión ----------------------------------------------------------

    @Test
    fun `sin conexion el detalle no pide el enlace del QR`() = runTest(dispatcher) {
        copiaGuardada(poliza("s1"))
        sinRed()
        val vm = viewModel()
        vm.iniciar(); advanceUntilIdle()

        vm.abrir(vm.uiState.value.polizas.single()); advanceUntilIdle()

        assertEquals(EnlaceQr.RequiereConexion, vm.uiState.value.qr)
        coVerify(exactly = 0) { repository.enlaceCertificado(any(), any()) }
    }

    @Test
    fun `un documento guardado se recuerda en la copia y se ofrece sin conexion`() = runTest(dispatcher) {
        billetera(poliza("s1"))
        coEvery { repository.enlaceCertificado(any(), any()) } returns
            PolizasRepository.Resultado.Exito(EnlaceCertificadoResponse("/v1/certificados/abc", "2026-11-15T12:00:00Z"))
        val vm = viewModel()
        vm.iniciar(); advanceUntilIdle()
        vm.abrir(vm.uiState.value.polizas.single()); advanceUntilIdle()

        vm.documentoGuardado(DocumentoPoliza.CERTIFICADO, "content://docs/certificado-s1")
        advanceUntilIdle()

        assertEquals(mapOf("s1/CERTIFICADO" to "content://docs/certificado-s1"), almacen.copia!!.documentos)

        // Otra apertura, ya sin red: el documento sigue disponible.
        sinRed()
        val sinRed = viewModel()
        sinRed.iniciar(); advanceUntilIdle()
        val poliza = sinRed.uiState.value.polizas.single()
        assertEquals("content://docs/certificado-s1", sinRed.uiState.value.documentoGuardado(poliza, DocumentoPoliza.CERTIFICADO))
        assertNull(sinRed.uiState.value.documentoGuardado(poliza, DocumentoPoliza.POLIZA_FIRMADA))
    }

    /**
     * El cruce que se vio en el emulador: al volver del selector de archivos la app pasa a primer plano y sincroniza
     * mientras se guarda el URI del PDF. Antes, la sincronización leía la copia, el URI se escribía y la sincronización
     * escribía encima su copia vieja: el PDF quedaba olvidado y sin conexión se pedía descargarlo otra vez.
     */
    @Test
    fun `un documento guardado mientras se sincroniza no se pierde`() = runTest(dispatcher) {
        billetera(poliza("s1"))
        coEvery { repository.enlaceCertificado(any(), any()) } returns
            PolizasRepository.Resultado.Exito(EnlaceCertificadoResponse("/v1/certificados/abc", "2026-11-15T12:00:00Z"))
        val vm = viewModel()
        vm.iniciar(); advanceUntilIdle()
        vm.abrir(vm.uiState.value.polizas.single()); advanceUntilIdle()

        // La sincronización lee la copia (sin el documento) y se queda a mitad de su transacción.
        val pausa = CompletableDeferred<Unit>()
        almacen.pausaTrasLeer = pausa
        vm.sincronizar(); advanceUntilIdle()
        // En ese momento el cliente termina de guardar el PDF.
        vm.documentoGuardado(DocumentoPoliza.CERTIFICADO, "content://docs/certificado-s1"); advanceUntilIdle()
        pausa.complete(Unit); advanceUntilIdle()

        assertEquals(mapOf("s1/CERTIFICADO" to "content://docs/certificado-s1"), almacen.copia!!.documentos)
        assertEquals(mapOf("s1/CERTIFICADO" to "content://docs/certificado-s1"), vm.uiState.value.documentosGuardados)
    }

    @Test
    fun `una sincronizacion conserva los documentos ya descargados`() = runTest(dispatcher) {
        almacen.copia = CopiasPolizas.desde("c1", listOf(poliza("s1")), Instant.parse("2026-11-10T09:15:00Z"), null)
            .conDocumento("s1/POLIZA_FIRMADA", "content://docs/firmada-s1")
        billetera(poliza("s1"))
        val vm = viewModel()

        vm.sincronizar(); advanceUntilIdle()

        assertEquals(mapOf("s1/POLIZA_FIRMADA" to "content://docs/firmada-s1"), vm.uiState.value.documentosGuardados)
        assertEquals(mapOf("s1/POLIZA_FIRMADA" to "content://docs/firmada-s1"), almacen.copia!!.documentos)
    }
}
