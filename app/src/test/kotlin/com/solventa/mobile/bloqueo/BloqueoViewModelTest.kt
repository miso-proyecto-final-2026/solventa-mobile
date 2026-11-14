package com.solventa.mobile.bloqueo

import android.security.keystore.KeyPermanentlyInvalidatedException
import com.solventa.mobile.biometria.Autenticador
import com.solventa.mobile.biometria.ClaveBiometrica
import com.solventa.mobile.biometria.EstadoClave
import com.solventa.mobile.biometria.ResultadoAutenticacion
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.security.KeyStoreException
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
class BloqueoViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val clave = mockk<ClaveBiometrica>(relaxed = true)
    private val cifrador = mockk<Cipher>()

    @Before
    fun antes() {
        Dispatchers.setMain(dispatcher)
        every { clave.estado() } returns EstadoClave.VALIDA
        every { clave.cifradorExistente() } returns cifrador
    }

    @After
    fun despues() = Dispatchers.resetMain()

    /** Autenticador simulado: llama a `alFallar` el número de veces dado y responde. */
    private fun autenticador(fallos: Int = 0, respuesta: ResultadoAutenticacion) =
        Autenticador { _, alFallar ->
            repeat(fallos) { alFallar() }
            respuesta
        }

    @Test
    fun `al abrir con la clave valida espera la biometria sin mostrar contrasena`() {
        val vm = BloqueoViewModel(clave)

        assertEquals(FaseBloqueo.LISTO, vm.uiState.value.fase)
        assertEquals(3, vm.uiState.value.intentosRestantes)
    }

    @Test
    fun `una autenticacion correcta desbloquea sin pedir la contrasena`() = runTest(dispatcher) {
        val vm = BloqueoViewModel(clave)

        vm.autenticar(autenticador(respuesta = ResultadoAutenticacion.Exito))
        advanceUntilIdle()

        assertEquals(FaseBloqueo.DESBLOQUEADO, vm.uiState.value.fase)
        assertNull(vm.uiState.value.motivo)
    }

    @Test
    fun `tres fallos piden la contrasena`() = runTest(dispatcher) {
        val vm = BloqueoViewModel(clave)

        vm.autenticar(autenticador(fallos = 3, respuesta = ResultadoAutenticacion.Agotado))
        advanceUntilIdle()

        val estado = vm.uiState.value
        assertEquals(FaseBloqueo.CONTRASENA, estado.fase)
        assertEquals(MotivoContrasena.TRES_FALLOS, estado.motivo)
        assertEquals(3, estado.fallidos)
        assertEquals(0, estado.intentosRestantes)
    }

    @Test
    fun `un fallo seguido de un acierto desbloquea y deja la cuenta de fallos`() = runTest(dispatcher) {
        val vm = BloqueoViewModel(clave)

        vm.autenticar(autenticador(fallos = 1, respuesta = ResultadoAutenticacion.Exito))
        advanceUntilIdle()

        assertEquals(FaseBloqueo.DESBLOQUEADO, vm.uiState.value.fase)
        assertEquals(1, vm.uiState.value.fallidos)
    }

    @Test
    fun `cancelar el dialogo deja el bloqueo para reintentar o elegir la contrasena`() = runTest(dispatcher) {
        val vm = BloqueoViewModel(clave)

        vm.autenticar(autenticador(respuesta = ResultadoAutenticacion.Cancelado))
        advanceUntilIdle()

        val estado = vm.uiState.value
        assertEquals(FaseBloqueo.LISTO, estado.fase)
        assertTrue(estado.cancelado)
    }

    @Test
    fun `los fallos se acumulan aunque se cancele y se reintente`() = runTest(dispatcher) {
        val vm = BloqueoViewModel(clave)

        vm.autenticar(autenticador(fallos = 2, respuesta = ResultadoAutenticacion.Cancelado))
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.intentosRestantes)

        vm.autenticar(autenticador(fallos = 1, respuesta = ResultadoAutenticacion.Agotado))
        advanceUntilIdle()

        assertEquals(MotivoContrasena.TRES_FALLOS, vm.uiState.value.motivo)
        assertEquals(3, vm.uiState.value.fallidos)
    }

    @Test
    fun `reintentar despues de cancelar quita el aviso de cancelado`() = runTest(dispatcher) {
        val vm = BloqueoViewModel(clave)
        vm.autenticar(autenticador(respuesta = ResultadoAutenticacion.Cancelado))
        advanceUntilIdle()

        vm.autenticar(autenticador(respuesta = ResultadoAutenticacion.Exito))
        advanceUntilIdle()

        assertFalse(vm.uiState.value.cancelado)
        assertEquals(FaseBloqueo.DESBLOQUEADO, vm.uiState.value.fase)
    }

    @Test
    fun `un error del sistema, como el bloqueo del sensor, pide la contrasena`() = runTest(dispatcher) {
        val vm = BloqueoViewModel(clave)

        vm.autenticar(autenticador(respuesta = ResultadoAutenticacion.Error))
        advanceUntilIdle()

        assertEquals(FaseBloqueo.CONTRASENA, vm.uiState.value.fase)
        assertEquals(MotivoContrasena.ERROR, vm.uiState.value.motivo)
    }

    @Test
    fun `elegir la contrasena la pide sin tocar la biometria`() {
        val vm = BloqueoViewModel(clave)

        vm.usarContrasena()

        assertEquals(FaseBloqueo.CONTRASENA, vm.uiState.value.fase)
        assertEquals(MotivoContrasena.ELEGIDA, vm.uiState.value.motivo)
        assertFalse(vm.cambioDeBiometria)
    }

    @Test
    fun `si la clave ya estaba invalidada al abrir se borra y se exige la contrasena`() {
        every { clave.estado() } returns EstadoClave.INVALIDADA

        val vm = BloqueoViewModel(clave)

        assertEquals(FaseBloqueo.CONTRASENA, vm.uiState.value.fase)
        assertEquals(MotivoContrasena.BIOMETRIA_CAMBIO, vm.uiState.value.motivo)
        assertTrue(vm.cambioDeBiometria)
        verify { clave.borrar() }
    }

    @Test
    fun `si la clave se invalida al usarla tambien se exige la contrasena y se invita a reactivar`() = runTest(dispatcher) {
        every { clave.cifradorExistente() } throws mockk<KeyPermanentlyInvalidatedException>(relaxed = true)
        val vm = BloqueoViewModel(clave)

        vm.autenticar(autenticador(respuesta = ResultadoAutenticacion.Exito))
        advanceUntilIdle()

        assertEquals(MotivoContrasena.BIOMETRIA_CAMBIO, vm.uiState.value.motivo)
        assertTrue(vm.cambioDeBiometria)
        verify { clave.borrar() }
    }

    @Test
    fun `si no se puede abrir la clave por otro motivo se pide la contrasena sin decir que cambiaron las biometrias`() = runTest(dispatcher) {
        every { clave.cifradorExistente() } throws KeyStoreException("sin clave")
        val vm = BloqueoViewModel(clave)

        vm.autenticar(autenticador(respuesta = ResultadoAutenticacion.Exito))
        advanceUntilIdle()

        assertEquals(MotivoContrasena.ERROR, vm.uiState.value.motivo)
        assertFalse(vm.cambioDeBiometria)
    }

    @Test
    fun `no se puede autenticar dos veces a la vez ni despues de pedir la contrasena`() = runTest(dispatcher) {
        val vm = BloqueoViewModel(clave)
        vm.usarContrasena()

        vm.autenticar(autenticador(respuesta = ResultadoAutenticacion.Exito))
        advanceUntilIdle()

        assertEquals(FaseBloqueo.CONTRASENA, vm.uiState.value.fase)
    }
}
