package com.solventa.mobile.verificacion

import com.solventa.mobile.red.VerificacionResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
class VerificacionViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<VerificacionRepository>()
    private val borrados = mutableListOf<String>()
    private var calidad: ProblemaImagen? = null
    private var claves = 0

    @Before
    fun antes() = Dispatchers.setMain(dispatcher)

    @After
    fun despues() = Dispatchers.resetMain()

    private fun viewModel() = VerificacionViewModel(
        repository = repository,
        analizador = { calidad },
        borrarArchivo = { borrados += it },
        nuevaClave = { "clave-${++claves}" },
    )

    private fun respuesta(estado: String, intentos: Int = 3) =
        VerificacionRepository.Resultado.Exito(VerificacionResponse("v1", estado, null, intentos, "2026-10-16T12:00:00Z"))

    private fun TestScope.capturarYUsar(vm: VerificacionViewModel, ruta: String) {
        vm.onCapturada(ruta)
        advanceUntilIdle()
        vm.usarCaptura()
        advanceUntilIdle()
    }

    /** Lleva el flujo hasta el envío: permiso, anverso, reverso, revisión y selfie. */
    private fun TestScope.hastaElEnvio(vm: VerificacionViewModel) {
        vm.onPermiso(true)
        capturarYUsar(vm, "anverso.jpg")
        capturarYUsar(vm, "reverso.jpg")
        vm.continuarDesdeRevision()
        capturarYUsar(vm, "selfie.jpg")
    }

    // -- criterio 1: permiso de cámara ----------------------------------------------------

    @Test
    fun `empieza pidiendo el permiso de camara`() {
        val estado = viewModel().uiState.value

        assertEquals(PasoVerificacion.PERMISO, estado.paso)
        assertFalse(estado.permisoDenegado)
    }

    @Test
    fun `si se concede el permiso pasa a capturar el anverso`() {
        val vm = viewModel()

        vm.onPermiso(true)

        assertEquals(PasoVerificacion.ANVERSO, vm.uiState.value.paso)
    }

    @Test
    fun `si se niega el permiso no avanza y lo indica`() {
        val vm = viewModel()

        vm.onPermiso(false)

        assertEquals(PasoVerificacion.PERMISO, vm.uiState.value.paso)
        assertTrue(vm.uiState.value.permisoDenegado)
    }

    @Test
    fun `tras negarlo se puede conceder mas tarde desde los ajustes`() {
        val vm = viewModel()
        vm.onPermiso(false)

        vm.onPermiso(true)

        assertEquals(PasoVerificacion.ANVERSO, vm.uiState.value.paso)
        assertFalse(vm.uiState.value.permisoDenegado)
    }

    // -- criterio 2: captura guiada ---------------------------------------------------------

    @Test
    fun `una captura aceptable queda pendiente de revision y no avanza sola`() = runTest {
        val vm = viewModel()
        vm.onPermiso(true)

        vm.onCapturada("anverso.jpg")
        advanceUntilIdle()

        assertEquals("anverso.jpg", vm.uiState.value.capturaPendiente)
        assertEquals(PasoVerificacion.ANVERSO, vm.uiState.value.paso)
        assertNull(vm.uiState.value.anverso)
    }

    @Test
    fun `una imagen borrosa u oscura se rechaza con su indicacion y se borra`() = runTest {
        val vm = viewModel()
        vm.onPermiso(true)

        for (problema in listOf(ProblemaImagen.BORROSA, ProblemaImagen.OSCURA)) {
            calidad = problema
            vm.onCapturada("mala-$problema.jpg")
            advanceUntilIdle()

            assertEquals(problema, vm.uiState.value.problemaCalidad)
            assertNull(vm.uiState.value.capturaPendiente)
            assertTrue(borrados.contains("mala-$problema.jpg"))
        }
        assertEquals(PasoVerificacion.ANVERSO, vm.uiState.value.paso)
    }

    @Test
    fun `se puede repetir una captura antes de usarla`() = runTest {
        val vm = viewModel()
        vm.onPermiso(true)
        vm.onCapturada("intento1.jpg")
        advanceUntilIdle()

        vm.repetirCaptura()

        assertNull(vm.uiState.value.capturaPendiente)
        assertTrue(borrados.contains("intento1.jpg"))
    }

    @Test
    fun `anverso y reverso llevan a la revision y luego a la selfie`() = runTest {
        val vm = viewModel()
        vm.onPermiso(true)

        capturarYUsar(vm, "anverso.jpg")
        assertEquals(PasoVerificacion.REVERSO, vm.uiState.value.paso)
        capturarYUsar(vm, "reverso.jpg")
        assertEquals(PasoVerificacion.REVISION, vm.uiState.value.paso)
        vm.continuarDesdeRevision()

        assertEquals(PasoVerificacion.SELFIE, vm.uiState.value.paso)
    }

    @Test
    fun `desde la revision se repite una cara y se vuelve a la revision`() = runTest {
        val vm = viewModel()
        vm.onPermiso(true)
        capturarYUsar(vm, "anverso.jpg")
        capturarYUsar(vm, "reverso.jpg")

        vm.repetirParte(PasoVerificacion.ANVERSO)
        assertEquals(PasoVerificacion.ANVERSO, vm.uiState.value.paso)
        assertTrue(borrados.contains("anverso.jpg"))
        capturarYUsar(vm, "anverso2.jpg")

        assertEquals(PasoVerificacion.REVISION, vm.uiState.value.paso)
        assertEquals("anverso2.jpg", vm.uiState.value.anverso)
        assertEquals("reverso.jpg", vm.uiState.value.reverso)
    }

    @Test
    fun `el indicador de progreso es n de 4`() {
        assertEquals(
            listOf(1, 2, 3, 4),
            listOf(
                PasoVerificacion.ANVERSO,
                PasoVerificacion.REVERSO,
                PasoVerificacion.REVISION,
                PasoVerificacion.SELFIE,
            ).map { it.numero },
        )
        assertEquals(4, TOTAL_PASOS)
    }

    // -- criterios 3 a 6: envío y resultados -------------------------------------------------

    @Test
    fun `al usar la selfie se envian las tres imagenes`() = runTest {
        coEvery { repository.enviar(any(), any(), any(), any()) } returns respuesta("VERIFICADO")
        val vm = viewModel()

        hastaElEnvio(vm)

        coVerify { repository.enviar("clave-1", "anverso.jpg", "reverso.jpg", "selfie.jpg") }
    }

    @Test
    fun `verificado termina el flujo y borra las imagenes del telefono`() = runTest {
        coEvery { repository.enviar(any(), any(), any(), any()) } returns respuesta("VERIFICADO")
        val vm = viewModel()

        hastaElEnvio(vm)

        assertEquals(PasoVerificacion.RESULTADO, vm.uiState.value.paso)
        assertEquals(EstadoResultado.VERIFICADO, vm.uiState.value.resultado)
        assertTrue(borrados.containsAll(listOf("anverso.jpg", "reverso.jpg", "selfie.jpg")))
    }

    @Test
    fun `rechazado informa los intentos restantes y conserva las imagenes`() = runTest {
        coEvery { repository.enviar(any(), any(), any(), any()) } returns respuesta("RECHAZADO", intentos = 2)
        val vm = viewModel()

        hastaElEnvio(vm)

        assertEquals(EstadoResultado.RECHAZADO, vm.uiState.value.resultado)
        assertEquals(2, vm.uiState.value.intentosRestantes)
        assertFalse(borrados.contains("selfie.jpg"))
    }

    @Test
    fun `tras un rechazo se reintenta con capturas nuevas y una clave nueva`() = runTest {
        coEvery { repository.enviar(any(), any(), any(), any()) } returns respuesta("RECHAZADO", intentos = 2)
        val vm = viewModel()
        hastaElEnvio(vm)

        vm.reintentarVerificacion()

        val estado = vm.uiState.value
        assertEquals(PasoVerificacion.ANVERSO, estado.paso)
        assertNull(estado.anverso)
        assertNull(estado.resultado)
        assertTrue(borrados.containsAll(listOf("anverso.jpg", "reverso.jpg", "selfie.jpg")))

        coEvery { repository.enviar(any(), any(), any(), any()) } returns respuesta("VERIFICADO")
        capturarYUsar(vm, "a2.jpg")
        capturarYUsar(vm, "r2.jpg")
        vm.continuarDesdeRevision()
        capturarYUsar(vm, "s2.jpg")
        coVerify { repository.enviar("clave-2", "a2.jpg", "r2.jpg", "s2.jpg") }
    }

    @Test
    fun `en revision no se rechaza al cliente y se informa que se le avisara`() = runTest {
        coEvery { repository.enviar(any(), any(), any(), any()) } returns respuesta("EN_REVISION")
        val vm = viewModel()

        hastaElEnvio(vm)

        assertEquals(EstadoResultado.EN_REVISION, vm.uiState.value.resultado)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `un fallo de red conserva las imagenes y reintenta con la misma clave`() = runTest {
        coEvery { repository.enviar(any(), any(), any(), any()) } returns
            VerificacionRepository.Resultado.Fallo(ErrorEnvio.SIN_CONEXION) andThen respuesta("VERIFICADO")
        val vm = viewModel()

        hastaElEnvio(vm)
        assertEquals(PasoVerificacion.ENVIANDO, vm.uiState.value.paso)
        assertEquals(ErrorEnvio.SIN_CONEXION, vm.uiState.value.error)
        assertEquals("selfie.jpg", vm.uiState.value.selfie)
        assertTrue(borrados.isEmpty())

        vm.enviar()
        advanceUntilIdle()

        assertEquals(EstadoResultado.VERIFICADO, vm.uiState.value.resultado)
        coVerify(exactly = 2) { repository.enviar("clave-1", "anverso.jpg", "reverso.jpg", "selfie.jpg") }
        assertEquals(1, claves)
    }

    @Test
    fun `bloqueado muestra cuanto esperar`() = runTest {
        coEvery { repository.enviar(any(), any(), any(), any()) } returns
            VerificacionRepository.Resultado.Fallo(ErrorEnvio.BLOQUEADO, 1800L)
        val vm = viewModel()

        hastaElEnvio(vm)

        assertEquals(EstadoResultado.BLOQUEADO, vm.uiState.value.resultado)
        assertEquals(1800L, vm.uiState.value.reintentarEnSegundos)
    }

    @Test
    fun `si el servidor rechaza una imagen hay que capturar todo de nuevo`() = runTest {
        coEvery { repository.enviar(any(), any(), any(), any()) } returns
            VerificacionRepository.Resultado.Fallo(ErrorEnvio.IMAGEN_INVALIDA)
        val vm = viewModel()

        hastaElEnvio(vm)

        val estado = vm.uiState.value
        assertEquals(PasoVerificacion.ANVERSO, estado.paso)
        assertEquals(ErrorEnvio.IMAGEN_INVALIDA, estado.error)
        assertNull(estado.selfie)
        assertTrue(borrados.contains("selfie.jpg"))
    }

    @Test
    fun `sin sesion o ya procesada termina en el resultado con el error`() = runTest {
        for (error in listOf(ErrorEnvio.SIN_SESION, ErrorEnvio.YA_PROCESADA)) {
            coEvery { repository.enviar(any(), any(), any(), any()) } returns
                VerificacionRepository.Resultado.Fallo(error)
            val vm = viewModel()

            hastaElEnvio(vm)

            assertEquals(PasoVerificacion.RESULTADO, vm.uiState.value.paso)
            assertEquals(error, vm.uiState.value.error)
        }
    }

    @Test
    fun `un doble toque al capturar no analiza dos veces`() = runTest {
        var analisis = 0
        val vm = VerificacionViewModel(
            repository = repository,
            analizador = { analisis++; null },
            borrarArchivo = {},
            nuevaClave = { "k" },
        )
        vm.onPermiso(true)

        vm.onCapturada("a.jpg")
        vm.onCapturada("b.jpg")
        advanceUntilIdle()

        assertEquals(1, analisis)
        assertEquals("a.jpg", vm.uiState.value.capturaPendiente)
    }

    @Test
    fun `finalizar avisa a la navegacion`() {
        val vm = viewModel()

        vm.finalizar()

        assertTrue(vm.uiState.value.finalizado)
    }
}
