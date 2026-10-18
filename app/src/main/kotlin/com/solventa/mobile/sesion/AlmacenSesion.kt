package com.solventa.mobile.sesion

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.first

data class Credenciales(val clienteId: String, val accessToken: String)

/**
 * Sesión guardada entre aperturas de la app, para que al reabrir se vea el resultado vigente de la
 * verificación (HU03, criterio 5). Provisional: el almacenamiento seguro y el inicio de sesión
 * biométrico son de HU04/HU05, que pueden reemplazar esta implementación sin tocar a quien la usa.
 */
interface AlmacenSesion {
    suspend fun guardar(credenciales: Credenciales)

    /** `null` si no hay sesión o si no se puede descifrar (por ejemplo, tras restaurar un respaldo). */
    suspend fun leer(): Credenciales?

    suspend fun borrar()

    companion object {
        fun crear(contexto: Context): AlmacenSesion =
            AlmacenSesionDataStore(contexto.applicationContext.datosSesion, CifradorKeystore())
    }
}

/** Un único DataStore por archivo y proceso: el delegado lo garantiza. */
private val Context.datosSesion: DataStore<Preferences> by preferencesDataStore(name = "sesion")

interface Cifrador {
    fun cifrar(texto: String): String

    /** `null` si el texto no se puede descifrar con la clave actual. */
    fun descifrar(cifrado: String): String?
}

class AlmacenSesionDataStore(
    private val datos: DataStore<Preferences>,
    private val cifrador: Cifrador,
) : AlmacenSesion {

    override suspend fun guardar(credenciales: Credenciales) {
        datos.edit {
            it[CLIENTE_ID] = cifrador.cifrar(credenciales.clienteId)
            it[ACCESS_TOKEN] = cifrador.cifrar(credenciales.accessToken)
        }
    }

    override suspend fun leer(): Credenciales? {
        val preferencias = try {
            datos.data.first()
        } catch (e: IOException) {
            return null
        }
        val clienteCifrado = preferencias[CLIENTE_ID] ?: return null
        val tokenCifrado = preferencias[ACCESS_TOKEN] ?: return null
        val clienteId = cifrador.descifrar(clienteCifrado)
        val token = cifrador.descifrar(tokenCifrado)
        if (clienteId == null || token == null) {
            // Datos que ya no se pueden leer: se descartan y el usuario vuelve a empezar.
            borrar()
            return null
        }
        return Credenciales(clienteId, token)
    }

    override suspend fun borrar() {
        datos.edit { it.clear() }
    }

    private companion object {
        val CLIENTE_ID = stringPreferencesKey("cliente_id")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
    }
}

/** AES-GCM con una clave del Android Keystore que nunca sale del dispositivo ni va en los respaldos. */
class CifradorKeystore(private val alias: String = "solventa_sesion") : Cifrador {

    override fun cifrar(texto: String): String {
        val cifrado = Cipher.getInstance(TRANSFORMACION)
        cifrado.init(Cipher.ENCRYPT_MODE, clave())
        val datos = cifrado.doFinal(texto.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cifrado.iv + datos, Base64.NO_WRAP)
    }

    override fun descifrar(cifrado: String): String? =
        try {
            val bytes = Base64.decode(cifrado, Base64.NO_WRAP)
            val descifrado = Cipher.getInstance(TRANSFORMACION)
            descifrado.init(
                Cipher.DECRYPT_MODE,
                clave(),
                GCMParameterSpec(BITS_ETIQUETA, bytes, 0, BYTES_IV),
            )
            String(descifrado.doFinal(bytes, BYTES_IV, bytes.size - BYTES_IV), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }

    private fun clave(): SecretKey {
        val almacen = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (almacen.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMACION = "AES/GCM/NoPadding"
        const val BYTES_IV = 12
        const val BITS_ETIQUETA = 128
    }
}
