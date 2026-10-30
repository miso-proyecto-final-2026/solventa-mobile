package com.solventa.mobile.notificaciones

import android.content.Context
import java.util.UUID

/**
 * Identificador del dispositivo para el servicio de Notificaciones. Lo genera la app y se conserva entre aperturas. En un
 * push real con Firebase sería el token de FCM; hoy el push es simulado (ADR-0020) y basta con un UUID propio.
 */
interface IdentificadorDispositivo {
    fun obtener(): String
}

class IdentificadorDispositivoPreferencias(contexto: Context) : IdentificadorDispositivo {
    private val preferencias = contexto.applicationContext.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

    @Synchronized
    override fun obtener(): String =
        preferencias.getString(CLAVE, null) ?: "app-${UUID.randomUUID()}".also {
            preferencias.edit().putString(CLAVE, it).apply()
        }

    private companion object {
        const val ARCHIVO = "dispositivo"
        const val CLAVE = "id"
    }
}
