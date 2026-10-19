package com.solventa.mobile.cotizacion

import com.solventa.mobile.R
import kotlinx.serialization.json.JsonPrimitive

/** Por qué un campo del formulario no es válido; la pantalla lo traduce a un texto del idioma del usuario. */
enum class ErrorCampo { REQUERIDO, NO_ENTERO, MENOR_AL_MINIMO, MAYOR_AL_MAXIMO, OPCION_INVALIDA }

data class Opcion(val valor: String, val etiqueta: Int)

sealed interface Campo {
    val clave: String
    val etiqueta: Int

    /** Devuelve null si el texto es válido. */
    fun validar(texto: String): ErrorCampo?

    data class Entero(
        override val clave: String,
        override val etiqueta: Int,
        val min: Long,
        val max: Long,
    ) : Campo {
        override fun validar(texto: String): ErrorCampo? {
            val limpio = texto.trim()
            if (limpio.isEmpty()) return ErrorCampo.REQUERIDO
            val valor = limpio.toLongOrNull() ?: return ErrorCampo.NO_ENTERO
            return when {
                valor < min -> ErrorCampo.MENOR_AL_MINIMO
                valor > max -> ErrorCampo.MAYOR_AL_MAXIMO
                else -> null
            }
        }
    }

    data class Seleccion(
        override val clave: String,
        override val etiqueta: Int,
        val opciones: List<Opcion>,
    ) : Campo {
        override fun validar(texto: String): ErrorCampo? {
            val limpio = texto.trim()
            return when {
                limpio.isEmpty() -> ErrorCampo.REQUERIDO
                opciones.none { it.valor == limpio } -> ErrorCampo.OPCION_INVALIDA
                else -> null
            }
        }
    }
}

data class Producto(val id: String, val nombre: Int, val campos: List<Campo>) {

    /** Un error por cada campo inválido; vacío si el formulario se puede enviar. */
    fun validar(valores: Map<String, String>): Map<String, ErrorCampo> =
        campos.mapNotNull { campo ->
            campo.validar(valores[campo.clave].orEmpty())?.let { campo.clave to it }
        }.toMap()

    /** Los datos con el tipo que espera el servidor: enteros como número y selecciones como texto. */
    fun datos(valores: Map<String, String>): Map<String, JsonPrimitive> =
        campos.associate { campo ->
            val texto = valores[campo.clave].orEmpty().trim()
            campo.clave to when (campo) {
                is Campo.Entero -> JsonPrimitive(texto.toLong())
                is Campo.Seleccion -> JsonPrimitive(texto)
            }
        }
}

/**
 * Productos que cotiza el motor, con los datos y rangos de `reglas/v1.yaml` de MS Cotización. El servidor no
 * expone un catálogo: si las reglas cambian allí, hay que cambiarlas aquí (y el 422 del servidor lo delata).
 */
object Catalogo {
    val productos: List<Producto> = listOf(
        Producto(
            id = "microseguro_vida",
            nombre = R.string.cotizacion_producto_microseguro_vida,
            campos = listOf(
                Campo.Entero("edad", R.string.cotizacion_campo_edad, min = 18, max = 65),
                Campo.Entero("suma_asegurada", R.string.cotizacion_campo_suma_asegurada, min = 5_000_000, max = 100_000_000),
            ),
        ),
        Producto(
            id = "proteccion_dispositivos",
            nombre = R.string.cotizacion_producto_proteccion_dispositivos,
            campos = listOf(
                Campo.Seleccion(
                    "tipo_dispositivo",
                    R.string.cotizacion_campo_tipo_dispositivo,
                    listOf(
                        Opcion("celular", R.string.cotizacion_dispositivo_celular),
                        Opcion("portatil", R.string.cotizacion_dispositivo_portatil),
                        Opcion("tableta", R.string.cotizacion_dispositivo_tableta),
                    ),
                ),
                Campo.Entero("valor_dispositivo", R.string.cotizacion_campo_valor_dispositivo, min = 300_000, max = 15_000_000),
                Campo.Entero("antiguedad_meses", R.string.cotizacion_campo_antiguedad_meses, min = 0, max = 36),
            ),
        ),
    )

    fun porId(id: String): Producto? = productos.firstOrNull { it.id == id }
}
