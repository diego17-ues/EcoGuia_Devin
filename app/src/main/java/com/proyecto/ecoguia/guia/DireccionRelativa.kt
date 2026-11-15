package com.proyecto.ecoguia.guia

/**
 * Respuesta que se envía al ESP32 (una letra por línea) para que el buzzer corrija el rumbo.
 * La app no la muestra como instrucción a la persona.
 */
enum class Indicacion(val letra: Char) {
    IZQUIERDA('L'),
    DERECHA('R'),
    RECTO('F')
}

object DireccionRelativa {

    /**
     * Diferencia firmada entre el rumbo objetivo y el rumbo actual, en (-180, 180].
     * Positiva: el objetivo queda a la derecha. Negativa: a la izquierda.
     */
    fun diferenciaGrados(rumboActual: Double, rumboObjetivo: Double): Double {
        var diferencia = Haversine.normalizarRumbo(rumboObjetivo - rumboActual)
        if (diferencia > 180.0) diferencia -= 360.0
        return diferencia
    }

    /** F si la diferencia está entre -umbral y +umbral; R si el objetivo está a la derecha; L si está a la izquierda. */
    fun calcular(
        rumboActual: Double,
        rumboObjetivo: Double,
        umbralRectoGrados: Double = ConfigGuia.UMBRAL_RECTO_GRADOS
    ): Indicacion {
        val diferencia = diferenciaGrados(rumboActual, rumboObjetivo)
        return when {
            diferencia > umbralRectoGrados -> Indicacion.DERECHA
            diferencia < -umbralRectoGrados -> Indicacion.IZQUIERDA
            else -> Indicacion.RECTO
        }
    }

    /** Indicación para ir desde [posicion] hacia [objetivo] mirando hacia [rumboActual]. */
    fun hacia(posicion: Coordenada, rumboActual: Double, objetivo: Coordenada): Indicacion =
        calcular(rumboActual, Haversine.rumboGrados(posicion, objetivo))
}
