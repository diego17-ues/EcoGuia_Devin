package com.proyecto.ecoguia.guia

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Punto geográfico en grados decimales (WGS84). */
data class Coordenada(
    val latitud: Double,
    val longitud: Double
)

/** Cálculos geodésicos sobre una esfera con el radio medio de la Tierra. */
object Haversine {

    const val RADIO_TIERRA_METROS = 6_371_008.8

    /** Distancia en metros entre dos puntos usando la fórmula de Haversine. */
    fun distanciaMetros(
        latitudA: Double,
        longitudA: Double,
        latitudB: Double,
        longitudB: Double
    ): Double {
        val lat1 = Math.toRadians(latitudA)
        val lat2 = Math.toRadians(latitudB)
        val deltaLat = Math.toRadians(latitudB - latitudA)
        val deltaLon = Math.toRadians(longitudB - longitudA)

        val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
                cos(lat1) * cos(lat2) * sin(deltaLon / 2) * sin(deltaLon / 2)
        // min() evita errores de redondeo que dejen a ligeramente por encima de 1.
        return 2 * RADIO_TIERRA_METROS * asin(min(1.0, sqrt(a)))
    }

    fun distanciaMetros(desde: Coordenada, hasta: Coordenada): Double =
        distanciaMetros(desde.latitud, desde.longitud, hasta.latitud, hasta.longitud)

    /** Rumbo inicial (0–360°, 0 = norte, sentido horario) para ir desde un punto hacia otro. */
    fun rumboGrados(desde: Coordenada, hasta: Coordenada): Double {
        val lat1 = Math.toRadians(desde.latitud)
        val lat2 = Math.toRadians(hasta.latitud)
        val deltaLon = Math.toRadians(hasta.longitud - desde.longitud)

        val y = sin(deltaLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
        return normalizarRumbo(Math.toDegrees(atan2(y, x)))
    }

    /**
     * Punto que se alcanza al avanzar [distanciaMetros] desde [origen] con el rumbo dado.
     * Lo usa el simulador para mover la posición a lo largo de la ruta.
     */
    fun destino(origen: Coordenada, rumboGrados: Double, distanciaMetros: Double): Coordenada {
        val distanciaAngular = distanciaMetros / RADIO_TIERRA_METROS
        val rumbo = Math.toRadians(rumboGrados)
        val lat1 = Math.toRadians(origen.latitud)
        val lon1 = Math.toRadians(origen.longitud)

        val lat2 = asin(
            sin(lat1) * cos(distanciaAngular) +
                    cos(lat1) * sin(distanciaAngular) * cos(rumbo)
        )
        val lon2 = lon1 + atan2(
            sin(rumbo) * sin(distanciaAngular) * cos(lat1),
            cos(distanciaAngular) - sin(lat1) * sin(lat2)
        )
        return Coordenada(
            latitud = Math.toDegrees(lat2),
            longitud = (Math.toDegrees(lon2) + 540.0) % 360.0 - 180.0
        )
    }

    /** Lleva cualquier ángulo al rango [0, 360). */
    fun normalizarRumbo(grados: Double): Double {
        val resto = grados % 360.0
        return if (resto < 0) resto + 360.0 else resto
    }
}
