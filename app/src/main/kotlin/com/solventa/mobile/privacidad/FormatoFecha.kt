package com.solventa.mobile.privacidad

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Fechas del consentimiento según la localización del dispositivo (HU07, criterio 9). */
object FormatoFecha {

    fun fechaHora(iso: String, locale: Locale, zona: ZoneId = ZoneId.systemDefault()): String =
        runCatching {
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(locale)
                .withZone(zona)
                .format(Instant.parse(iso))
        }.getOrDefault(iso)
}
