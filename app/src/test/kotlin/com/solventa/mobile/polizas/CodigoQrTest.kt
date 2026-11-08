package com.solventa.mobile.polizas

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodigoQrTest {

    @Test
    fun `la url absoluta une la base y la ruta con una sola barra`() {
        val esperada = "https://d1.cloudfront.net/v1/certificados/abc.def"

        assertEquals(esperada, CodigoQr.urlAbsoluta("https://d1.cloudfront.net", "/v1/certificados/abc.def"))
        assertEquals(esperada, CodigoQr.urlAbsoluta("https://d1.cloudfront.net/", "/v1/certificados/abc.def"))
        assertEquals(esperada, CodigoQr.urlAbsoluta("https://d1.cloudfront.net/", "v1/certificados/abc.def"))
    }

    @Test
    fun `la matriz es cuadrada y tiene modulos oscuros y claros`() {
        val matriz = CodigoQr.matriz("https://d1.cloudfront.net/v1/certificados/abc.def")

        assertTrue(matriz.size >= 21) // la versión 1 de QR ya mide 21 × 21
        assertTrue(matriz.all { it.size == matriz.size })
        assertTrue(matriz.flatten().any { it } && matriz.flatten().any { !it })
    }

    @Test
    fun `un lector decodifica la matriz y obtiene el mismo enlace`() {
        val enlace = "https://d1.cloudfront.net/v1/certificados/eyJwIjoiYWJjIn0.c2lnbmF0dXJh"
        val matriz = CodigoQr.matriz(enlace)
        // Se repinta con la zona de silencio de 4 módulos que exige el estándar y 4 píxeles por módulo.
        val escala = 4
        val margen = 4
        val lado = (matriz.size + 2 * margen) * escala
        val pixeles = IntArray(lado * lado) { 0xFFFFFFFF.toInt() }
        matriz.forEachIndexed { y, fila ->
            fila.forEachIndexed { x, oscuro ->
                if (oscuro) {
                    for (dy in 0 until escala) for (dx in 0 until escala) {
                        pixeles[((y + margen) * escala + dy) * lado + (x + margen) * escala + dx] = 0xFF000000.toInt()
                    }
                }
            }
        }

        val leido = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(lado, lado, pixeles))))

        assertEquals(enlace, leido.text)
    }
}
