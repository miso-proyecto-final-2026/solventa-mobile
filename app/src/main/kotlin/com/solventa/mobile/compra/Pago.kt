package com.solventa.mobile.compra

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.time.YearMonth
import java.util.UUID

/**
 * Lo que el cliente teclea. Vive solo en memoria y se descarta en cuanto se obtiene el token (HU17, criterio 2).
 * `toString` no revela nada para que ni un log ni un mensaje de error lo filtren.
 */
class DatosTarjeta(val numero: String = "", val vencimiento: String = "", val cvv: String = "") {
    override fun toString() = "DatosTarjeta(***)"
}

enum class ErrorTarjeta { NUMERO, VENCIMIENTO, CVV }

/**
 * Qué se puede teclear en cada campo de la tarjeta: solo dígitos y con tope de largo. La validación completa (dígito de
 * control, vencimiento no pasado) sigue siendo de [ValidadorTarjeta] al pagar.
 */
object EntradaTarjeta {
    /** Las tarjetas van de 13 a 19 dígitos. */
    const val MAXIMO_NUMERO = 19

    /** 3 dígitos, o 4 en algunas marcas. */
    const val MAXIMO_CVV = 4

    fun numero(texto: String): String = texto.filter(Char::isDigit).take(MAXIMO_NUMERO)

    fun cvv(texto: String): String = texto.filter(Char::isDigit).take(MAXIMO_CVV)

    /**
     * `MM/AA` con un mes válido: un primer dígito mayor que 1 completa el mes con cero («5» → «05») y un mes imposible
     * («13», «00») no se acepta. La barra se agrega sola al pasar del mes.
     */
    fun vencimiento(texto: String): String {
        var digitos = texto.filter(Char::isDigit).take(6)
        if (digitos.isNotEmpty() && digitos[0] > '1') digitos = "0$digitos"
        if (digitos.length >= 2) {
            val mes = digitos.take(2).toInt()
            if (mes !in 1..12) digitos = digitos.take(1)
        }
        digitos = digitos.take(4)
        return if (digitos.length <= 2) digitos else digitos.substring(0, 2) + "/" + digitos.substring(2)
    }
}

/** Muestra el número en grupos de 4 («4111 1111 1111 1111») sin cambiar lo que se guarda. */
object AgrupadorDeTarjeta : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        val agrupado = original.chunked(4).joinToString(" ")
        val mapa = object : OffsetMapping {
            // Antes de cada dígito (salvo el primero de un grupo de 4) hay un espacio más.
            override fun originalToTransformed(offset: Int): Int =
                if (offset <= 0) 0 else (offset + (offset - 1) / 4).coerceAtMost(agrupado.length)

            override fun transformedToOriginal(offset: Int): Int =
                (offset - offset / 5).coerceIn(0, original.length)
        }
        return TransformedText(AnnotatedString(agrupado), mapa)
    }
}

object ValidadorTarjeta {
    /** Validación local y barata para no gastar un intento de cobro: el servidor y la pasarela deciden de verdad. */
    fun validar(tarjeta: DatosTarjeta, hoy: YearMonth = YearMonth.now()): Set<ErrorTarjeta> = buildSet {
        if (!numeroValido(tarjeta.numero)) add(ErrorTarjeta.NUMERO)
        if (!vencimientoValido(tarjeta.vencimiento, hoy)) add(ErrorTarjeta.VENCIMIENTO)
        if (tarjeta.cvv.length !in 3..4 || !tarjeta.cvv.all(Char::isDigit)) add(ErrorTarjeta.CVV)
    }

    private fun numeroValido(numero: String): Boolean {
        if (numero.length !in 13..19 || !numero.all(Char::isDigit)) return false
        val suma = numero.reversed().mapIndexed { i, c ->
            val d = c - '0'
            if (i % 2 == 1) (d * 2).let { if (it > 9) it - 9 else it } else d
        }.sum()
        return suma % 10 == 0
    }

    /** Formato `MM/AA`. */
    private fun vencimientoValido(texto: String, hoy: YearMonth): Boolean {
        val partes = texto.split("/")
        if (partes.size != 2 || partes[0].length != 2 || partes[1].length != 2) return false
        val mes = partes[0].toIntOrNull() ?: return false
        val anio = partes[1].toIntOrNull() ?: return false
        if (mes !in 1..12) return false
        return !YearMonth.of(2000 + anio, mes).isBefore(hoy)
    }
}

/**
 * Componente que convierte la tarjeta en un token de un solo uso. En producción lo hace el componente seguro de
 * la pasarela (SAQ-A) y la app nunca ve el número; mientras MS Pagos (HU18) no exista se usa [TokenizadorSimulado].
 * Cambiar de proveedor es cambiar esta implementación.
 */
interface TokenizadorPago {
    suspend fun tokenizar(tarjeta: DatosTarjeta): String
}

/**
 * Pasarela simulada (T4): el token no contiene ni deriva nada de la tarjeta, salvo el modo de la pasarela simulada de
 * MS Pagos, que se elige por el prefijo (`tok_sim_<modo>_…`). Tres tarjetas de prueba, como en un sandbox real, activan
 * esos modos para probar en staging los casos de rechazo, respuesta perdida y demora; cualquier otra aprueba.
 */
class TokenizadorSimulado(private val generar: () -> String = { UUID.randomUUID().toString() }) : TokenizadorPago {
    override suspend fun tokenizar(tarjeta: DatosTarjeta): String {
        val modo = MODOS_DE_PRUEBA[tarjeta.numero]
        return if (modo == null) "tok_sim_" + generar() else "tok_sim_${modo}_" + generar()
    }

    private companion object {
        val MODOS_DE_PRUEBA = mapOf(
            "4000000000000002" to "rechazar",
            "4000000000000119" to "perder",
            "4000000000000259" to "tardar",
        )
    }
}
