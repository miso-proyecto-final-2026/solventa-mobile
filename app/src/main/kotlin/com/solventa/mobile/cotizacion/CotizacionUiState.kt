package com.solventa.mobile.cotizacion

import com.solventa.mobile.red.CotizacionResponse

enum class FaseCotizacion { ELEGIR_PRODUCTO, FORMULARIO, CARGANDO, RESULTADO }

/** Cada falla tiene su mensaje y su acción (HU37, criterio 8). */
enum class ErrorCotizacion { TIEMPO_AGOTADO, SIN_CONEXION, SERVICIO, SESION_VENCIDA }

data class CotizacionUiState(
    val fase: FaseCotizacion = FaseCotizacion.ELEGIR_PRODUCTO,
    val producto: Producto? = null,
    val valores: Map<String, String> = emptyMap(),
    /** Validación local, en línea. */
    val errores: Map<String, ErrorCampo> = emptyMap(),
    /** Motivo que devolvió el servidor (422) por campo. */
    val erroresServidor: Map<String, String> = emptyMap(),
    val error: ErrorCotizacion? = null,
    val cotizacion: CotizacionResponse? = null,
) {
    /** Sin consentimiento o con proveedores caídos el precio es una estimación. */
    val esEstimacion: Boolean get() = cotizacion?.let { it.aproximada || it.degradado } == true

    /**
     * Compartir los datos solo mejora el precio cuando la estimación viene de no tener consentimiento:
     * si fue una caída de proveedores (`degradado`), compartirlos no lo arregla.
     */
    val ofreceCompartirDatos: Boolean get() = cotizacion?.let { it.aproximada && !it.degradado } == true
}
