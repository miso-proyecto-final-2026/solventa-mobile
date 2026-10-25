package com.solventa.mobile.sesion

/**
 * Sesión del cliente en memoria, creada al registrarse (HU01). Al reabrir la app se restaura desde
 * [AlmacenSesion] (HU03); el inicio de sesión biométrico llega con HU04/HU05.
 */
object SesionActual {
    @Volatile
    var clienteId: String? = null
        private set

    @Volatile
    var accessToken: String? = null
        private set

    /** Nombre con el que se registró el cliente; solo para saludarlo. Puede faltar en sesiones guardadas antes. */
    @Volatile
    var nombre: String? = null
        private set

    fun iniciar(clienteId: String, accessToken: String, nombre: String? = null) {
        this.clienteId = clienteId
        this.accessToken = accessToken
        this.nombre = nombre
    }

    fun cerrar() {
        clienteId = null
        accessToken = null
        nombre = null
    }

    val activa: Boolean get() = clienteId != null && accessToken != null

    fun credenciales(): Credenciales? {
        val id = clienteId ?: return null
        val token = accessToken ?: return null
        return Credenciales(id, token, nombre)
    }
}

/** «Ana María» → «Ana». `null` si no hay nombre que mostrar. */
fun primerNombre(nombre: String?): String? =
    nombre?.trim()?.substringBefore(' ')?.takeIf { it.isNotEmpty() }
