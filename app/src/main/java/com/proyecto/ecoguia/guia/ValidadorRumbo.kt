package com.proyecto.ecoguia.guia

import kotlin.math.abs
import kotlin.math.cos

enum class EstadoValidacion(val texto: String) {
    EN_RUTA("Vas por la ruta correcta"),
    DESVIADO("Te desviaste del rumbo"),
    SIN_MPU("Sin datos del MPU"),
    SIN_POSICION("Sin posición"),
    RUTA_TERMINADA("Destino alcanzado")
}

data class ResultadoValidacion(
    val estado: EstadoValidacion,
    /** Letra de estado para el ESP32 (L, R o A); null si no se puede validar. */
    val indicacion: Indicacion?,
    /** Diferencia firmada entre el rumbo (anticipado con el giro) y el de la ruta. Positiva: la ruta queda a la derecha. */
    val errorRumboGrados: Double? = null,
    /** Distancia a la línea del tramo actual. Positiva: la persona está a la derecha del tramo. */
    val desvioLateralMetros: Double? = null
)

/**
 * Valida con el MPU9250 si la persona sigue la ruta y decide la letra de estado (L, R o A) para el
 * ESP32. No es un modelo entrenado: es un filtro de reglas pensado para no hacer sonar el buzzer
 * por ruido del sensor o del GPS.
 * - Apunta a un punto [DISTANCIA_OBJETIVO_TRAMO_METROS] por delante sobre el tramo, así corrige
 *   también cuando la persona se apartó de la línea aunque mire hacia el nodo.
 * - Anticipa el rumbo con la velocidad de giro (gyro Z): si ya está girando hacia la ruta, no corrige.
 * - Histéresis: corrige a partir de [umbralRecto] y vuelve a "A" por debajo de [umbralRegreso].
 * - Un cambio debe repetirse [calculosConfirmacion] veces seguidas, salvo que vaya en otra dirección.
 * Sin rumbo del MPU no valida: devuelve SIN_MPU y ninguna letra.
 */
class ValidadorRumbo(
    private val umbralRecto: Double = ConfigGuia.UMBRAL_RECTO_GRADOS,
    private val umbralRegreso: Double = ConfigGuia.UMBRAL_REGRESO_RECTO_GRADOS,
    private val umbralInmediato: Double = ConfigGuia.UMBRAL_CORRECCION_INMEDIATA_GRADOS,
    private val anticipacionSegundos: Double = ConfigGuia.ANTICIPACION_GIRO_SEGUNDOS,
    private val limiteAnticipacion: Double = ConfigGuia.LIMITE_ANTICIPACION_GRADOS,
    private val distanciaObjetivo: Double = ConfigGuia.DISTANCIA_OBJETIVO_TRAMO_METROS,
    private val calculosConfirmacion: Int = ConfigGuia.CALCULOS_CONFIRMACION
) {
    private var vigente: Indicacion? = null
    private var candidata: Indicacion? = null
    private var repeticiones = 0

    /** Olvida la decisión anterior (p. ej. al alcanzar un nodo, cuando cambia el tramo). */
    fun reiniciar() {
        vigente = null
        candidata = null
        repeticiones = 0
    }

    /**
     * @param anterior nodo ya alcanzado (inicio del tramo), o null si aún no se alcanzó ninguno.
     * @param siguiente nodo objetivo, o null si la ruta terminó.
     */
    fun evaluar(
        posicion: Coordenada?,
        rumbo: Double?,
        giroGradosSegundo: Double?,
        anterior: Coordenada?,
        siguiente: Coordenada?
    ): ResultadoValidacion {
        if (siguiente == null) {
            reiniciar()
            return ResultadoValidacion(EstadoValidacion.RUTA_TERMINADA, null)
        }
        if (posicion == null) {
            reiniciar()
            return ResultadoValidacion(EstadoValidacion.SIN_POSICION, null)
        }
        val tramo = anterior?.let { proyectar(it, siguiente, posicion) }
        if (rumbo == null) {
            reiniciar()
            return ResultadoValidacion(EstadoValidacion.SIN_MPU, null, desvioLateralMetros = tramo?.lateral)
        }

        val objetivo = tramo?.objetivo ?: siguiente
        val anticipacion = ((giroGradosSegundo ?: 0.0) * anticipacionSegundos)
            .coerceIn(-limiteAnticipacion, limiteAnticipacion)
        val rumboAnticipado = Haversine.normalizarRumbo(rumbo + anticipacion)
        val error = DireccionRelativa.diferenciaGrados(rumboAnticipado, Haversine.rumboGrados(posicion, objetivo))

        val propuesta = when {
            error > umbralRecto -> Indicacion.DERECHA
            error < -umbralRecto -> Indicacion.IZQUIERDA
            vigente == Indicacion.DERECHA && error > umbralRegreso -> Indicacion.DERECHA
            vigente == Indicacion.IZQUIERDA && error < -umbralRegreso -> Indicacion.IZQUIERDA
            else -> Indicacion.RECTO
        }
        confirmar(propuesta, inmediata = abs(error) >= umbralInmediato)

        val decision = vigente ?: propuesta
        return ResultadoValidacion(
            estado = if (decision == Indicacion.RECTO) EstadoValidacion.EN_RUTA else EstadoValidacion.DESVIADO,
            indicacion = decision,
            errorRumboGrados = error,
            desvioLateralMetros = tramo?.lateral
        )
    }

    private fun confirmar(propuesta: Indicacion, inmediata: Boolean) {
        when {
            vigente == null || inmediata || propuesta == vigente -> {
                vigente = propuesta
                candidata = null
                repeticiones = 0
            }
            propuesta == candidata -> {
                repeticiones++
                if (repeticiones >= calculosConfirmacion) {
                    vigente = propuesta
                    candidata = null
                    repeticiones = 0
                }
            }
            else -> {
                candidata = propuesta
                repeticiones = 1
                if (repeticiones >= calculosConfirmacion) {
                    vigente = propuesta
                    candidata = null
                    repeticiones = 0
                }
            }
        }
    }

    private data class Tramo(val objetivo: Coordenada, val lateral: Double)

    /** Proyección en un plano local (x = este, y = norte), suficiente para un tramo a pie. */
    private fun proyectar(inicio: Coordenada, fin: Coordenada, posicion: Coordenada): Tramo? {
        val metrosPorGrado = Math.toRadians(1.0) * Haversine.RADIO_TIERRA_METROS
        val metrosPorGradoLon = metrosPorGrado * cos(Math.toRadians(inicio.latitud))
        val xF = (fin.longitud - inicio.longitud) * metrosPorGradoLon
        val yF = (fin.latitud - inicio.latitud) * metrosPorGrado
        val longitud = kotlin.math.sqrt(xF * xF + yF * yF)
        if (longitud < 1.0) return null
        val xP = (posicion.longitud - inicio.longitud) * metrosPorGradoLon
        val yP = (posicion.latitud - inicio.latitud) * metrosPorGrado
        val avance = (xP * xF + yP * yF) / longitud
        val lateral = (xP * yF - yP * xF) / longitud
        val objetivo = if (avance + distanciaObjetivo >= longitud) fin else {
            val t = (avance.coerceAtLeast(0.0) + distanciaObjetivo) / longitud
            Coordenada(inicio.latitud + yF * t / metrosPorGrado, inicio.longitud + xF * t / metrosPorGradoLon)
        }
        return Tramo(objetivo, lateral)
    }
}
