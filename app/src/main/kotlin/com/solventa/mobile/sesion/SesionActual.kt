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

    /** Correo con el que se registró o inició sesión el cliente; prellena el inicio de sesión (HU05). */
    @Volatile
    var email: String? = null
        private set

    /**
     * Último correo conocido: sobrevive a [cerrar] para prellenar el inicio de sesión cuando la sesión venció. Solo en
     * memoria; si el proceso muere, el cliente lo escribe de nuevo.
     */
    @Volatile
    var ultimoEmail: String? = null
        private set

    fun iniciar(clienteId: String, accessToken: String, nombre: String? = null, email: String? = null) {
        this.clienteId = clienteId
        this.accessToken = accessToken
        this.nombre = nombre
        this.email = email
        if (email != null) ultimoEmail = email
    }

    fun cerrar() {
        clienteId = null
        accessToken = null
        nombre = null
        email = null
    }

    val activa: Boolean get() = clienteId != null && accessToken != null

    fun credenciales(): Credenciales? {
        val id = clienteId ?: return null
        val token = accessToken ?: return null
        return Credenciales(id, token, nombre, email)
    }
}

/** «Ana María» → «Ana». `null` si no hay nombre que mostrar. */
fun primerNombre(nombre: String?): String? =
    nombre?.trim()?.substringBefore(' ')?.takeIf { it.isNotEmpty() }
