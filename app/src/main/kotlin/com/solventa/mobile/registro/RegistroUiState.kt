package com.solventa.mobile.registro

import com.solventa.mobile.red.RegistroRequest

data class RegistroUiState(
    val nombre: String = "",
    val apellido: String = "",
    val tipoDocumento: String = "CC",
    val numeroDocumento: String = "",
    val email: String = "",
    val celular: String = "",
    val password: String = "",
    val aceptoTerminos: Boolean = false,
    val errores: Map<String, String> = emptyMap(),
    val cargando: Boolean = false,
    val registroExitoso: Boolean = false,
    val mensajeError: String? = null,
) {
    fun aSolicitud() = RegistroRequest(
        nombre = nombre.trim(),
        apellido = apellido.trim(),
        tipoDocumento = tipoDocumento,
        numeroDocumento = numeroDocumento.trim(),
        email = email.trim(),
        celular = celular.trim(),
        password = password,
        aceptoTerminos = aceptoTerminos,
    )
}
