package com.solventa.mobile.registro

object Validadores {
    private val EMAIL_RE = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    private val CELULAR_RE = Regex("^3\\d{9}$")
    private val DOCUMENTO_RE = Regex("^[A-Za-z0-9]{5,15}$")

    fun errorNombre(valor: String): String? =
        if (valor.isBlank()) "Este campo no puede estar vacío" else null

    fun errorEmail(valor: String): String? =
        if (!EMAIL_RE.matches(valor)) "El correo electrónico no tiene un formato válido" else null

    fun errorCelular(valor: String): String? =
        if (!CELULAR_RE.matches(valor)) "El celular debe tener 10 dígitos y empezar por 3" else null

    fun errorDocumento(valor: String): String? =
        if (!DOCUMENTO_RE.matches(valor)) {
            "El número de documento debe tener entre 5 y 15 caracteres alfanuméricos"
        } else {
            null
        }

    fun errorPassword(valor: String): String? {
        if (valor.length < 8) return "La contraseña debe tener al menos 8 caracteres"
        if (!valor.any { it.isLetter() }) return "La contraseña debe incluir al menos una letra"
        if (!valor.any { it.isDigit() }) return "La contraseña debe incluir al menos un número"
        return null
    }
}
