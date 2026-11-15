package com.proyecto.ecoguia.guia

import kotlin.math.roundToLong

/** Estimación de tiempos de recorrido a partir de distancias y la velocidad de caminata. */
class EstimadorTiempo(
    val velocidadMetrosSegundo: Double = ConfigGuia.VELOCIDAD_CAMINATA_POR_DEFECTO_MS
) {
    init {
        require(velocidadMetrosSegundo > 0.0) { "La velocidad de caminata debe ser mayor que cero." }
    }

    fun segundosPara(distanciaMetros: Double): Long =
        (distanciaMetros.coerceAtLeast(0.0) / velocidadMetrosSegundo).roundToLong()

    companion object {

        /**
         * Longitud de cada tramo i → i+1. Usa la longitud de la arista si se conoce
         * (distancia_m) y, si no, la distancia Haversine entre los nodos consecutivos.
         */
        fun longitudesTramos(
            puntos: List<Coordenada>,
            longitudesAristas: List<Double?>
        ): List<Double> = puntos.zipWithNext().mapIndexed { indice, (desde, hasta) ->
            longitudesAristas.getOrNull(indice)
                ?.takeIf { it.isFinite() && it >= 0.0 }
                ?: Haversine.distanciaMetros(desde, hasta)
        }

        /** Distancia total de la ruta (suma de sus tramos). */
        fun distanciaTotal(puntos: List<Coordenada>, longitudesAristas: List<Double?>): Double =
            longitudesTramos(puntos, longitudesAristas).sum()

        /**
         * Distancia que falta: desde la posición actual hasta el nodo objetivo, más los
         * tramos que quedan desde ese nodo hasta el final.
         */
        fun distanciaRestante(
            posicion: Coordenada,
            puntos: List<Coordenada>,
            longitudesTramos: List<Double>,
            indiceObjetivo: Int
        ): Double {
            if (indiceObjetivo !in puntos.indices) return 0.0
            val hastaObjetivo = Haversine.distanciaMetros(posicion, puntos[indiceObjetivo])
            val resto = (indiceObjetivo until puntos.lastIndex).sumOf { longitudesTramos[it] }
            return hastaObjetivo + resto
        }

        /** Texto corto en español, por ejemplo "4 min 30 s". */
        fun formatear(segundos: Long): String {
            val total = segundos.coerceAtLeast(0)
            val horas = total / 3600
            val minutos = (total % 3600) / 60
            val seg = total % 60
            return when {
                horas > 0 -> "$horas h $minutos min"
                minutos > 0 -> if (seg > 0) "$minutos min $seg s" else "$minutos min"
                else -> "$seg s"
            }
        }
    }
}
