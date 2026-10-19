package com.solventa.mobile.cotizacion

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

/** Importes y fechas según la localización del dispositivo (HU37, criterio 9). */
object FormatoCotizacion {

    /** Los montos del motor son pesos enteros: sin decimales aunque la moneda los defina. */
    fun moneda(monto: Long, codigoMoneda: String, locale: Locale): String {
        val formato = NumberFormat.getCurrencyInstance(locale)
        runCatching { Currency.getInstance(codigoMoneda) }.getOrNull()?.let { formato.currency = it }
        formato.minimumFractionDigits = 0
        formato.maximumFractionDigits = 0
        return formato.format(monto)
    }

    fun vigencia(iso: String, locale: Locale, zona: ZoneId = ZoneId.systemDefault()): String =
        runCatching {
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(locale)
                .withZone(zona)
                .format(Instant.parse(iso))
        }.getOrDefault(iso)
}
