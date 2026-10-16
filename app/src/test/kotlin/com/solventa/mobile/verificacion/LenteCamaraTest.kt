package com.solventa.mobile.verificacion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LenteCamaraTest {

    @Test
    fun `usa la camara preferida cuando existe`() {
        assertEquals(LenteCamara.FRONTAL, resolverLente(LenteCamara.FRONTAL, hayFrontal = true, hayTrasera = true))
        assertEquals(LenteCamara.TRASERA, resolverLente(LenteCamara.TRASERA, hayFrontal = true, hayTrasera = true))
    }

    @Test
    fun `sin camara frontal la selfie usa la trasera en lugar de bloquearse`() {
        assertEquals(LenteCamara.TRASERA, resolverLente(LenteCamara.FRONTAL, hayFrontal = false, hayTrasera = true))
    }

    @Test
    fun `sin camara trasera el documento usa la frontal`() {
        assertEquals(LenteCamara.FRONTAL, resolverLente(LenteCamara.TRASERA, hayFrontal = true, hayTrasera = false))
    }

    @Test
    fun `sin ninguna camara no hay lente`() {
        assertNull(resolverLente(LenteCamara.FRONTAL, hayFrontal = false, hayTrasera = false))
        assertNull(resolverLente(LenteCamara.TRASERA, hayFrontal = false, hayTrasera = false))
    }
}
