package com.solventa.mobile.biometria

import com.solventa.mobile.red.BiometriaResponse
import com.solventa.mobile.red.EstadoKycResponse
import com.solventa.mobile.resultado.ResultadoRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import javax.crypto.Cipher
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
class BiometriaViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val proveedor = mockk<ProveedorCapacidad>()
    private val clave = mockk<ClaveBiometrica>(relaxed = true)
    private val repository = mockk<BiometriaRepository>()
    private val resultado = mockk<ResultadoRepository>()
    private val cifrador = mockk<Cipher>()

    private val disponible = Capacidad(Disponibilidad.DISPONIBLE, listOf(MetodoBiometrico.HUELLA, MetodoBiometrico.FACIAL))
    private val inactiva = BiometriaResponse(clienteId = "c1", activo = false)
    private val activa = BiometriaResponse(clienteId = "c1", activo = true, metodo = "FACIAL", activadoEn = "2026-11-13T15:00:00Z")

    @Before
    fun antes() {
        Dispatchers.setMain(dispatcher)
        every { proveedor.consultar() } returns disponible
        every { clave.estado() } returns EstadoClave.AUSENTE
        every { clave.crearParaAutenticar() } returns cifrador
        coEvery { repository.consultar() } returns BiometriaRepository.Resultado.Exito(inactiva)
        coEvery { repository.desactivar() } returns BiometriaRepository.Resultado.Exito(null)
        kyc("VERIFICADO")
    }

    @After
    fun despues() = Dispatchers.resetMain()

    private fun kyc(estado: String) {
        coEvery { resultado.estado() } returns
            ResultadoRepository.Consulta.Exito(EstadoKycResponse(clienteId = "c1", estadoKyc = estado, intentosRestantes = 3))
    }

    private fun crear() = BiometriaViewModel(proveedor, clave, repository, resultado)

    private fun autenticador(respuesta: ResultadoAutenticacion) = Autenticador { _, _ -> respuesta }

    @Test
    fun `al abrir consulta el servidor y el dispositivo`() = runTest(dispatcher) {
        val vm = crear()
        assertEquals(FaseBiometria.CARGANDO, vm.uiState.value.fase)

        advanceUntilIdle()

        val estado = vm.uiState.value
        assertEquals(FaseBiometria.ESTADO, estado.fase)
        assertFalse(estado.activo)
        assertEquals(disponible, estado.capacidad)
        assertTrue(estado.puedeActivar)
        assertNull(estado.bloqueo)
    }

    @Test
    fun `sin sensor la opcion queda bloqueada con su motivo`() = runTest(dispatcher) {
        every { proveedor.consultar() } returns Capacidad(Disponibilidad.SIN_SENSOR, emptyList())
        val vm = crear()
        advanceUntilIdle()

        assertEquals(MotivoBloqueo.SIN_SENSOR, vm.uiState.value.bloqueo)
        vm.abrirFormulario()
        assertEquals(FaseBiometria.ESTADO, vm.uiState.value.fase)
    }

    @Test
    fun `sin biometria registrada en el sistema se explica el motivo`() = runTest(dispatcher) {
        every { proveedor.consultar() } returns Capacidad(Disponibilidad.SIN_REGISTRO, emptyList())
        val vm = crear()
        advanceUntilIdle()

        assertEquals(MotivoBloqueo.SIN_REGISTRO, vm.uiState.value.bloqueo)
    }

    @Test
    fun `con el KYC sin aprobar no se puede activar`() = runTest(dispatcher) {
        kyc("PENDIENTE")
        val vm = crear()
        advanceUntilIdle()

        assertEquals(MotivoBloqueo.KYC_NO_APROBADO, vm.uiState.value.bloqueo)
        vm.abrirFormulario()
        assertEquals(FaseBiometria.ESTADO, vm.uiState.value.fase)
    }

    @Test
    fun `si no se puede consultar el KYC el servidor lo valida al activar`() = runTest(dispatcher) {
        coEvery { resultado.estado() } returns ResultadoRepository.Consulta.Fallo(com.solventa.mobile.resultado.ErrorConsulta.SIN_CONEXION)
        val vm = crear()
        advanceUntilIdle()

        assertNull(vm.uiState.value.kycAprobado)
        assertTrue(vm.uiState.value.puedeActivar)
    }

    @Test
    fun `el formulario ofrece el primer metodo y deja elegir otro`() = runTest(dispatcher) {
        val vm = crear()
        advanceUntilIdle()

        vm.abrirFormulario()
        assertEquals(FaseBiometria.FORMULARIO, vm.uiState.value.fase)
        assertEquals(MetodoBiometrico.HUELLA, vm.uiState.value.metodoElegido)

        vm.elegirMetodo(MetodoBiometrico.FACIAL)
        assertEquals(MetodoBiometrico.FACIAL, vm.uiState.value.metodoElegido)
    }

    @Test
    fun `no deja elegir un metodo que el dispositivo no ofrece`() = runTest(dispatcher) {
        every { proveedor.consultar() } returns Capacidad(Disponibilidad.DISPONIBLE, listOf(MetodoBiometrico.HUELLA))
        val vm = crear()
        advanceUntilIdle()
        vm.abrirFormulario()

        vm.elegirMetodo(MetodoBiometrico.FACIAL)

        assertEquals(MetodoBiometrico.HUELLA, vm.uiState.value.metodoElegido)
    }

    @Test
    fun `una contrasena vacia no llama al servidor`() = runTest(dispatcher) {
        val vm = crear()
        advanceUntilIdle()
        vm.abrirFormulario()

        vm.confirmarActivacion("  ", autenticador(ResultadoAutenticacion.Exito))
        advanceUntilIdle()

        assertEquals(FaseBiometria.FORMULARIO, vm.uiState.value.fase)
        assertEquals(ErrorAcceso.CONTRASENA_VACIA, vm.uiState.value.error)
        coVerify(exactly = 0) { repository.activar(any(), any()) }
    }

    @Test
    fun `activa con contrasena correcta y biometria confirmada`() = runTest(dispatcher) {
        coEvery { repository.activar(MetodoBiometrico.FACIAL, "Clave1234") } returns BiometriaRepository.Resultado.Exito(activa)
        val vm = crear()
        advanceUntilIdle()
        vm.abrirFormulario()
        vm.elegirMetodo(MetodoBiometrico.FACIAL)

        vm.confirmarActivacion("Clave1234", autenticador(ResultadoAutenticacion.Exito))
        advanceUntilIdle()

        val estado = vm.uiState.value
        assertEquals(FaseBiometria.ESTADO, estado.fase)
        assertTrue(estado.activo)
        assertEquals(MetodoBiometrico.FACIAL, estado.metodoActivo)
        assertEquals(AvisoAcceso.ACTIVADO, estado.aviso)
        verify { clave.crearParaAutenticar() }
        coVerify(exactly = 0) { repository.desactivar() }
    }

    @Test
    fun `con contrasena incorrecta vuelve al formulario sin pedir la biometria`() = runTest(dispatcher) {
        coEvery { repository.activar(any(), any()) } returns BiometriaRepository.Resultado.ContrasenaIncorrecta
        val vm = crear()
        advanceUntilIdle()
        vm.abrirFormulario()

        vm.confirmarActivacion("mala", autenticador(ResultadoAutenticacion.Exito))
        advanceUntilIdle()

        assertEquals(FaseBiometria.FORMULARIO, vm.uiState.value.fase)
        assertEquals(ErrorAcceso.CONTRASENA_INCORRECTA, vm.uiState.value.error)
        assertFalse(vm.uiState.value.activo)
        verify(exactly = 0) { clave.crearParaAutenticar() }
    }

    @Test
    fun `si el servidor dice KYC no aprobado la opcion queda bloqueada`() = runTest(dispatcher) {
        coEvery { repository.activar(any(), any()) } returns BiometriaRepository.Resultado.KycNoAprobado
        val vm = crear()
        advanceUntilIdle()
        vm.abrirFormulario()

        vm.confirmarActivacion("Clave1234", autenticador(ResultadoAutenticacion.Exito))
        advanceUntilIdle()

        assertEquals(MotivoBloqueo.KYC_NO_APROBADO, vm.uiState.value.bloqueo)
        assertEquals(ErrorAcceso.KYC_NO_APROBADO, vm.uiState.value.error)
    }

    @Test
    fun `si el cliente cancela la biometria se revierte la activacion`() = runTest(dispatcher) {
        coEvery { repository.activar(any(), any()) } returns BiometriaRepository.Resultado.Exito(activa)
        val vm = crear()
        advanceUntilIdle()
        vm.abrirFormulario()

        vm.confirmarActivacion("Clave1234", autenticador(ResultadoAutenticacion.Cancelado))
        advanceUntilIdle()

        val estado = vm.uiState.value
        assertFalse(estado.activo)
        assertEquals(ErrorAcceso.CANCELADO, estado.error)
        verify { clave.borrar() }
        coVerify { repository.desactivar() }
    }

    @Test
    fun `si la biometria falla se revierte con otro mensaje`() = runTest(dispatcher) {
        coEvery { repository.activar(any(), any()) } returns BiometriaRepository.Resultado.Exito(activa)
        val vm = crear()
        advanceUntilIdle()
        vm.abrirFormulario()

        vm.confirmarActivacion("Clave1234", autenticador(ResultadoAutenticacion.Error))
        advanceUntilIdle()

        assertEquals(ErrorAcceso.AUTENTICACION, vm.uiState.value.error)
        assertFalse(vm.uiState.value.activo)
        coVerify { repository.desactivar() }
    }

    @Test
    fun `si no se puede crear la clave local se revierte`() = runTest(dispatcher) {
        coEvery { repository.activar(any(), any()) } returns BiometriaRepository.Resultado.Exito(activa)
        every { clave.crearParaAutenticar() } throws java.security.KeyStoreException()
        val vm = crear()
        advanceUntilIdle()
        vm.abrirFormulario()

        vm.confirmarActivacion("Clave1234", autenticador(ResultadoAutenticacion.Exito))
        advanceUntilIdle()

        assertEquals(ErrorAcceso.AUTENTICACION, vm.uiState.value.error)
        assertFalse(vm.uiState.value.activo)
        coVerify { repository.desactivar() }
    }

    @Test
    fun `una falla de red al activar deja el formulario para reintentar`() = runTest(dispatcher) {
        coEvery { repository.activar(any(), any()) } returns BiometriaRepository.Resultado.SinConexion
        val vm = crear()
        advanceUntilIdle()
        vm.abrirFormulario()

        vm.confirmarActivacion("Clave1234", autenticador(ResultadoAutenticacion.Exito))
        advanceUntilIdle()

        assertEquals(FaseBiometria.FORMULARIO, vm.uiState.value.fase)
        assertEquals(ErrorAcceso.SIN_CONEXION, vm.uiState.value.error)
    }

    @Test
    fun `desactivar borra la clave local y avisa al servidor`() = runTest(dispatcher) {
        coEvery { repository.consultar() } returns BiometriaRepository.Resultado.Exito(activa)
        every { clave.estado() } returns EstadoClave.VALIDA
        val vm = crear()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.activo)
        assertEquals(MetodoBiometrico.FACIAL, vm.uiState.value.metodoActivo)

        vm.pedirDesactivar()
        assertEquals(FaseBiometria.CONFIRMANDO_DESACTIVAR, vm.uiState.value.fase)
        vm.confirmarDesactivar()
        advanceUntilIdle()

        val estado = vm.uiState.value
        assertFalse(estado.activo)
        assertEquals(AvisoAcceso.DESACTIVADO, estado.aviso)
        verify { clave.borrar() }
        coVerify { repository.desactivar() }
    }

    @Test
    fun `cancelar la desactivacion no toca nada`() = runTest(dispatcher) {
        coEvery { repository.consultar() } returns BiometriaRepository.Resultado.Exito(activa)
        every { clave.estado() } returns EstadoClave.VALIDA
        val vm = crear()
        advanceUntilIdle()
        vm.pedirDesactivar()

        vm.cancelarDesactivar()

        assertEquals(FaseBiometria.ESTADO, vm.uiState.value.fase)
        assertTrue(vm.uiState.value.activo)
        verify(exactly = 0) { clave.borrar() }
    }

    @Test
    fun `si el servidor no confirma la desactivacion la clave local ya se borro y se puede reintentar`() = runTest(dispatcher) {
        coEvery { repository.consultar() } returns BiometriaRepository.Resultado.Exito(activa)
        every { clave.estado() } returns EstadoClave.VALIDA
        val vm = crear()
        advanceUntilIdle()
        coEvery { repository.desactivar() } returns BiometriaRepository.Resultado.SinConexion
        vm.pedirDesactivar()
        vm.confirmarDesactivar()
        advanceUntilIdle()

        assertEquals(ErrorAcceso.SIN_CONEXION, vm.uiState.value.error)
        assertEquals(AccionAcceso.DESACTIVAR, vm.uiState.value.pendiente)
        verify { clave.borrar() }

        coEvery { repository.desactivar() } returns BiometriaRepository.Resultado.Exito(null)
        vm.reintentar()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.activo)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `si cambiaron las biometrias del dispositivo se invalida y se desactiva en el servidor`() = runTest(dispatcher) {
        coEvery { repository.consultar() } returns BiometriaRepository.Resultado.Exito(activa)
        every { clave.estado() } returns EstadoClave.INVALIDADA

        val vm = crear()
        advanceUntilIdle()

        val estado = vm.uiState.value
        assertFalse(estado.activo)
        assertEquals(AvisoAcceso.CLAVE_INVALIDADA, estado.aviso)
        verify { clave.borrar() }
        coVerify { repository.desactivar() }
    }

    @Test
    fun `si el servidor dice activo pero no hay clave local tambien se desactiva`() = runTest(dispatcher) {
        coEvery { repository.consultar() } returns BiometriaRepository.Resultado.Exito(activa)
        every { clave.estado() } returns EstadoClave.AUSENTE

        val vm = crear()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.activo)
        assertEquals(AvisoAcceso.CLAVE_INVALIDADA, vm.uiState.value.aviso)
        coVerify { repository.desactivar() }
    }

    @Test
    fun `una clave huerfana con el servidor inactivo se borra en silencio`() = runTest(dispatcher) {
        every { clave.estado() } returns EstadoClave.VALIDA

        val vm = crear()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.activo)
        assertNull(vm.uiState.value.aviso)
        verify { clave.borrar() }
    }

    @Test
    fun `si la consulta falla se ofrece reintentar y no se muestra como activo`() = runTest(dispatcher) {
        coEvery { repository.consultar() } returns BiometriaRepository.Resultado.TiempoAgotado
        val vm = crear()
        advanceUntilIdle()

        assertEquals(ErrorAcceso.TIEMPO_AGOTADO, vm.uiState.value.error)
        assertEquals(AccionAcceso.CONSULTAR, vm.uiState.value.pendiente)
        assertFalse(vm.uiState.value.activo)

        coEvery { repository.consultar() } returns BiometriaRepository.Resultado.Exito(inactiva)
        vm.reintentar()
        advanceUntilIdle()

        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `sesion vencida se distingue de otras fallas`() = runTest(dispatcher) {
        coEvery { repository.consultar() } returns BiometriaRepository.Resultado.SinSesion
        val vm = crear()
        advanceUntilIdle()

        assertEquals(ErrorAcceso.SESION_VENCIDA, vm.uiState.value.error)
    }
}
