package com.solventa.mobile.notificaciones

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.solventa.mobile.MainActivity
import com.solventa.mobile.R
import com.solventa.mobile.red.NotificacionResponse

/** Lo que el sondeo necesita del sistema de notificaciones; separado para probar el sondeo sin Android. */
interface AvisosSistema {
    /** `false` si el cliente no dio permiso o desactivó las notificaciones: no se muestra ni se confirma nada. */
    fun puedeMostrar(): Boolean

    fun mostrar(notificacion: NotificacionResponse)
}

/**
 * Notificación del sistema de prioridad alta (HU22, criterio 2). Con la pantalla de bloqueo visible solo se ve la versión
 * pública, sin datos de la póliza (criterio 8); el detalle aparece al desbloquear.
 */
class AvisosAndroid(private val contexto: Context) : AvisosSistema {

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CANAL_POLIZAS,
                contexto.getString(R.string.notificaciones_canal_nombre),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = contexto.getString(R.string.notificaciones_canal_descripcion)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
            contexto.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
        }
    }

    override fun puedeMostrar(): Boolean {
        val permitido = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permitido && NotificationManagerCompat.from(contexto).areNotificationsEnabled()
    }

    @android.annotation.SuppressLint("MissingPermission") // lo comprueba `puedeMostrar()`, que el sondeo llama antes
    override fun mostrar(notificacion: NotificacionResponse) {
        val publica = NotificationCompat.Builder(contexto, CANAL_POLIZAS)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(notificacion.tituloPublico)
            .setContentText(notificacion.textoPublico)
            .build()
        val aviso = NotificationCompat.Builder(contexto, CANAL_POLIZAS)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(notificacion.titulo)
            .setContentText(notificacion.texto)
            .setPriority(if (notificacion.prioridad == "ALTA") NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publica)
            .setAutoCancel(true)
            .setContentIntent(abrirCertificado(notificacion))
            .build()
        NotificationManagerCompat.from(contexto).notify(notificacion.id.hashCode(), aviso)
    }

    private fun abrirCertificado(notificacion: NotificacionResponse): PendingIntent {
        val intent = Intent(contexto, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_SUSCRIPCION, notificacion.suscripcionId)
        return PendingIntent.getActivity(
            contexto, notificacion.id.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val CANAL_POLIZAS = "polizas"
    }
}
