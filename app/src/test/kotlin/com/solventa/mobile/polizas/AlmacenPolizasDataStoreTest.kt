package com.solventa.mobile.polizas

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.solventa.mobile.sesion.Cifrador
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** HU36, criterio 7. El cifrado real (Android Keystore) es el mismo de la sesión y se prueba en el dispositivo. */
class AlmacenPolizasDataStoreTest {

    private val carpeta: File = Files.createTempDirectory("polizas").toFile()
    private val alcance = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val archivo = File(carpeta, "polizas_sin_conexion.preferences_pb")
    private val datos = PreferenceDataStoreFactory.create(scope = alcance) { archivo }

    /** Invierte el texto: reversible y distinto del original, suficiente para la prueba. */
    private class CifradorInverso(var roto: Boolean = false) : Cifrador {
        override fun cifrar(texto: String) = "x" + texto.reversed()
        override fun descifrar(cifrado: String) = if (roto) null else cifrado.drop(1).reversed()
    }

    private val cifrador = CifradorInverso()
    private var documentosLiberados = 0
    private val almacen = AlmacenPolizasDataStore(datos, cifrador) { documentosLiberados++ }

    private val copia = CopiaPolizas(
        clienteId = "c1",
        sincronizadoEn = "2026-11-14T15:30:00Z",
        polizas = listOf(
            PolizaGuardada(
                id = "p1", suscripcionId = "s1", numeroPoliza = "SOL-NUMERO-UNICO", producto = "microseguro_vida",
                estado = "VIGENTE", vigenciaDesde = "2026-06-12T00:00:00Z", fechaVencimiento = "2027-06-12T00:00:00Z",
                coberturas = listOf(CoberturaGuardada("fallecimiento", 50_000_000)),
            ),
        ),
        documentos = mapOf("s1/CERTIFICADO" to "content://docs/certificado"),
    )

    @After
    fun limpiar() {
        alcance.cancel()
        carpeta.deleteRecursively()
    }

    @Test
    fun `dos actualizaciones que se cruzan no se pisan`() = runTest {
        almacen.guardar(copia.copy(documentos = emptyMap()))
        val leyo = CompletableDeferred<Unit>()
        val seguir = CompletableDeferred<Unit>()

        // La sincronización lee la copia y se detiene antes de escribir...
        val sincronizacion = async(Dispatchers.IO) {
            almacen.actualizar("c1") { anterior ->
                leyo.complete(Unit)
                seguir.await()
                anterior!!.copy(sincronizadoEn = "2026-11-15T16:00:00Z")
            }
        }
        leyo.await()
        // ...mientras se guarda el URI de un PDF. Debe esperar a que la sincronización termine y partir de su resultado.
        val documento = async(Dispatchers.IO) {
            almacen.actualizar("c1") { it?.conDocumento("s1/CERTIFICADO", "content://docs/certificado") }
        }
        seguir.complete(Unit)
        sincronizacion.await()
        documento.await()

        val final = almacen.leer("c1")!!
        assertEquals("2026-11-15T16:00:00Z", final.sincronizadoEn)
        assertEquals(mapOf("s1/CERTIFICADO" to "content://docs/certificado"), final.documentos)
    }

    @Test
    fun `actualizar sin copia del cliente recibe null y devolver null no escribe nada`() = runTest {
        almacen.guardar(copia.copy(clienteId = "otro"))

        val resultado = almacen.actualizar("c1") { anterior ->
            assertNull(anterior)
            null
        }

        assertNull(resultado)
        assertEquals("otro", almacen.leer("otro")!!.clienteId)
    }

    @Test
    fun `sin nada guardado no hay copia`() = runTest {
        assertNull(almacen.leer("c1"))
    }

    @Test
    fun `la copia guardada se recupera con sus documentos`() = runTest {
        almacen.guardar(copia)

        assertEquals(copia, almacen.leer("c1"))
    }

    @Test
    fun `en disco no queda nada en claro`() = runTest {
        almacen.guardar(copia)

        val enDisco = String(archivo.readBytes(), Charsets.ISO_8859_1)
        assertFalse(enDisco.contains("SOL-NUMERO-UNICO"))
        assertFalse(enDisco.contains("content://docs"))
    }

    @Test
    fun `guardar de nuevo reemplaza la copia anterior`() = runTest {
        almacen.guardar(copia)
        val nueva = copia.copy(sincronizadoEn = "2026-11-15T08:00:00Z", polizas = emptyList(), documentos = emptyMap())

        almacen.guardar(nueva)

        assertEquals(nueva, almacen.leer("c1"))
    }

    @Test
    fun `borrar elimina la copia y suelta los documentos`() = runTest {
        almacen.guardar(copia)

        almacen.borrar()

        assertNull(almacen.leer("c1"))
        assertEquals(1, documentosLiberados)
    }

    @Test
    fun `la copia de otro cliente no se muestra y se borra`() = runTest {
        almacen.guardar(copia)

        assertNull(almacen.leer("otro"))
        assertNull(almacen.leer("c1"))
    }

    @Test
    fun `si ya no se puede descifrar se descarta y se borra`() = runTest {
        almacen.guardar(copia)
        cifrador.roto = true

        assertNull(almacen.leer("c1"))

        cifrador.roto = false
        assertNull(almacen.leer("c1"))
    }

    @Test
    fun `un contenido que no es una copia valida se descarta`() = runTest {
        datos.edit { it[stringPreferencesKey("copia")] = cifrador.cifrar("{no es json") }

        assertNull(almacen.leer("c1"))
    }
}
