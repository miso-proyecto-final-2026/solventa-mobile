package com.solventa.mobile.cotizacion

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Los campos y rangos replican `reglas/v1.yaml` de MS Cotización: si cambian allí, cambian aquí. */
class ProductosTest {

    private val vida = Catalogo.porId("microseguro_vida")!!
    private val dispositivos = Catalogo.porId("proteccion_dispositivos")!!

    private fun entero(producto: Producto, clave: String) =
        producto.campos.filterIsInstance<Campo.Entero>().first { it.clave == clave }

    @Test
    fun `el catalogo ofrece los productos del motor`() {
        assertEquals(listOf("microseguro_vida", "proteccion_dispositivos"), Catalogo.productos.map { it.id })
        assertNull(Catalogo.porId("inexistente"))
    }

    @Test
    fun `cada producto pide solo los datos que necesita`() {
        assertEquals(listOf("edad", "suma_asegurada"), vida.campos.map { it.clave })
        assertEquals(
            listOf("tipo_dispositivo", "valor_dispositivo", "antiguedad_meses"),
            dispositivos.campos.map { it.clave },
        )
    }

    @Test
    fun `los rangos coinciden con las reglas del motor`() {
        assertEquals(18L..65L, entero(vida, "edad").let { it.min..it.max })
        assertEquals(5_000_000L..100_000_000L, entero(vida, "suma_asegurada").let { it.min..it.max })
        assertEquals(300_000L..15_000_000L, entero(dispositivos, "valor_dispositivo").let { it.min..it.max })
        assertEquals(0L..36L, entero(dispositivos, "antiguedad_meses").let { it.min..it.max })
        val tipo = dispositivos.campos.filterIsInstance<Campo.Seleccion>().single()
        assertEquals(listOf("celular", "portatil", "tableta"), tipo.opciones.map { it.valor })
    }

    @Test
    fun `un entero valido no tiene error y admite espacios alrededor`() {
        val edad = entero(vida, "edad")
        assertNull(edad.validar("18"))
        assertNull(edad.validar("65"))
        assertNull(edad.validar("  30 "))
    }

    @Test
    fun `los enteros se rechazan con el motivo correcto`() {
        val edad = entero(vida, "edad")
        assertEquals(ErrorCampo.REQUERIDO, edad.validar(""))
        assertEquals(ErrorCampo.REQUERIDO, edad.validar("   "))
        assertEquals(ErrorCampo.NO_ENTERO, edad.validar("abc"))
        assertEquals(ErrorCampo.NO_ENTERO, edad.validar("30.5"))
        assertEquals(ErrorCampo.MENOR_AL_MINIMO, edad.validar("17"))
        assertEquals(ErrorCampo.MAYOR_AL_MAXIMO, edad.validar("66"))
    }

    @Test
    fun `un numero demasiado grande no rompe la validacion`() {
        assertEquals(ErrorCampo.NO_ENTERO, entero(vida, "suma_asegurada").validar("99999999999999999999999"))
    }

    @Test
    fun `la seleccion exige una de las opciones`() {
        val tipo = dispositivos.campos.filterIsInstance<Campo.Seleccion>().single()
        assertNull(tipo.validar("celular"))
        assertEquals(ErrorCampo.REQUERIDO, tipo.validar(""))
        assertEquals(ErrorCampo.OPCION_INVALIDA, tipo.validar("reloj"))
    }

    @Test
    fun `validar un producto devuelve un error por cada campo invalido`() {
        val errores = vida.validar(mapOf("edad" to "10"))

        assertEquals(ErrorCampo.MENOR_AL_MINIMO, errores["edad"])
        assertEquals(ErrorCampo.REQUERIDO, errores["suma_asegurada"])
        assertEquals(emptyMap<String, ErrorCampo>(), vida.validar(mapOf("edad" to "30", "suma_asegurada" to "50000000")))
    }

    @Test
    fun `los datos se envian con el tipo que espera el servidor`() {
        val datos = dispositivos.datos(
            mapOf("tipo_dispositivo" to "celular", "valor_dispositivo" to " 2500000 ", "antiguedad_meses" to "10"),
        )

        assertEquals(JsonPrimitive("celular"), datos["tipo_dispositivo"])
        assertEquals(JsonPrimitive(2_500_000L), datos["valor_dispositivo"])
        assertEquals(JsonPrimitive(10L), datos["antiguedad_meses"])
        assertNotNull(datos["valor_dispositivo"]?.longOrNullValue())
    }

    private fun JsonPrimitive.longOrNullValue(): Long? = content.toLongOrNull()
}
