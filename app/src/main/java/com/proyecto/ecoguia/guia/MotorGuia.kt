package com.proyecto.ecoguia.guia

import java.text.Normalizer

/** Nodo de la ruta tal como lo usa la guía. */
data class PuntoRuta(
    val id: Long,
    val nombre: String,
    val tipo: String,
    val descripcion: String,
    val coordenada: Coordenada
) {
    val esObstaculo: Boolean get() = esTipoObstaculo(tipo)

    companion object {
        /** Tipo que se guarda en nodos.tipo para marcar un obstáculo. */
        const val TIPO_OBSTACULO = "Obstáculo"

        /**
         * El esquema no tiene un campo booleano de obstáculo; mientras tanto se usa el
         * texto libre de nodos.tipo (sin distinguir mayúsculas ni tildes).
         */
        fun esTipoObstaculo(tipo: String): Boolean {
            val normalizado = Normalizer.normalize(tipo, Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .lowercase()
            return "obstaculo" in normalizado
        }
    }
}

/** Ruta ordenada (según sus aristas) con la longitud conocida de cada tramo, si existe. */
data class RutaGuia(
    val id: Long,
    val nombre: String,
    val puntos: List<PuntoRuta>,
    /** Elemento i = distancia_m de la arista puntos[i] → puntos[i+1], o null si no se conoce. */
    val longitudesAristas: List<Double?> = emptyList()
)

data class ObstaculoProximo(
    val punto: PuntoRuta,
    val distanciaMetros: Double?,
    val indicacion: Indicacion?
)

data class EstadoGuia(
    val indiceObjetivo: Int,
    val siguientePunto: PuntoRuta?,
    val distanciaSiguientePuntoMetros: Double?,
    val indicacion: Indicacion?,
    val distanciaTotalMetros: Double,
    val distanciaRestanteMetros: Double,
    val tiempoTotalSegundos: Long,
    val tiempoRestanteSegundos: Long,
    val obstaculo: ObstaculoProximo?,
    val obstaculosPendientes: Int,
    val finalizada: Boolean
)

/**
 * Calcula la guía (nodo objetivo, indicación, tiempos y obstáculo siguiente) a partir de la
 * posición y el rumbo que entregue cualquier fuente. Guarda su avance internamente, así que
 * cambiar de fuente (ESP32, teléfono o simulación) no reinicia la ruta.
 */
class MotorGuia(
    val ruta: RutaGuia,
    /** Se puede reemplazar en caliente si la persona cambia la velocidad de caminata. */
    var estimador: EstimadorTiempo = EstimadorTiempo(),
    indiceInicial: Int = 0,
    private val umbralLlegadaNodoMetros: Double = ConfigGuia.UMBRAL_LLEGADA_NODO_METROS,
    private val umbralObstaculoMetros: Double = ConfigGuia.UMBRAL_OBSTACULO_SUPERADO_METROS
) {
    private val puntos = ruta.puntos
    private val coordenadas = puntos.map { it.coordenada }
    private val longitudesTramos = EstimadorTiempo.longitudesTramos(coordenadas, ruta.longitudesAristas)

    val distanciaTotalMetros: Double = longitudesTramos.sum()
    val tiempoTotalSegundos: Long get() = estimador.segundosPara(distanciaTotalMetros)

    private val indicesObstaculos = puntos.indices.filter { puntos[it].esObstaculo }

    var indiceObjetivo: Int = indiceInicial.coerceIn(0, puntos.size)
        private set

    /** Posición dentro de [indicesObstaculos] del siguiente obstáculo no superado. */
    private var cursorObstaculo = 0

    fun actualizar(posicion: Coordenada?, rumbo: Double?): EstadoGuia {
        if (posicion != null) {
            avanzarNodos(posicion)
        }
        avanzarObstaculos(posicion)

        val finalizada = puntos.isEmpty() || indiceObjetivo > puntos.lastIndex
        val siguiente = puntos.getOrNull(indiceObjetivo)

        val distanciaSiguiente = if (posicion != null && siguiente != null) {
            Haversine.distanciaMetros(posicion, siguiente.coordenada)
        } else null

        val distanciaRestante = when {
            finalizada -> 0.0
            posicion != null -> EstimadorTiempo.distanciaRestante(
                posicion, coordenadas, longitudesTramos, indiceObjetivo
            )
            // Sin posición todavía: se cuentan solo los tramos pendientes.
            else -> (indiceObjetivo until puntos.lastIndex).sumOf { longitudesTramos[it] }
        }

        val indicacion = if (posicion != null && rumbo != null && siguiente != null) {
            DireccionRelativa.hacia(posicion, rumbo, siguiente.coordenada)
        } else null

        val obstaculo = if (finalizada) null else indicesObstaculos.getOrNull(cursorObstaculo)?.let { indice ->
            val punto = puntos[indice]
            ObstaculoProximo(
                punto = punto,
                distanciaMetros = posicion?.let { Haversine.distanciaMetros(it, punto.coordenada) },
                indicacion = if (posicion != null && rumbo != null) {
                    DireccionRelativa.hacia(posicion, rumbo, punto.coordenada)
                } else null
            )
        }

        return EstadoGuia(
            indiceObjetivo = indiceObjetivo,
            siguientePunto = siguiente,
            distanciaSiguientePuntoMetros = distanciaSiguiente,
            indicacion = indicacion,
            distanciaTotalMetros = distanciaTotalMetros,
            distanciaRestanteMetros = distanciaRestante,
            tiempoTotalSegundos = tiempoTotalSegundos,
            tiempoRestanteSegundos = estimador.segundosPara(distanciaRestante),
            obstaculo = obstaculo,
            obstaculosPendientes = if (finalizada) 0 else (indicesObstaculos.size - cursorObstaculo).coerceAtLeast(0),
            finalizada = finalizada
        )
    }

    private fun avanzarNodos(posicion: Coordenada) {
        while (indiceObjetivo <= puntos.lastIndex && nodoAlcanzado(indiceObjetivo, posicion)) {
            indiceObjetivo++
        }
    }

    /**
     * Un nodo se da por alcanzado si se está a menos del umbral o si ya se avanzó por el tramo
     * siguiente (útil cuando el error del GPS impide acercarse a pocos metros del nodo).
     */
    private fun nodoAlcanzado(indice: Int, posicion: Coordenada): Boolean {
        val nodo = puntos[indice].coordenada
        if (Haversine.distanciaMetros(posicion, nodo) < umbralLlegadaNodoMetros) return true
        val siguiente = puntos.getOrNull(indice + 1)?.coordenada ?: return false
        val longitudTramo = Haversine.distanciaMetros(nodo, siguiente)
        if (longitudTramo <= 0.0) return true
        // Proyección sobre el tramo en un plano local (suficiente para distancias de una ruta a pie).
        val (xP, yP) = planoLocal(nodo, posicion)
        val (xS, yS) = planoLocal(nodo, siguiente)
        val avance = (xP * xS + yP * yS) / longitudTramo
        return avance > 0.0 && Haversine.distanciaMetros(posicion, siguiente) < longitudTramo
    }

    private fun planoLocal(origen: Coordenada, punto: Coordenada): Pair<Double, Double> {
        val metrosPorGrado = Math.toRadians(1.0) * Haversine.RADIO_TIERRA_METROS
        val x = (punto.longitud - origen.longitud) * metrosPorGrado * kotlin.math.cos(Math.toRadians(origen.latitud))
        val y = (punto.latitud - origen.latitud) * metrosPorGrado
        return x to y
    }

    private fun avanzarObstaculos(posicion: Coordenada?) {
        while (cursorObstaculo <= indicesObstaculos.lastIndex) {
            val indice = indicesObstaculos[cursorObstaculo]
            val cerca = posicion != null &&
                    Haversine.distanciaMetros(posicion, puntos[indice].coordenada) < umbralObstaculoMetros
            // Respaldo por si el GPS nunca se acerca a 5 m: si ya se alcanzó el nodo que sigue
            // al obstáculo, se considera superado.
            val yaPasado = indice + 1 < indiceObjetivo
            if (cerca || yaPasado) cursorObstaculo++ else break
        }
    }
}
