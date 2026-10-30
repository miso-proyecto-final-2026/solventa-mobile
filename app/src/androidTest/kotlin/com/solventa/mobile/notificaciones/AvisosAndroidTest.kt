package com.solventa.mobile.notificaciones

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.solventa.mobile.red.NotificacionResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La notificación del sistema de HU22: canal de importancia alta (criterio 2) y, con la pantalla de bloqueo visible,
 * solo la versión pública, sin datos de la póliza (criterio 8).
 */
@RunWith(AndroidJUnit4::class)
class AvisosAndroidTest {

    private val contexto: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = contexto.getSystemService(NotificationManager::class.java)
    private val notificacion = NotificacionResponse(
        id = "prueba-hu22", tipo = "POLIZA_EMITIDA", titulo = "Suscripción completada", texto = "Tu póliza ya está activa.",
        tituloPublico = "Solventa", textoPublico = "Tienes una notificación nueva.", prioridad = "ALTA", idioma = "es-CO",
        suscripcionId = "s1", creadaEn = "2026-10-30T20:43:22Z",
    )

    @Before
    fun darPermiso() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(contexto.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        manager.cancelAll()
    }

    @Test
    fun elCanalEsDeImportanciaAltaYPrivadoEnLaPantallaDeBloqueo() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        AvisosAndroid(contexto)

        val canal = manager.getNotificationChannel(AvisosAndroid.CANAL_POLIZAS)

        assertNotNull(canal)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, canal.importance)
        assertEquals(Notification.VISIBILITY_PRIVATE, canal.lockscreenVisibility)
    }

    @Test
    fun conPermisoLaNotificacionSeMuestraConSuVersionPublicaSinDatosDeLaPoliza() {
        val avisos = AvisosAndroid(contexto)
        assumeTrue("las notificaciones están desactivadas en este dispositivo", avisos.puedeMostrar())

        avisos.mostrar(notificacion)

        val publicada = manager.activeNotifications.single { it.id == notificacion.id.hashCode() }.notification
        assertEquals("Suscripción completada", publicada.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(Notification.VISIBILITY_PRIVATE, publicada.visibility)
        val publica = checkNotNull(publicada.publicVersion)
        assertEquals("Solventa", publica.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Tienes una notificación nueva.", publica.extras.getString(Notification.EXTRA_TEXT))
        assertTrue(publica.extras.getString(Notification.EXTRA_TEXT)!!.none(Char::isDigit))
        assertNotNull(publicada.contentIntent) // al tocarla abre el certificado
    }

    @Test
    fun mostrarDosVecesLaMismaNotificacionLaReemplazaEnVezDeDuplicarla() {
        val avisos = AvisosAndroid(contexto)
        assumeTrue(avisos.puedeMostrar())

        avisos.mostrar(notificacion)
        avisos.mostrar(notificacion)

        assertEquals(1, manager.activeNotifications.count { it.id == notificacion.id.hashCode() })
    }
}
