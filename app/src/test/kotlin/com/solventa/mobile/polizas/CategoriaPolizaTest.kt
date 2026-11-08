package com.solventa.mobile.polizas

import com.solventa.mobile.R
import com.solventa.mobile.ui.iconos.Iconos
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoriaPolizaTest {

    @Test
    fun `cada producto del catalogo tiene su categoria`() {
        assertEquals(R.string.polizas_categoria_vida, categoriaDe("microseguro_vida").etiqueta)
        assertEquals(Iconos.Casa, categoriaDe("microseguro_vida").icono)
        assertEquals(R.string.polizas_categoria_dispositivos, categoriaDe("proteccion_dispositivos").etiqueta)
    }

    @Test
    fun `un producto desconocido no rompe la tarjeta`() {
        assertEquals(R.string.polizas_categoria_otro, categoriaDe("seguro_nuevo").etiqueta)
    }
}
