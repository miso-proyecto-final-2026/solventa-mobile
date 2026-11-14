package com.solventa.mobile.biometria

enum class FaseBiometria { CARGANDO, ESTADO, FORMULARIO, PROCESANDO, CONFIRMANDO_DESACTIVAR }

/** Cada falla tiene su mensaje y, si aplica, su acción (HU04, criterio 9). */
enum class ErrorAcceso {
    TIEMPO_AGOTADO,
    SIN_CONEXION,
    SERVICIO,
    SESION_VENCIDA,
    CONTRASENA_VACIA,
    CONTRASENA_INCORRECTA,
    BLOQUEADO,
    KYC_NO_APROBADO,
    CANCELADO,
    AUTENTICACION,
}

/** Lo que `reintentar()` repite. */
enum class AccionAcceso { CONSULTAR, DESACTIVAR }

/** Resultado de la última operación que el cliente debe notar. */
enum class AvisoAcceso { ACTIVADO, DESACTIVADO, CLAVE_INVALIDADA }

/** Por qué la opción no se puede activar (criterios 1 y 2). */
enum class MotivoBloqueo { KYC_NO_APROBADO, SIN_SENSOR, SIN_REGISTRO, NO_DISPONIBLE }

data class BiometriaUiState(
    val fase: FaseBiometria = FaseBiometria.CARGANDO,
    val capacidad: Capacidad = Capacidad(Disponibilidad.NO_DISPONIBLE, emptyList()),
    /** Estado confirmado por el servidor y por la clave local; nunca se adelanta a la respuesta. */
    val activo: Boolean = false,
    val metodoActivo: MetodoBiometrico? = null,
    /** `null` si no se pudo consultar: el servidor lo vuelve a validar al activar. */
    val kycAprobado: Boolean? = null,
    val metodoElegido: MetodoBiometrico? = null,
    val error: ErrorAcceso? = null,
    val pendiente: AccionAcceso? = null,
    val aviso: AvisoAcceso? = null,
) {
    val bloqueo: MotivoBloqueo?
        get() = when {
            activo -> null
            kycAprobado == false -> MotivoBloqueo.KYC_NO_APROBADO
            capacidad.disponibilidad == Disponibilidad.SIN_SENSOR -> MotivoBloqueo.SIN_SENSOR
            capacidad.disponibilidad == Disponibilidad.SIN_REGISTRO -> MotivoBloqueo.SIN_REGISTRO
            !capacidad.puedeActivar -> MotivoBloqueo.NO_DISPONIBLE
            else -> null
        }

    val puedeActivar: Boolean get() = !activo && bloqueo == null
}
