package com.solventa.mobile.polizas

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** El código QR de la credencial (HU35): el contenido es el enlace del certificado, que se puede abrir sin sesión. */
object CodigoQr {
    /** El enlace absoluto: la base del borde sin barra final más la ruta que entrega el servidor. */
    fun urlAbsoluta(base: String, ruta: String): String = base.trimEnd('/') + "/" + ruta.trimStart('/')

    /**
     * La matriz del QR, fila por fila (`true` = módulo oscuro), sin el margen: la pantalla dibuja su propio borde blanco.
     * Corrección de errores media: deja el código pequeño y aun así legible en una pantalla con reflejos.
     */
    fun matriz(contenido: String): List<List<Boolean>> {
        val pistas = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0)
        val bits = QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, 0, 0, pistas)
        return List(bits.height) { y -> List(bits.width) { x -> bits[x, y] } }
    }
}
