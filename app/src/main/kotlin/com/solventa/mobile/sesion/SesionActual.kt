package com.solventa.mobile.sesion

/**
 * Sesión del cliente en memoria, creada al registrarse (HU01). Solo vive mientras el proceso;
 * el almacenamiento seguro y el inicio de sesión biométrico llegan con HU04/HU05.
 */
object SesionActual {
    @Volatile
    var clienteId: String? = null
        private set

    @Volatile
    var accessToken: String? = null
        private set

    fun iniciar(clienteId: String, accessToken: String) {
        this.clienteId = clienteId
        this.accessToken = accessToken
    }

    fun cerrar() {
        clienteId = null
        accessToken = null
    }

    val activa: Boolean get() = clienteId != null && accessToken != null
}
