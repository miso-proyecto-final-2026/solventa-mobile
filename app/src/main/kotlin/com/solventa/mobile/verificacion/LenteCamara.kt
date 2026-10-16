package com.solventa.mobile.verificacion

enum class LenteCamara { FRONTAL, TRASERA }

/**
 * Elige la cámara a usar. Si falta la preferida se usa la otra, para no dejar bloqueado un
 * dispositivo sin cámara frontal (tabletas, emuladores sin webcam). `null` si no hay ninguna.
 */
fun resolverLente(preferida: LenteCamara, hayFrontal: Boolean, hayTrasera: Boolean): LenteCamara? =
    when (preferida) {
        LenteCamara.FRONTAL -> if (hayFrontal) LenteCamara.FRONTAL else if (hayTrasera) LenteCamara.TRASERA else null
        LenteCamara.TRASERA -> if (hayTrasera) LenteCamara.TRASERA else if (hayFrontal) LenteCamara.FRONTAL else null
    }
