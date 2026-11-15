package com.solventa.mobile.polizas

import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.solventa.mobile.sesion.Cifrador
import com.solventa.mobile.sesion.CifradorKeystore
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Dónde vive la copia de «Mis pólizas» sin conexión (HU36, criterio 7): en el almacenamiento privado de la app, cifrada
 * con una clave del Android Keystore y fuera de los respaldos (`res/xml/reglas_respaldo.xml`). Android la borra al
 * desinstalar; la app, al cerrar sesión y al revocar el consentimiento.
 */
interface AlmacenPolizas {
    /** La copia del cliente, o `null` si no hay, no se puede leer o es de otro cliente (en esos dos casos se borra). */
    suspend fun leer(clienteId: String): CopiaPolizas?

    suspend fun guardar(copia: CopiaPolizas)

    /** Borra la copia y olvida los documentos descargados (el archivo del cliente no se toca: es suyo). */
    suspend fun borrar()

    companion object {
        fun crear(contexto: Context): AlmacenPolizas {
            val app = contexto.applicationContext
            return AlmacenPolizasDataStore(app.datosPolizas, CifradorKeystore(ALIAS_CLAVE)) { liberarDocumentos(app) }
        }

        /** Sin copia: para las pruebas y para quien no necesita guardar nada. */
        val Ninguno: AlmacenPolizas = object : AlmacenPolizas {
            override suspend fun leer(clienteId: String): CopiaPolizas? = null
            override suspend fun guardar(copia: CopiaPolizas) = Unit
            override suspend fun borrar() = Unit
        }

        private const val ALIAS_CLAVE = "solventa_polizas"
    }
}

/** Un único DataStore por archivo y proceso. El nombre lo excluyen las reglas de respaldo. */
private val Context.datosPolizas: DataStore<Preferences> by preferencesDataStore(name = "polizas_sin_conexion")

/**
 * Suelta los permisos persistentes sobre los PDF que el cliente guardó (criterio 2). La app no toma permisos
 * persistentes sobre ningún otro archivo, así que se sueltan todos.
 */
private fun liberarDocumentos(contexto: Context) {
    val resolver = contexto.contentResolver
    resolver.persistedUriPermissions.forEach { permiso ->
        runCatching { resolver.releasePersistableUriPermission(permiso.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
}

class AlmacenPolizasDataStore(
    private val datos: DataStore<Preferences>,
    private val cifrador: Cifrador,
    private val alBorrar: () -> Unit = {},
) : AlmacenPolizas {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun leer(clienteId: String): CopiaPolizas? {
        val cifrada = try {
            datos.data.first()[COPIA]
        } catch (e: IOException) {
            return null
        } ?: return null
        val copia = cifrador.descifrar(cifrada)?.let { texto ->
            try {
                json.decodeFromString(CopiaPolizas.serializer(), texto)
            } catch (e: SerializationException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }
        // Ilegible (por ejemplo, tras restaurar en otro teléfono) o de otro cliente: no se muestra y se descarta.
        if (copia == null || copia.clienteId != clienteId) {
            borrar()
            return null
        }
        return copia
    }

    override suspend fun guardar(copia: CopiaPolizas) {
        val cifrada = cifrador.cifrar(json.encodeToString(CopiaPolizas.serializer(), copia))
        datos.edit { it[COPIA] = cifrada }
    }

    override suspend fun borrar() {
        datos.edit { it.clear() }
        alBorrar()
    }

    private companion object {
        val COPIA = stringPreferencesKey("copia")
    }
}
