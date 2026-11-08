package com.solventa.mobile.polizas

import com.solventa.mobile.ui.componentes.TipoEstado
import com.solventa.mobile.ui.iconos.Iconos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** El color del chip de cada estado sigue el patrón de la app: verde, azul, ámbar, rojo y gris. */
class ChipEstadoPolizaTest {

    @Test
    fun `cada estado de la poliza tiene su color`() {
        assertEquals(TipoEstado.ACTIVO, EstadoPoliza.VIGENTE.tipoChip)
        assertEquals(TipoEstado.INFO, EstadoPoliza.PROXIMA_A_VENCER.tipoChip)
        assertEquals(TipoEstado.ADVERTENCIA, EstadoPoliza.PENDIENTE_FIRMA.tipoChip)
        assertEquals(TipoEstado.ERROR, EstadoPoliza.VENCIDA.tipoChip)
        assertEquals(TipoEstado.NEUTRO, EstadoPoliza.ANULADA.tipoChip)
    }

    @Test
    fun `ningun par de estados comparte el color`() {
        val colores = EstadoPoliza.entries.map { it.tipoChip }

        assertEquals(EstadoPoliza.entries.size, colores.toSet().size)
    }

    @Test
    fun `el color no es el unico indicador, cada estado lleva su propio icono`() {
        val iconos = EstadoPoliza.entries.map { it.icono }

        assertEquals(EstadoPoliza.entries.size, iconos.toSet().size)
        assertNotEquals(EstadoPoliza.VENCIDA.icono, EstadoPoliza.PENDIENTE_FIRMA.icono)
        assertEquals(Iconos.Check, EstadoPoliza.VIGENTE.icono)
    }
}
