package com.solventa.mobile.verificacion

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sin literales incrustados: toda cadena de es-CO existe en en-US con los mismos marcadores. */
class RecursosIdiomaTest {

    private val regexCadena = Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    private val regexMarcador = Regex("""%\d+\$[sd]""")

    private fun cargar(carpeta: String): Map<String, String> {
        val archivo = File("src/main/res/$carpeta/strings.xml")
        assertTrue("No existe ${archivo.path}", archivo.exists())
        return regexCadena.findAll(archivo.readText()).associate { it.groupValues[1] to it.groupValues[2] }
    }

    @Test
    fun `en-US tiene las mismas cadenas que es-CO`() {
        val base = cargar("values")
        val ingles = cargar("values-en-rUS")

        assertEquals("Faltan en en-US", emptySet<String>(), base.keys - ingles.keys)
        assertEquals("Sobran en en-US", emptySet<String>(), ingles.keys - base.keys)
    }

    @Test
    fun `los marcadores de formato coinciden entre idiomas`() {
        val base = cargar("values")
        val ingles = cargar("values-en-rUS")

        base.forEach { (clave, texto) ->
            val esperados = regexMarcador.findAll(texto).map { it.value }.toSet()
            val reales = regexMarcador.findAll(ingles.getValue(clave)).map { it.value }.toSet()
            assertEquals("Marcadores distintos en $clave", esperados, reales)
        }
    }

    @Test
    fun `ninguna cadena de verificacion esta vacia`() {
        cargar("values").filterKeys { it.startsWith("verificacion_") }.forEach { (clave, texto) ->
            assertTrue("$clave está vacía", texto.isNotBlank())
        }
    }

    private val regexPlural = Regex("""<plurals name="([^"]+)">(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL)
    private val regexCantidad = Regex("""<item quantity="([^"]+)">""")

    private fun cargarPlurales(carpeta: String): Map<String, Set<String>> =
        regexPlural.findAll(File("src/main/res/$carpeta/strings.xml").readText())
            .associate { p -> p.groupValues[1] to regexCantidad.findAll(p.groupValues[2]).map { it.groupValues[1] }.toSet() }

    @Test
    fun `los plurales existen en ambos idiomas con one y other`() {
        val base = cargarPlurales("values")
        val ingles = cargarPlurales("values-en-rUS")

        assertEquals(base.keys, ingles.keys)
        (base + ingles).forEach { (clave, cantidades) ->
            assertTrue("$clave sin one/other", cantidades.containsAll(setOf("one", "other")))
        }
    }
}
