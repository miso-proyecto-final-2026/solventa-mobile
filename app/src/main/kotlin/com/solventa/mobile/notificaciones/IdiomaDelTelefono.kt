package com.solventa.mobile.notificaciones

import java.util.Locale

/** El idioma que el servidor usa en las notificaciones: inglés si el teléfono está en inglés, es-CO en cualquier otro caso. */
object IdiomaDelTelefono {
    fun de(locale: Locale): String = if (locale.language == "en") "en-US" else "es-CO"

    fun actual(): String = de(Locale.getDefault())
}
