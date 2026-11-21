package com.solventa.mobile.red

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Si el teléfono tiene una red con Internet comprobada (HU36): con ella la barra de seguros avisa que cotizar y comprar
 * requieren conexión. En modo avión, o con una red sin salida a Internet, emite `false`.
 */
fun conexionDisponible(contexto: Context): Flow<Boolean> = callbackFlow {
    val conectividad = contexto.getSystemService(ConnectivityManager::class.java)
    fun actual(): Boolean = conectividad.getNetworkCapabilities(conectividad.activeNetwork)?.let {
        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } ?: false

    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            trySend(actual())
        }

        override fun onLost(network: Network) {
            trySend(false)
        }

        override fun onCapabilitiesChanged(network: Network, capacidades: NetworkCapabilities) {
            trySend(
                capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            )
        }
    }
    trySend(actual())
    conectividad.registerDefaultNetworkCallback(callback)
    awaitClose { conectividad.unregisterNetworkCallback(callback) }
}.distinctUntilChanged()
