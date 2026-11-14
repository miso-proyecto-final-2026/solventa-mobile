package com.solventa.mobile.bloqueo

/** Cuánto puede estar la app en segundo plano antes de volver a pedir la biometría (HU05, criterio 7). */
const val PLAZO_SEGUNDO_PLANO_MS: Long = 5 * 60 * 1000L

/** Intentos fallidos de biometría tras los que se pide la contraseña (HU05, criterio 3). */
const val MAX_INTENTOS_BIOMETRIA = 3

/**
 * Se vuelve a pedir si la app salió de primer plano hace [plazoMs] o más. `salioEnMs` es `null` si nunca salió
 * (apertura en frío, que siempre se pide por otro camino). Usa un reloj monótono: cambiar la hora del teléfono no lo
 * esquiva.
 */
fun debeBloquearTrasSegundoPlano(salioEnMs: Long?, ahoraMs: Long, plazoMs: Long = PLAZO_SEGUNDO_PLANO_MS): Boolean =
    salioEnMs != null && ahoraMs - salioEnMs >= plazoMs
