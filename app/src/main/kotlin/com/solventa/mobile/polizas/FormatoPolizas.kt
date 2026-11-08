package com.solventa.mobile.polizas

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Textos de la billetera, separados de la pantalla para probarlos sin Compose. */
object FormatoPolizas {
    /** `12 jun 2027` / `Jun 12, 2027` según el idioma; si la fecha no se puede leer, el texto tal cual llegó. */
    fun fecha(iso: String, locale: Locale, zona: ZoneId = ZoneId.systemDefault()): String =
        runCatching {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withZone(zona)
                .format(OffsetDateTime.parse(iso).toInstant())
        }.getOrDefault(iso)

    /** El tipo de cobertura llega como identificador (`dano_accidental`): se muestra con espacios y mayúscula inicial. */
    fun tipoCobertura(tipo: String): String =
        tipo.replace('_', ' ').trim().replaceFirstChar { it.titlecase(Locale.ROOT) }
}
