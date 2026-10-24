package com.solventa.mobile.navegacion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasoTest {

    @Test
    fun `solo las secciones principales muestran la barra inferior`() {
        val conBarra = Paso.entries.filter { it.conBarra }.toSet()

        assertEquals(setOf(Paso.INICIO, Paso.COMPRA, Paso.CONFIGURACION, Paso.PRIVACIDAD), conBarra)
    }

    @Test
    fun `el alta y la verificacion no muestran la barra`() {
        listOf(Paso.ARRANQUE, Paso.REGISTRO, Paso.CONSENTIMIENTO, Paso.VERIFICACION, Paso.RESULTADO)
            .forEach { assertFalse("$it", it.conBarra) }
    }

    @Test
    fun `inicio es seguros y configuracion con privacidad es configuracion`() {
        assertEquals(Seccion.SEGUROS, Paso.INICIO.seccion)
        assertEquals(Seccion.CONFIGURACION, Paso.CONFIGURACION.seccion)
        assertEquals(Seccion.CONFIGURACION, Paso.PRIVACIDAD.seccion)
        assertNull(Paso.REGISTRO.seccion)
    }

    @Test
    fun `la compra es parte de seguros y atras vuelve al resultado de la cotizacion`() {
        assertEquals(Seccion.SEGUROS, Paso.COMPRA.seccion)
        assertEquals(Paso.INICIO, Paso.COMPRA.atras)
        assertFalse(reiniciaSeguros(desde = Paso.INICIO, hacia = Paso.COMPRA))
        assertFalse(reiniciaSeguros(desde = Paso.COMPRA, hacia = Paso.INICIO))
    }

    @Test
    fun `cambiar de pestana no reinicia la cotizacion en curso`() {
        assertFalse(reiniciaSeguros(desde = Paso.CONFIGURACION, hacia = Paso.INICIO))
        assertFalse(reiniciaSeguros(desde = Paso.PRIVACIDAD, hacia = Paso.INICIO))
        assertFalse(reiniciaSeguros(desde = Paso.INICIO, hacia = Paso.INICIO))
    }

    @Test
    fun `llegar a seguros desde fuera de las secciones empieza una cotizacion nueva`() {
        listOf(Paso.ARRANQUE, Paso.REGISTRO, Paso.CONSENTIMIENTO, Paso.VERIFICACION, Paso.RESULTADO)
            .forEach { assertTrue("$it", reiniciaSeguros(desde = it, hacia = Paso.INICIO)) }
    }

    @Test
    fun `ir a otro destino nunca reinicia seguros`() {
        assertFalse(reiniciaSeguros(desde = Paso.REGISTRO, hacia = Paso.CONSENTIMIENTO))
        assertFalse(reiniciaSeguros(desde = Paso.INICIO, hacia = Paso.CONFIGURACION))
    }

    @Test
    fun `volver a la raiz de cada seccion desde adentro`() {
        assertEquals(Paso.CONFIGURACION, Paso.PRIVACIDAD.atras)
        assertEquals(Paso.INICIO, Paso.CONFIGURACION.atras)
        assertNull("INICIO deja que el sistema cierre la app", Paso.INICIO.atras)
        assertNull(Paso.REGISTRO.atras)
    }
}
