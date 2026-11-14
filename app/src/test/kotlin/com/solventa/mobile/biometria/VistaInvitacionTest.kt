package com.solventa.mobile.biometria

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VistaInvitacionTest {

    private val carpeta: File = Files.createTempDirectory("invitacion").toFile()
    private val alcance = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val vista = VistaInvitacionDataStore(
        PreferenceDataStoreFactory.create(scope = alcance) { File(carpeta, "invitacion.preferences_pb") },
    )

    private val disponible = Capacidad(Disponibilidad.DISPONIBLE, listOf(MetodoBiometrico.HUELLA))
    private fun estado(capacidad: Capacidad = disponible, activo: Boolean = false, kyc: Boolean? = true) =
        BiometriaUiState(fase = FaseBiometria.ESTADO, capacidad = capacidad, activo = activo, kycAprobado = kyc)

    @After
    fun limpiar() {
        alcance.cancel()
        carpeta.deleteRecursively()
    }

    @Test
    fun `al principio nadie la ha visto`() = runTest {
        assertFalse(vista.vista("c1"))
    }

    @Test
    fun `marcarla la deja vista solo para ese cliente`() = runTest {
        vista.marcar("c1")

        assertTrue(vista.vista("c1"))
        assertFalse(vista.vista("c2"))
    }

    @Test
    fun `se invita a quien puede y no la ha visto`() {
        assertTrue(debeInvitar(estado(), yaVista = false))
    }

    @Test
    fun `no se invita dos veces`() {
        assertFalse(debeInvitar(estado(), yaVista = true))
    }

    @Test
    fun `no se invita si el dispositivo no puede`() {
        listOf(Disponibilidad.SIN_SENSOR, Disponibilidad.SIN_REGISTRO, Disponibilidad.NO_DISPONIBLE).forEach {
            assertFalse("$it", debeInvitar(estado(Capacidad(it, emptyList())), yaVista = false))
        }
    }

    @Test
    fun `no se invita si ya esta activo`() {
        assertFalse(debeInvitar(estado(activo = true), yaVista = false))
    }

    @Test
    fun `no se invita si el KYC no esta aprobado`() {
        assertFalse(debeInvitar(estado(kyc = false), yaVista = false))
    }

    @Test
    fun `no se invita mientras sigue consultando o si la consulta fallo`() {
        assertFalse(debeInvitar(BiometriaUiState(fase = FaseBiometria.CARGANDO), yaVista = false))
        assertFalse(debeInvitar(BiometriaUiState(fase = FaseBiometria.ESTADO), yaVista = false)) // sin capacidad: no puede
    }

    @Test
    fun `desmarcarla permite volver a invitar solo a ese cliente`() = runTest {
        vista.marcar("c1")
        vista.marcar("c2")

        vista.desmarcar("c1")

        assertFalse(vista.vista("c1"))
        assertTrue(vista.vista("c2"))
    }
}
