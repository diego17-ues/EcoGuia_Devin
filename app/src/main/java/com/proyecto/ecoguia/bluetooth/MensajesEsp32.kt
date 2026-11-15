package com.proyecto.ecoguia.bluetooth

/**
 * Protocolo de texto entre la app y el ESP32 (Bluetooth Classic SPP). Cada mensaje termina en \n.
 *
 * ESP32 → app:
 *   Y,rumbo,giro          10 Hz. rumbo magnético 0–360° (MPU9250); giro = velocidad angular en Z (°/s).
 *   P,latitud,longitud,fix  Posición del GPS NEO-6M; fix = 1 válida, 0 no válida.
 *
 * App → ESP32:
 *   L | R | F   Indicación (izquierda, derecha, recto); solo cuando cambia.
 *   H           Latido cada segundo mientras la sesión está activa.
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
            rumbo < 0.0 || rumbo > 360.0 -> MensajeEsp32.Invalido(linea, "rumbo fuera de 0–360")
            giro == null || !giro.isFinite() -> MensajeEsp32.Invalido(linea, "giro no numérico")
            else -> MensajeEsp32.Rumbo(if (rumbo == 360.0) 0.0 else rumbo, giro)
        }
    }

    private fun parsearPosicion(linea: String, partes: List<String>): MensajeEsp32 {
        if (partes.size != 4) return MensajeEsp32.Invalido(linea, "P espera 3 valores")
        val latitud = partes[1].toDoubleOrNull()
        val longitud = partes[2].toDoubleOrNull()
        return when {
            latitud == null || latitud !in -90.0..90.0 -> MensajeEsp32.Invalido(linea, "latitud inválida")
            longitud == null || longitud !in -180.0..180.0 -> MensajeEsp32.Invalido(linea, "longitud inválida")
            partes[3] != "0" && partes[3] != "1" -> MensajeEsp32.Invalido(linea, "fix debe ser 0 o 1")
            else -> MensajeEsp32.Posicion(latitud, longitud, partes[3] == "1")
        }
    }
}
