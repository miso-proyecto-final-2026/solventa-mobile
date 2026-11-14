package com.solventa.mobile.biometria

import android.content.Context
import android.content.pm.PackageManager
import androidx.biometric.BiometricManager

/** Métodos que el cliente puede elegir; el código es el que entiende MS Identidad. */
enum class MetodoBiometrico(val codigo: String) {
    HUELLA("HUELLA"),
    FACIAL("FACIAL"),
    ;

    companion object {
        fun desde(codigo: String?): MetodoBiometrico? = values().firstOrNull { it.codigo == codigo }
    }
}

/** Por qué el dispositivo puede o no ofrecer biometría (HU04, criterio 1). */
enum class Disponibilidad { DISPONIBLE, SIN_SENSOR, SIN_REGISTRO, NO_DISPONIBLE }

data class Capacidad(val disponibilidad: Disponibilidad, val metodos: List<MetodoBiometrico>) {
    val puedeActivar: Boolean get() = disponibilidad == Disponibilidad.DISPONIBLE && metodos.isNotEmpty()
}

interface ProveedorCapacidad {
    fun consultar(): Capacidad
}

/**
 * Traduce el resultado de `BiometricManager.canAuthenticate` y los sensores declarados a una [Capacidad]. Separada
 * de Android para poder probarla con valores fijos.
 */
fun clasificarCapacidad(resultadoAndroid: Int, huella: Boolean, facial: Boolean): Capacidad {
    val disponibilidad = when (resultadoAndroid) {
        BiometricManager.BIOMETRIC_SUCCESS -> Disponibilidad.DISPONIBLE
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> Disponibilidad.SIN_SENSOR
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Disponibilidad.SIN_REGISTRO
        else -> Disponibilidad.NO_DISPONIBLE
    }
    if (disponibilidad != Disponibilidad.DISPONIBLE) return Capacidad(disponibilidad, emptyList())
    val metodos = buildList {
        if (huella) add(MetodoBiometrico.HUELLA)
        if (facial) add(MetodoBiometrico.FACIAL)
    }
    // Android dice que hay biometría fuerte pero el fabricante no declaró el sensor: se ofrece huella, la más común.
    return Capacidad(disponibilidad, metodos.ifEmpty { listOf(MetodoBiometrico.HUELLA) })
}

class ProveedorCapacidadAndroid(private val contexto: Context) : ProveedorCapacidad {
    override fun consultar(): Capacidad {
        val paquetes = contexto.packageManager
        val resultado = BiometricManager.from(contexto).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        return clasificarCapacidad(
            resultadoAndroid = resultado,
            huella = paquetes.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT),
            facial = paquetes.hasSystemFeature("android.hardware.biometrics.face") ||
                paquetes.hasSystemFeature("android.hardware.face"),
        )
    }
}
