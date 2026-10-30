package com.solventa.mobile.firma

import com.solventa.mobile.red.DocumentoFirmaResponse
import com.solventa.mobile.red.PolizaResponse

enum class FaseFirma {
    /** Obteniendo el documento (el PDF se genera aparte, justo después del cobro). */
    CARGANDO,

    /** Revisión del documento, casilla de lectura y código. */
    LISTA,
    FIRMANDO,
    FIRMADA,

    /** Se agotaron los códigos: el cobro se devuelve. */
    FALLIDA,

    /** El plazo para firmar terminó: el cobro se devuelve. */
    VENCIDA,
    ERROR,
}

enum class ErrorFirma { SIN_CONEXION, SERVICIO, SESION_VENCIDA, DOCUMENTO_NO_LISTO, NO_DISPONIBLE, RESULTADO_DESCONOCIDO }

/** Avisos que se muestran sobre el formulario sin sacar al cliente de la pantalla. */
enum class AvisoFirma { CODIGO_INCORRECTO, DOCUMENTO_CAMBIO, SIN_CONEXION, SERVICIO }

/** Descarga del certificado de cobertura (HU22): se baja el PDF y el cliente elige dónde guardarlo. */
enum class EstadoCertificado {
    INACTIVO,

    /** Pidiendo el PDF al servidor. */
    DESCARGANDO,

    /** El PDF está en memoria y falta que el cliente elija dónde guardarlo. */
    LISTO_PARA_GUARDAR,
    GUARDADO,

    /** No se pudo descargar o guardar. */
    ERROR,
}

const val LARGO_CODIGO = 6

/** Bytes de un PDF. Se compara por referencia (un `ByteArray` en un `data class` compararía por referencia sin avisar). */
class ContenidoPdf(val bytes: ByteArray)

data class FirmaUiState(
    val fase: FaseFirma = FaseFirma.CARGANDO,
    val documento: DocumentoFirmaResponse? = null,
    /** El cliente confirma que leyó la póliza (criterio 1). */
    val revisado: Boolean = false,
    /** Se marca al intentar firmar sin confirmar la lectura. */
    val revisionPendiente: Boolean = false,
    val codigo: String = "",
    /** Se marca al intentar firmar con un código incompleto. */
    val codigoIncompleto: Boolean = false,
    val intentosRestantes: Int? = null,
    val aviso: AvisoFirma? = null,
    /** Segundos para poder pedir otro código; 0 = ya se puede. */
    val segundosParaReenviar: Int = 0,
    val codigoReenviado: Boolean = false,
    /** PDF abierto en el lector, o `null` si el lector está cerrado. */
    val pdf: ContenidoPdf? = null,
    val cargandoPdf: Boolean = false,
    val errorPdf: Boolean = false,
    /** La póliza emitida, para el certificado en pantalla. `null` mientras carga o si no se pudo leer. */
    val poliza: PolizaResponse? = null,
    val certificado: EstadoCertificado = EstadoCertificado.INACTIVO,
    /** El PDF del certificado ya descargado, a la espera de que el cliente elija dónde guardarlo. */
    val certificadoPdf: ContenidoPdf? = null,
    val error: ErrorFirma? = null,
)
