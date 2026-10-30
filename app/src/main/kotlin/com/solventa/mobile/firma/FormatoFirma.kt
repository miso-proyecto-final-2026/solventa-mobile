package com.solventa.mobile.firma

/** Textos numéricos de la firma, separados de la pantalla para probarlos sin Compose. */
object FormatoFirma {
    /** `0:42`: lo que falta para poder pedir otro código. */
    fun cuentaAtras(segundos: Int): String {
        val total = segundos.coerceAtLeast(0)
        return "%d:%02d".format(total / 60, total % 60)
    }

    /** Las seis casillas del código: los dígitos escritos y huecos hasta completar [largo]. */
    fun casillas(codigo: String, largo: Int = LARGO_CODIGO): List<Char?> = List(largo) { codigo.getOrNull(it) }
}
