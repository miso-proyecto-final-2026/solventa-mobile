package com.solventa.mobile.polizas

import com.solventa.mobile.red.CoberturaPolizaResponse
import com.solventa.mobile.red.PolizaResponse
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Copia local de «Mis pólizas» para verlas sin conexión (HU36). Guarda solo lo que pide el criterio 1 (número, estado,
 * producto, coberturas y vigencia): sin prima, sin fecha de emisión y sin datos del titular (criterio 7).
 */
@Serializable
data class CopiaPolizas(
    @SerialName("cliente_id") val clienteId: String,
    /** Instante ISO-8601 de la última sincronización con el servidor (criterio 5). */
    @SerialName("sincronizado_en") val sincronizadoEn: String,
    val polizas: List<PolizaGuardada>,
    /**
     * Documentos que el cliente descargó, por [claveDocumento]: el URI del archivo que eligió con el selector del sistema
     * (criterio 2). La app no guarda el PDF: lo abre desde donde el cliente lo dejó.
     */
    val documentos: Map<String, String> = emptyMap(),
) {
    fun conDocumento(clave: String, uri: String): CopiaPolizas = copy(documentos = documentos + (clave to uri))
}

@Serializable
data class PolizaGuardada(
    val id: String,
    @SerialName("suscripcion_id") val suscripcionId: String,
    @SerialName("numero_poliza") val numeroPoliza: String,
    val producto: String,
    /** El estado que informó el servidor; vigente o vencida se recalcula con la fecha al mostrarla (criterio 6). */
    val estado: String,
    @SerialName("vigencia_desde") val vigenciaDesde: String,
    @SerialName("fecha_vencimiento") val fechaVencimiento: String,
    val moneda: String = "COP",
    val coberturas: List<CoberturaGuardada> = emptyList(),
)

@Serializable
data class CoberturaGuardada(val tipo: String, val monto: Long, val deducible: Long = 0)

/** Clave de un documento descargado dentro de [CopiaPolizas.documentos]. */
fun claveDocumento(suscripcionId: String, documento: DocumentoPoliza): String = "$suscripcionId/${documento.name}"

object CopiasPolizas {
    /**
     * La copia nueva tras una sincronización: lo del servidor reemplaza a la copia anterior (criterio 8). De la anterior
     * solo se conservan los documentos descargados de pólizas que siguen existiendo, y solo si es del mismo cliente.
     */
    fun desde(clienteId: String, polizas: List<PolizaResponse>, sincronizadoEn: Instant, anterior: CopiaPolizas?): CopiaPolizas {
        val vigentes = polizas.map { it.suscripcionId }.toSet()
        val documentos = anterior
            ?.takeIf { it.clienteId == clienteId }
            ?.documentos
            ?.filterKeys { it.substringBefore('/') in vigentes }
            .orEmpty()
        return CopiaPolizas(clienteId, sincronizadoEn.toString(), polizas.map(PolizaResponse::aGuardada), documentos)
    }
}

fun PolizaResponse.aGuardada(): PolizaGuardada = PolizaGuardada(
    id = id,
    suscripcionId = suscripcionId,
    numeroPoliza = numeroPoliza,
    producto = producto,
    estado = estado,
    vigenciaDesde = vigenciaDesde,
    fechaVencimiento = fechaVencimiento,
    moneda = moneda,
    coberturas = coberturas.map { CoberturaGuardada(it.tipo, it.monto, it.deducible) },
)

/** La póliza guardada vuelve al modelo que usan las pantallas; la prima no se guardó y queda en cero (no se muestra). */
fun PolizaGuardada.aRespuesta(): PolizaResponse = PolizaResponse(
    id = id,
    suscripcionId = suscripcionId,
    numeroPoliza = numeroPoliza,
    producto = producto,
    estado = estado,
    vigenciaDesde = vigenciaDesde,
    fechaVencimiento = fechaVencimiento,
    moneda = moneda,
    coberturas = coberturas.map { CoberturaPolizaResponse(it.tipo, it.monto, it.deducible) },
)
