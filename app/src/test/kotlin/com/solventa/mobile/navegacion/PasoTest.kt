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

        assertEquals(setOf(Paso.INICIO, Paso.COMPRA, Paso.FIRMA, Paso.POLIZAS, Paso.POLIZA_DETALLE, Paso.PERFIL, Paso.SEGURIDAD), conBarra)
    }

    @Test
    fun `el alta y la verificacion no muestran la barra`() {
        listOf(Paso.ARRANQUE, Paso.REGISTRO, Paso.CONSENTIMIENTO, Paso.VERIFICACION, Paso.RESULTADO)
            .forEach { assertFalse("$it", it.conBarra) }
    }

    @Test
    fun `inicio es seguros y perfil es perfil`() {
        assertEquals(Seccion.SEGUROS, Paso.INICIO.seccion)
        assertEquals(Seccion.PERFIL, Paso.PERFIL.seccion)
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
    fun `la firma es parte de seguros y atras vuelve al inicio`() {
        assertEquals(Seccion.SEGUROS, Paso.FIRMA.seccion)
        assertEquals(Paso.INICIO, Paso.FIRMA.atras)
        assertFalse(reiniciaSeguros(desde = Paso.COMPRA, hacia = Paso.FIRMA))
        assertFalse(reiniciaSeguros(desde = Paso.FIRMA, hacia = Paso.INICIO))
    }

    @Test
    fun `la lista y el detalle de polizas son la seccion polizas y atras sube un nivel`() {
        assertEquals(Seccion.POLIZAS, Paso.POLIZAS.seccion)
        assertEquals(Seccion.POLIZAS, Paso.POLIZA_DETALLE.seccion)
        assertEquals(Paso.POLIZAS, Paso.POLIZA_DETALLE.atras)
        assertEquals(Paso.INICIO, Paso.POLIZAS.atras)
    }

    @Test
    fun `ir a polizas o abrir un detalle nunca reinicia la cotizacion`() {
        assertFalse(reiniciaSeguros(desde = Paso.INICIO, hacia = Paso.POLIZAS))
        assertFalse(reiniciaSeguros(desde = Paso.POLIZAS, hacia = Paso.INICIO))
        assertFalse(reiniciaSeguros(desde = Paso.POLIZAS, hacia = Paso.POLIZA_DETALLE))
    }

    @Test
    fun `cambiar de pestana no reinicia la cotizacion en curso`() {
        assertFalse(reiniciaSeguros(desde = Paso.PERFIL, hacia = Paso.INICIO))
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
        assertFalse(reiniciaSeguros(desde = Paso.INICIO, hacia = Paso.PERFIL))
    }

    @Test
    fun `volver a la raiz de cada seccion desde adentro`() {
        assertEquals(Paso.INICIO, Paso.PERFIL.atras)
        assertNull("INICIO deja que el sistema cierre la app", Paso.INICIO.atras)
        assertNull(Paso.REGISTRO.atras)
    }

    @Test
    fun `seguridad es parte de perfil y atras vuelve a perfil sin reiniciar la cotizacion`() {
        assertEquals(Seccion.PERFIL, Paso.SEGURIDAD.seccion)
        assertEquals(Paso.PERFIL, Paso.SEGURIDAD.atras)
        assertFalse(reiniciaSeguros(desde = Paso.PERFIL, hacia = Paso.SEGURIDAD))
    }

    @Test
    fun `la invitacion biometrica no muestra la barra y llega desde fuera de las secciones`() {
        assertFalse(Paso.INVITACION_BIOMETRICA.conBarra)
        assertNull(Paso.INVITACION_BIOMETRICA.atras)
        // Al salir de la invitación hacia Inicio empieza una cotización nueva, igual que desde el resultado del KYC.
        assertTrue(reiniciaSeguros(desde = Paso.INVITACION_BIOMETRICA, hacia = Paso.INICIO))
    }

    @Test
    fun `el inicio de sesion y el bloqueo no muestran la barra y atras del login vuelve al registro`() {
        assertFalse(Paso.INICIO_SESION.conBarra)
        assertFalse(Paso.BLOQUEO.conBarra)
        assertEquals(Paso.REGISTRO, Paso.INICIO_SESION.atras)
        assertNull(Paso.BLOQUEO.atras) // atrás en el bloqueo no deja entrar: sale de la app
    }
}
