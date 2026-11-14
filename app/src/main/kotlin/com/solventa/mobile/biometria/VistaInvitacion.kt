package com.solventa.mobile.biometria

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.first

/**
 * Recuerda por cliente que ya se le ofreció activar el acceso biométrico tras aprobar el KYC (HU04, B1 del diseño),
 * para no repetir la invitación cada vez que la app se reabre y pasa por el resultado de la verificación.
 */
interface VistaInvitacion {
    suspend fun vista(clienteId: String): Boolean

    suspend fun marcar(clienteId: String)

    companion object {
        fun crear(contexto: Context): VistaInvitacion = VistaInvitacionDataStore(contexto.applicationContext.datosInvitacion)
    }
}

private val Context.datosInvitacion: DataStore<Preferences> by preferencesDataStore(name = "invitacion_biometrica")

class VistaInvitacionDataStore(private val datos: DataStore<Preferences>) : VistaInvitacion {

    override suspend fun vista(clienteId: String): Boolean =
        try {
            datos.data.first()[clave(clienteId)] == true
        } catch (e: IOException) {
            // Sin poder leer, no se vuelve a molestar: el cliente la encuentra en Perfil → Seguridad.
            true
        }

    override suspend fun marcar(clienteId: String) {
        datos.edit { it[clave(clienteId)] = true }
    }

    private fun clave(clienteId: String) = booleanPreferencesKey("vista_$clienteId")
}

/** La invitación solo se muestra si el cliente aún no la vio y puede activar el acceso (criterios 1 y 2 de HU04). */
fun debeInvitar(estado: BiometriaUiState, yaVista: Boolean): Boolean =
    !yaVista && estado.fase == FaseBiometria.ESTADO && estado.puedeActivar
