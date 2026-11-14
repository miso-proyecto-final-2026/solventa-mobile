package com.solventa.mobile.biometria

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

enum class EstadoClave { AUSENTE, VALIDA, INVALIDADA }

/**
 * Clave local del acceso biométrico (HU04). Vive en el Android Keystore y solo se puede usar tras una autenticación
 * biométrica; la biometría nunca sale del dispositivo. Si el sistema cambia sus biometrías registradas, la clave
 * queda invalidada (criterio 8). HU05 la usará para desbloquear la sesión guardada.
 */
interface ClaveBiometrica {
    fun estado(): EstadoClave

    /** Crea una clave nueva (reemplaza la anterior) y devuelve el cifrador que se entrega al diálogo biométrico. */
    fun crearParaAutenticar(): Cipher

    /** Idempotente: borra la clave si existe. */
    fun borrar()
}

class ClaveBiometricaKeystore(private val alias: String = "solventa_biometria") : ClaveBiometrica {

    override fun estado(): EstadoClave {
        val clave = try {
            val almacen = almacen()
            if (!almacen.containsAlias(alias)) return EstadoClave.AUSENTE
            almacen.getKey(alias, null) as? SecretKey ?: return EstadoClave.AUSENTE
        } catch (e: GeneralSecurityException) {
            return EstadoClave.INVALIDADA
        }
        return try {
            cifrador(clave)
            EstadoClave.VALIDA
        } catch (e: KeyPermanentlyInvalidatedException) {
            EstadoClave.INVALIDADA
        } catch (e: GeneralSecurityException) {
            EstadoClave.INVALIDADA
        }
    }

    override fun crearParaAutenticar(): Cipher {
        borrar()
        val especificacion = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                }
            }
            .build()
        val clave = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            .apply { init(especificacion) }
            .generateKey()
        return cifrador(clave)
    }

    override fun borrar() {
        try {
            val almacen = almacen()
            if (almacen.containsAlias(alias)) almacen.deleteEntry(alias)
        } catch (e: GeneralSecurityException) {
            // Sin acceso al almacén no hay nada que se pueda borrar.
        }
    }

    private fun almacen(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun cifrador(clave: SecretKey): Cipher =
        Cipher.getInstance(TRANSFORMACION).apply { init(Cipher.ENCRYPT_MODE, clave) }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMACION = "AES/GCM/NoPadding"
    }
}
