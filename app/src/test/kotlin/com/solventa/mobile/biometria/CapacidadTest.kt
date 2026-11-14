package com.solventa.mobile.biometria

import androidx.biometric.BiometricManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapacidadTest {

    @Test
    fun `con biometria fuerte ofrece los metodos que el telefono declara`() {
        val capacidad = clasificarCapacidad(BiometricManager.BIOMETRIC_SUCCESS, huella = true, facial = true)

        assertEquals(Disponibilidad.DISPONIBLE, capacidad.disponibilidad)
        assertEquals(listOf(MetodoBiometrico.HUELLA, MetodoBiometrico.FACIAL), capacidad.metodos)
        assertTrue(capacidad.puedeActivar)
    }

    @Test
    fun `solo con rostro solo ofrece rostro`() {
        val capacidad = clasificarCapacidad(BiometricManager.BIOMETRIC_SUCCESS, huella = false, facial = true)

        assertEquals(listOf(MetodoBiometrico.FACIAL), capacidad.metodos)
    }

    @Test
    fun `si Android dice que hay biometria pero no declara el sensor ofrece huella`() {
        val capacidad = clasificarCapacidad(BiometricManager.BIOMETRIC_SUCCESS, huella = false, facial = false)

        assertEquals(listOf(MetodoBiometrico.HUELLA), capacidad.metodos)
    }

    @Test
    fun `sin sensor no se ofrece nada y se explica el motivo`() {
        val capacidad = clasificarCapacidad(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE, huella = true, facial = false)

        assertEquals(Disponibilidad.SIN_SENSOR, capacidad.disponibilidad)
        assertTrue(capacidad.metodos.isEmpty())
        assertFalse(capacidad.puedeActivar)
    }

    @Test
    fun `con sensor pero sin registro biometrico en el sistema no se ofrece`() {
        val capacidad = clasificarCapacidad(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED, huella = true, facial = false)

        assertEquals(Disponibilidad.SIN_REGISTRO, capacidad.disponibilidad)
        assertFalse(capacidad.puedeActivar)
    }

    @Test
    fun `sensor ocupado o con actualizacion pendiente es no disponible`() {
        listOf(
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED,
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN,
        ).forEach {
            assertEquals("$it", Disponibilidad.NO_DISPONIBLE, clasificarCapacidad(it, huella = true, facial = false).disponibilidad)
        }
    }

    @Test
    fun `el metodo se reconoce por el codigo del servidor`() {
        assertEquals(MetodoBiometrico.FACIAL, MetodoBiometrico.desde("FACIAL"))
        assertEquals(null, MetodoBiometrico.desde("IRIS"))
        assertEquals(null, MetodoBiometrico.desde(null))
    }
}
