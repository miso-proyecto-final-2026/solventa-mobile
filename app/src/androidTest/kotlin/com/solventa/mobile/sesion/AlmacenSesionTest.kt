package com.solventa.mobile.sesion

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Cifrado real con el Android Keystore y el almacén que usa la app (HU03, criterio 5). */
class AlmacenSesionTest {

    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext
    private val almacen = AlmacenSesion.crear(contexto)

    @After
    fun limpiar() = runBlocking { almacen.borrar() }

    @Test
    fun cifraYDescifraConLaClaveDelKeystore() {
        val cifrador = CifradorKeystore(alias = "solventa_sesion_prueba")

        val cifrado = cifrador.cifrar("token-123")

        assertNotEquals("token-123", cifrado)
        assertEquals("token-123", cifrador.descifrar(cifrado))
    }

    @Test
    fun unTextoAlteradoNoSeDescifra() {
        val cifrador = CifradorKeystore(alias = "solventa_sesion_prueba")

        assertNull(cifrador.descifrar("no-es-un-cifrado"))
    }

    @Test
    fun laSesionGuardadaSeRecuperaConOtraInstancia() = runBlocking {
        almacen.guardar(Credenciales("c1", "token-123"))

        // Una instancia nueva, como la que crea MainActivity al reabrir la app.
        assertEquals(Credenciales("c1", "token-123"), AlmacenSesion.crear(contexto).leer())
    }
}
