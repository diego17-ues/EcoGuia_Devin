package com.proyecto.ecoguia.guia

/**
 * Parámetros de la guía de navegación y del rastreo.
 * Se agrupan aquí para ajustarlos sin buscar valores repartidos por el código.
 */
object ConfigGuia {

    /** Nombre Bluetooth con el que el ESP32 aparece entre los dispositivos emparejados. */
    const val NOMBRE_DISPOSITIVO_ESP32 = "EcoGuia_ESP32"

    /** UUID estándar del perfil de puerto serie (SPP) de Bluetooth Classic. */
    const val UUID_SPP = "00001101-0000-1000-8000-00805F9B34FB"

    /** Espera entre intentos de reconexión con el ESP32. */
    const val INTERVALO_RECONEXION_MS = 3_000L

    /** Latido "H" hacia el ESP32; el firmware entra en respaldo tras 3 s sin datos. */
    const val INTERVALO_LATIDO_MS = 1_000L

    /** Una lectura de rumbo del ESP32 (10 Hz) se considera vigente durante este tiempo. */
    const val VIGENCIA_RUMBO_ESP32_MS = 1_000L

    /** Una posición del GPS del ESP32 se considera vigente durante este tiempo. */
    const val VIGENCIA_POSICION_ESP32_MS = 3_000L

    /** Una posición del GPS del teléfono se considera vigente durante este tiempo. */
    const val VIGENCIA_POSICION_TELEFONO_MS = 10_000L

    /** Diferencia máxima (en grados, a cada lado) para indicar "seguir recto". */
    const val UMBRAL_RECTO_GRADOS = 20.0

    /** Velocidad angular (°/s) a partir de la cual se considera que la persona está girando. */
    const val UMBRAL_GIRO_GRADOS_SEGUNDO = 30.0

    /** Velocidad de caminata usada para estimar tiempos. */
    const val VELOCIDAD_CAMINATA_POR_DEFECTO_MS = 1.2

    /** Distancia a la que un nodo de la ruta se da por alcanzado. */
    const val UMBRAL_LLEGADA_NODO_METROS = 6.0

    /** Distancia a la que un obstáculo se da por superado y se pasa al siguiente. */
    const val UMBRAL_OBSTACULO_SUPERADO_METROS = 5.0

    /** Frecuencia con que se recalcula la guía. */
    const val INTERVALO_CALCULO_GUIA_MS = 250L

    /** Intervalo de lectura del GPS del teléfono durante una sesión. */
    const val INTERVALO_GPS_TELEFONO_MS = 1_000L

    /** Envío de la posición del teléfono a posiciones_usuario. */
    const val INTERVALO_ENVIO_POSICION_MS = 10_000L

    /** Paso del simulador de recorrido. */
    const val INTERVALO_SIMULACION_MS = 1_000L

    /** Bandera para iniciar las sesiones directamente en modo simulación (sin ESP32 ni GPS reales). */
    const val SIMULACION_ACTIVA_POR_DEFECTO = false
}
