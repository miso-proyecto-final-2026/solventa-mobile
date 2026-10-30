package com.solventa.mobile.notificaciones

import com.solventa.mobile.red.NotificacionResponse

/**
 * Hace de «push» lo que el servidor deja en la bandeja (HU22, ADR-0020): registra el dispositivo con el idioma del teléfono
 * y, en cada sondeo, muestra como notificación del sistema lo que haya de nuevo.
 *
 * Una notificación solo se confirma al servidor después de mostrarse: si no hay permiso o falla el aviso, sigue como nueva
 * y se muestra más tarde, y confirmarla dos veces es inofensivo, así que un corte entre mostrar y confirmar no la duplica
 * en la bandeja del servidor (sí podría mostrarse una segunda vez en el teléfono, con el mismo identificador: reemplaza a la
 * anterior).
 */
class SondeadorNotificaciones(
    private val repository: NotificacionesRepository,
    private val avisos: AvisosSistema,
    private val dispositivo: IdentificadorDispositivo,
) {
    private var registrado: String? = null

    /**
     * Registra el dispositivo si aún no se hizo para ese cliente y ese idioma (otro cliente en el mismo teléfono se registra
     * de nuevo). Un fallo se reintenta en la siguiente llamada.
     */
    suspend fun registrar(clienteId: String, idioma: String) {
        val clave = "$clienteId|$idioma"
        if (registrado == clave) return
        if (repository.registrarDispositivo(dispositivo.obtener(), idioma) is NotificacionesRepository.Resultado.Exito) {
            registrado = clave
        }
    }

    /** Una vuelta del sondeo. Devuelve cuántas notificaciones mostró. */
    suspend fun sondear(): Int {
        if (!avisos.puedeMostrar()) return 0
        val nuevas = (repository.nuevas() as? NotificacionesRepository.Resultado.Exito)?.valor ?: return 0
        var mostradas = 0
        for (n in nuevas.sortedBy(NotificacionResponse::creadaEn)) {
            val exito = runCatching { avisos.mostrar(n) }.isSuccess
            if (!exito) continue
            mostradas++
            repository.confirmarRecibida(n.id)
        }
        return mostradas
    }
}
