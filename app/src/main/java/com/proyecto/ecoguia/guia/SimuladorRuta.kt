package com.proyecto.ecoguia.guia

import kotlin.math.sin

data class LecturaSimulada(
    val posicion: Coordenada,
    val rumbo: Double,
    val giro: Double
)

/**
 * Genera posiciones y rumbos falsos que recorren la ruta a la velocidad de caminata.
 * El rumbo oscila alrededor de la dirección correcta para que la guía alterne L, R y F.
 */
class SimuladorRuta(
    private val puntos: List<Coordenada>,
    private val velocidadMetrosSegundo: Double = ConfigGuia.VELOCIDAD_CAMINATA_POR_DEFECTO_MS,
    private val amplitudOscilacionGrados: Double = 35.0,
    private val periodoOscilacionPasos: Int = 12,
    inicio: Coordenada? = null
) {
    private var posicion: Coordenada = inicio ?: puntos.firstOrNull() ?: Coordenada(0.0, 0.0)
    private var indiceDestino = if (inicio == null) 1 else 0
    private var paso = 0
    private var rumboAnterior: Double? = null
    private var rumboBaseAnterior = 0.0

    val terminado: Boolean get() = indiceDestino > puntos.lastIndex

    /** Avanza [segundos] y devuelve la nueva lectura simulada. */
    fun avanzar(segundos: Double = ConfigGuia.INTERVALO_SIMULACION_MS / 1000.0): LecturaSimulada {
        var restante = velocidadMetrosSegundo * segundos
        var rumboBase = rumboBaseAnterior

        while (restante > 0.0 && indiceDestino <= puntos.lastIndex) {
            val destino = puntos[indiceDestino]
            val distancia = Haversine.distanciaMetros(posicion, destino)
            rumboBase = Haversine.rumboGrados(posicion, destino)
            if (distancia <= restante) {
                posicion = destino
                restante -= distancia
                indiceDestino++
            } else {
                posicion = Haversine.destino(posicion, rumboBase, restante)
                restante = 0.0
            }
        }

        rumboBaseAnterior = rumboBase
        paso++
        val oscilacion = amplitudOscilacionGrados *
                sin(2 * Math.PI * paso / periodoOscilacionPasos.coerceAtLeast(1))
        val rumbo = Haversine.normalizarRumbo(rumboBase + oscilacion)
        val giro = rumboAnterior?.let {
            DireccionRelativa.diferenciaGrados(it, rumbo) / segundos
        } ?: 0.0
        rumboAnterior = rumbo
        return LecturaSimulada(posicion, rumbo, giro)
    }
}
