package com.solventa.mobile.navegacion

/**
 * Registro (HU01) → consentimiento (HU06) → verificación (HU02) → resultado (HU03) → secciones principales:
 * seguros (cotización, HU37, y compra de la oferta, HU17) y perfil, que muestra los consentimientos (HU06, HU07).
 */
enum class Paso { ARRANQUE, REGISTRO, CONSENTIMIENTO, VERIFICACION, RESULTADO, INICIO, COMPRA, PERFIL }

/** Pestañas de la barra inferior. */
enum class Seccion { SEGUROS, PERFIL }

val Paso.seccion: Seccion?
    get() = when (this) {
        Paso.INICIO, Paso.COMPRA -> Seccion.SEGUROS
        Paso.PERFIL -> Seccion.PERFIL
        else -> null
    }

/** La barra solo aparece cuando el cliente ya está dentro de la app, no durante el alta ni la verificación. */
val Paso.conBarra: Boolean get() = seccion != null

/** Destino del botón atrás del sistema dentro de las secciones; `null` deja el comportamiento por defecto. */
val Paso.atras: Paso?
    get() = when (this) {
        Paso.COMPRA -> Paso.INICIO
        Paso.PERFIL -> Paso.INICIO
        else -> null
    }

/**
 * Cambiar de pestaña no debe perder lo que el cliente escribió en el formulario de cotización: solo se empieza una
 * cotización nueva al llegar a seguros desde fuera de las secciones (por ejemplo, tras la verificación o tras
 * compartir los datos).
 */
fun reiniciaSeguros(desde: Paso, hacia: Paso): Boolean = hacia == Paso.INICIO && desde.seccion == null
