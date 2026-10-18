package com.solventa.mobile.sesion

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
}
