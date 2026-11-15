package com.proyecto.ecoguia.guia

enum class FuentePosicion(val texto: String) {
    ESP32("GPS del dispositivo EcoGuía (ESP32)"),
    TELEFONO("GPS del teléfono"),
    SIMULACION("Simulación"),
    NINGUNA("Sin posición todavía")
}

enum class FuenteRumbo(val texto: String) {
    ESP32("Brújula del dispositivo (MPU9250)"),
    TELEFONO("Brújula del teléfono"),
    SIMULACION("Simulación"),
    NINGUNA("Sin rumbo todavía")
}

/** Última lectura recibida de una fuente, con su marca de tiempo (reloj monotónico, ms). */
data class Lectura<T>(val valor: T, val instanteMs: Long)

data class PosicionEsp32(val coordenada: Coordenada, val fixValido: Boolean)

data class EntradasGuia(
    val simulacion: Boolean,
    val esp32Conectado: Boolean,
    val posicionEsp32: Lectura<PosicionEsp32>?,
    val rumboEsp32: Lectura<Double>?,
    val posicionTelefono: Lectura<Coordenada>?,
    val rumboTelefono: Lectura<Double>?,
    val posicionSimulada: Lectura<Coordenada>?,
    val rumboSimulado: Lectura<Double>?
)

data class FuentesElegidas(
    val posicion: Coordenada?,
    val fuentePosicion: FuentePosicion,
    val rumbo: Double?,
    val fuenteRumbo: FuenteRumbo
)

/**
 * Prioridad de fuentes:
 * - Simulación activa: se usan solo los datos simulados.
 * - ESP32 conectado y con fix = 1 reciente: posición del GPS del ESP32.
 * - ESP32 conectado con lecturas "Y" recientes: rumbo del MPU9250 (aunque el GPS no tenga fix).
 * - En cualquier otro caso: GPS y brújula del teléfono.
 */
object SelectorFuente {

    fun elegir(entradas: EntradasGuia, ahoraMs: Long): FuentesElegidas {
        if (entradas.simulacion) {
            return FuentesElegidas(
                posicion = entradas.posicionSimulada?.valor,
                fuentePosicion = if (entradas.posicionSimulada != null) FuentePosicion.SIMULACION else FuentePosicion.NINGUNA,
                rumbo = entradas.rumboSimulado?.valor,
                fuenteRumbo = if (entradas.rumboSimulado != null) FuenteRumbo.SIMULACION else FuenteRumbo.NINGUNA
            )
        }

        val posicionEsp32 = entradas.posicionEsp32
            ?.takeIf {
                entradas.esp32Conectado && it.valor.fixValido &&
                        ahoraMs - it.instanteMs <= ConfigGuia.VIGENCIA_POSICION_ESP32_MS
            }
        val posicionTelefono = entradas.posicionTelefono
            ?.takeIf { ahoraMs - it.instanteMs <= ConfigGuia.VIGENCIA_POSICION_TELEFONO_MS }

        val rumboEsp32 = entradas.rumboEsp32
            ?.takeIf {
                entradas.esp32Conectado &&
                        ahoraMs - it.instanteMs <= ConfigGuia.VIGENCIA_RUMBO_ESP32_MS
            }

        val (posicion, fuentePosicion) = when {
            posicionEsp32 != null -> posicionEsp32.valor.coordenada to FuentePosicion.ESP32
            posicionTelefono != null -> posicionTelefono.valor to FuentePosicion.TELEFONO
            else -> null to FuentePosicion.NINGUNA
        }
        val (rumbo, fuenteRumbo) = when {
            rumboEsp32 != null -> rumboEsp32.valor to FuenteRumbo.ESP32
            entradas.rumboTelefono != null -> entradas.rumboTelefono.valor to FuenteRumbo.TELEFONO
            else -> null to FuenteRumbo.NINGUNA
        }
        return FuentesElegidas(posicion, fuentePosicion, rumbo, fuenteRumbo)
    }
}
