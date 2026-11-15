package com.proyecto.ecoguia.bluetooth

import com.proyecto.ecoguia.guia.Indicacion

/**
 * Protocolo de texto entre la app y el ESP32 (Bluetooth Classic SPP). Cada mensaje termina en \n.
 *
 * ESP32 → app:
 *   Y,rumbo,giro            10 Hz (MODO_APP del firmware): rumboMagnetico() y mpu.getGyroZ() (°/s).
 *   P,latitud,longitud[,fix] Posición del GPS NEO-7M; fix = 1 válida, 0 no válida. Sin fix se
 *                            toma como válida; los campos adicionales (satélites, HDOP…) se ignoran.
 *                            El ESP32 no la envía hasta encontrar satélites: mientras tanto la app
 *                            usa el GPS del teléfono.
 *
 * App → ESP32 (el firmware hace sonar el buzzer con el patrón de cada letra):
 *   L   Gira a la izquierda.       ┐ Letras de estado: se envían al cambiar y se repiten cada
 *   R   Gira a la derecha.         │ segundo como latido.
 *   A   Vas bien, sigue adelante.  ┘
 *   N   Nodo alcanzado.            ┐ Avisos: una sola vez cuando ocurren.
 *   F   Destino final.             ┘
 *   H   Latido opcional (conexión abierta) cuando no hay letra de estado, p. ej. sin datos del MPU.
 */
sealed interface MensajeEsp32 {

    data class Rumbo(val rumboGrados: Double, val giroGradosSegundo: Double) : MensajeEsp32

    data class Posicion(
        val latitud: Double,
        val longitud: Double,
        val fixValido: Boolean
    ) : MensajeEsp32

    /** Línea que no cumple el formato; se registra en logs y se ignora. */
    data class Invalido(val linea: String, val motivo: String) : MensajeEsp32
}

object ParserMensajesEsp32 {

    const val LATIDO = "H"

    /** Mensaje repetido cada segundo: la indicación vigente o, si no hay, el latido "H". */
    fun mensajeLatido(indicacion: Indicacion?): String = indicacion?.letra?.toString() ?: LATIDO

    fun parsear(lineaRecibida: String): MensajeEsp32 {
        val linea = lineaRecibida.trim()
        if (linea.isEmpty()) return MensajeEsp32.Invalido(lineaRecibida, "línea vacía")

        val partes = linea.split(',').map { it.trim() }
        return when (partes[0].uppercase()) {
            "Y" -> parsearRumbo(linea, partes)
            "P" -> parsearPosicion(linea, partes)
            else -> MensajeEsp32.Invalido(linea, "tipo de mensaje desconocido")
        }
    }

    private fun parsearRumbo(linea: String, partes: List<String>): MensajeEsp32 {
        if (partes.size != 3) return MensajeEsp32.Invalido(linea, "Y espera 2 valores")
        val rumbo = partes[1].toDoubleOrNull()
        val giro = partes[2].toDoubleOrNull()
        return when {
            rumbo == null || !rumbo.isFinite() -> MensajeEsp32.Invalido(linea, "rumbo no numérico")
            rumbo < -360.0 || rumbo > 360.0 -> MensajeEsp32.Invalido(linea, "rumbo fuera de ±360")
            giro == null || !giro.isFinite() -> MensajeEsp32.Invalido(linea, "giro no numérico")
            else -> MensajeEsp32.Rumbo(
                when {
                    rumbo < 0.0 -> rumbo + 360.0
                    rumbo >= 360.0 -> rumbo - 360.0
                    else -> rumbo
                },
                giro
            )
        }
    }

    private fun parsearPosicion(linea: String, partes: List<String>): MensajeEsp32 {
        if (partes.size < 3) return MensajeEsp32.Invalido(linea, "P espera latitud y longitud")
        val latitud = partes[1].toDoubleOrNull()
        val longitud = partes[2].toDoubleOrNull()
        val fix = partes.getOrNull(3)
        return when {
            latitud == null || latitud !in -90.0..90.0 -> MensajeEsp32.Invalido(linea, "latitud inválida")
            longitud == null || longitud !in -180.0..180.0 -> MensajeEsp32.Invalido(linea, "longitud inválida")
            fix != null && fix != "0" && fix != "1" -> MensajeEsp32.Invalido(linea, "fix debe ser 0 o 1")
            // 0,0 es lo que suele enviar el GPS antes de tener señal.
            latitud == 0.0 && longitud == 0.0 -> MensajeEsp32.Posicion(latitud, longitud, false)
            else -> MensajeEsp32.Posicion(latitud, longitud, fix != "0")
        }
    }
}
