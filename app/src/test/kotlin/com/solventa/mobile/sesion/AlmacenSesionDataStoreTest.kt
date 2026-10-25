package com.solventa.mobile.sesion

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** El cifrado real (Android Keystore) se prueba en el dispositivo: ver CifradorKeystoreTest. */
class AlmacenSesionDataStoreTest {

    private val carpeta: File = Files.createTempDirectory("sesion").toFile()
    private val alcance = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val archivo = File(carpeta, "sesion.preferences_pb")
    private val datos = PreferenceDataStoreFactory.create(scope = alcance) { archivo }

    /** Invierte el texto: reversible y distinto del original, suficiente para la prueba. */
    private class CifradorInverso(var roto: Boolean = false) : Cifrador {
        override fun cifrar(texto: String) = "x" + texto.reversed()
        override fun descifrar(cifrado: String) = if (roto) null else cifrado.drop(1).reversed()
    }

    private val cifrador = CifradorInverso()
    private val almacen = AlmacenSesionDataStore(datos, cifrador)

    @After
    fun limpiar() {
        alcance.cancel()
        carpeta.deleteRecursively()
    }

    @Test
    fun `sin nada guardado no hay sesion`() = runTest {
        assertNull(almacen.leer())
    }

    @Test
    fun `lo guardado se recupera al reabrir`() = runTest {
        almacen.guardar(Credenciales("c1", "token-123"))

        assertEquals(Credenciales("c1", "token-123"), almacen.leer())
    }

    @Test
    fun `en disco nunca queda el token en claro`() = runTest {
        almacen.guardar(Credenciales("c1", "token-secreto-123"))

        assertFalse(String(archivo.readBytes(), Charsets.ISO_8859_1).contains("token-secreto-123"))
    }

    @Test
    fun `borrar elimina la sesion`() = runTest {
        almacen.guardar(Credenciales("c1", "token-123"))

        almacen.borrar()

        assertNull(almacen.leer())
    }

    @Test
    fun `si ya no se puede descifrar se descarta y se borra`() = runTest {
        almacen.guardar(Credenciales("c1", "token-123"))
        cifrador.roto = true

        assertNull(almacen.leer())

        cifrador.roto = false
        assertNull(almacen.leer())
    }

    @Test
    fun `el nombre se recupera al reabrir`() = runTest {
        almacen.guardar(Credenciales("c1", "token-123", "Ana María"))

        assertEquals(Credenciales("c1", "token-123", "Ana María"), almacen.leer())
    }

    @Test
    fun `en disco tampoco queda el nombre en claro`() = runTest {
        almacen.guardar(Credenciales("c1", "token-123", "Nombre Secreto"))

        assertFalse(String(archivo.readBytes(), Charsets.ISO_8859_1).contains("Nombre Secreto"))
    }

    @Test
    fun `una sesion guardada sin nombre sigue siendo valida`() = runTest {
        almacen.guardar(Credenciales("c1", "token-123"))

        assertEquals(Credenciales("c1", "token-123", null), almacen.leer())
    }

    @Test
    fun `un nombre que ya no se puede leer no descarta la sesion`() = runTest {
        almacen.guardar(Credenciales("c1", "token-123", "Ana"))
        // Solo el nombre queda ilegible: la sesión (cliente y token) sigue siendo utilizable.
        datos.edit { it[androidx.datastore.preferences.core.stringPreferencesKey("nombre")] = "ilegible" }
        val cifradorSelectivo = object : Cifrador {
            override fun cifrar(texto: String) = cifrador.cifrar(texto)
            override fun descifrar(cifrado: String) = if (cifrado == "ilegible") null else cifrador.descifrar(cifrado)
        }

        assertEquals(Credenciales("c1", "token-123", null), AlmacenSesionDataStore(datos, cifradorSelectivo).leer())
    }
}
