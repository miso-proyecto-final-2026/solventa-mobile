package com.solventa.mobile.polizas

import com.solventa.mobile.firma.ContenidoPdf
import com.solventa.mobile.red.PolizaResponse
import java.time.Instant

enum class FasePolizas { CARGANDO, LISTA, ERROR }

enum class ErrorPolizas { SIN_CONEXION, SERVICIO, SESION_VENCIDA }

/** Las dos pestañas de la lista (diseño F1). */
enum class FiltroPolizas { ACTIVAS, VENCIDAS }

/** Los dos documentos del detalle (diseño F2). */
enum class DocumentoPoliza { POLIZA_FIRMADA, CERTIFICADO }

enum class EstadoDescarga {
    INACTIVO,
    DESCARGANDO,

    /** El PDF está en memoria y falta que el cliente elija dónde guardarlo. */
    LISTO_PARA_GUARDAR,
    GUARDADO,
    ERROR,

    /** El servidor dice que ese documento no existe (la póliza aún no lo tiene). */
    NO_DISPONIBLE,
}

/** El enlace del QR de la credencial: `Cargando` mientras se pide, `Listo` con la URL absoluta o `Fallido`. */
sealed interface EnlaceQr {
    data object NoAplica : EnlaceQr
    data object Cargando : EnlaceQr
    data class Listo(val url: String) : EnlaceQr
    data object Fallido : EnlaceQr
}

data class PolizasUiState(
    val fase: FasePolizas = FasePolizas.CARGANDO,
    val polizas: List<PolizaResponse> = emptyList(),
    val filtro: FiltroPolizas = FiltroPolizas.ACTIVAS,
    /** Instante con el que se calcularon los estados: así la lista no cambia de pestaña mientras se mira. */
    val ahora: Instant = Instant.EPOCH,
    /** La póliza cuyo detalle está abierto, o `null` en la lista. */
    val seleccionada: PolizaResponse? = null,
    val qr: EnlaceQr = EnlaceQr.NoAplica,
    val documento: DocumentoPoliza? = null,
    val descarga: EstadoDescarga = EstadoDescarga.INACTIVO,
    /** El PDF ya descargado, a la espera de que el cliente elija dónde guardarlo. */
    val pdf: ContenidoPdf? = null,
    val error: ErrorPolizas? = null,
) {
    fun estadoDe(poliza: PolizaResponse): EstadoPoliza = EstadosPoliza.de(poliza, ahora)

    /** Las pólizas de la pestaña elegida. */
    val visibles: List<PolizaResponse>
        get() = polizas.filter { estadoDe(it).activa == (filtro == FiltroPolizas.ACTIVAS) }

    /** Cuántas hay en cada pestaña, para mostrar el vacío correcto. */
    val hayPolizas: Boolean get() = polizas.isNotEmpty()
}
