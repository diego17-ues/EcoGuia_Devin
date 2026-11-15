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

    /**
     * El rumbo `Y` del MPU9250 se toma como magnético y se corrige con la declinación local para
     * compararlo con el rumbo geográfico al nodo. Poner en false si el firmware ya lo corrige.
     */
    const val RUMBO_ESP32_ES_MAGNETICO = true

    /** Diferencia máxima (en grados, a cada lado) para indicar "seguir recto". */
    const val UMBRAL_RECTO_GRADOS = 20.0

    /** Velocidad angular (°/s) a partir de la cual se considera que la persona está girando. */
    const val UMBRAL_GIRO_GRADOS_SEGUNDO = 30.0

    /** Tras una corrección L/R, se vuelve a "A" cuando la diferencia baja de este valor (histéresis). */
    const val UMBRAL_REGRESO_RECTO_GRADOS = 12.0

    /** Diferencia a partir de la cual la corrección se envía sin esperar confirmación (va en otra dirección). */
    const val UMBRAL_CORRECCION_INMEDIATA_GRADOS = 90.0

    /** Segundos que se anticipa el rumbo con el giro del MPU: si ya está girando hacia la ruta, no se corrige. */
    const val ANTICIPACION_GIRO_SEGUNDOS = 0.5

    /** Máximo que puede mover el rumbo la anticipación del giro. */
    const val LIMITE_ANTICIPACION_GRADOS = 45.0

    /** Distancia por delante sobre el tramo hacia la que se apunta (devuelve a la ruta si se apartó). */
    const val DISTANCIA_OBJETIVO_TRAMO_METROS = 8.0

    /** Cálculos seguidos (cada 250 ms) que debe repetirse un cambio de L/R/A antes de enviarlo. */
    const val CALCULOS_CONFIRMACION = 2

    /** Pausa en las letras de estado tras N o F, para que el buzzer termine ese aviso (< 3 s del firmware). */
    const val PAUSA_TRAS_AVISO_MS = 1_200L

    /** Velocidad de caminata usada para estimar tiempos. */
    const val VELOCIDAD_CAMINATA_POR_DEFECTO_MS = 1.2

    /** Distancia a la que un nodo de la ruta se da por alcanzado. */
    const val UMBRAL_LLEGADA_NODO_METROS = 6.0

    /** Distancia máxima al primer nodo de la ruta para permitir iniciar el recorrido. */
    const val RADIO_NODO_INICIO_METROS = 15.0

    /** Tiempo máximo para obtener una ubicación nueva del GPS del teléfono al iniciar. */
    const val ESPERA_UBICACION_INICIO_MS = 15_000L

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
