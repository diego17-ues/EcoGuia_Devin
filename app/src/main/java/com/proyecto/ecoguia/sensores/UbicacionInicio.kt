package com.proyecto.ecoguia.sensores

import android.content.Context
import com.proyecto.ecoguia.bluetooth.ConexionEsp32
import com.proyecto.ecoguia.bluetooth.EstadoEsp32
import com.proyecto.ecoguia.bluetooth.MensajeEsp32
import com.proyecto.ecoguia.guia.ConfigGuia
import com.proyecto.ecoguia.guia.Coordenada
import com.proyecto.ecoguia.guia.PreferenciasGuia
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

enum class FuenteUbicacionInicio(val texto: String) {
    ESP32("GPS del dispositivo EcoGuía"),
    TELEFONO("GPS del teléfono")
}

data class UbicacionInicio(
    val coordenada: Coordenada,
    val fuente: FuenteUbicacionInicio,
    /** El ESP32 respondió pero el NEO-7M aún no tiene satélites, por eso se usó el teléfono. */
    val esp32BuscandoSatelites: Boolean = false
)

private sealed interface LecturaEsp32 {
    data class ConFix(val coordenada: Coordenada) : LecturaEsp32
    object BuscandoSatelites : LecturaEsp32
    object SinConexion : LecturaEsp32
}

/** Estados en los que reintentar la conexión no sirve durante la validación. */
private val estadosSinConexionPosible = setOf(
    EstadoEsp32.SIN_PERMISO,
    EstadoEsp32.BLUETOOTH_NO_DISPONIBLE,
    EstadoEsp32.BLUETOOTH_APAGADO,
    EstadoEsp32.NO_EMPAREJADO
)

/**
 * Ubicación para validar el nodo de inicio: el GPS del ESP32 (NEO-7M) si está conectado y ya
 * tiene satélites; si no, el GPS del teléfono, que se lee en paralelo para no alargar la espera.
 */
suspend fun obtenerUbicacionInicio(contexto: Context): UbicacionInicio? = coroutineScope {
    val telefono = async { obtenerUbicacionActual(contexto) }
    val lecturaEsp32 = if (PreferenciasGuia.dispositivoActivo(contexto)) {
        leerPosicionEsp32(contexto)
    } else {
        LecturaEsp32.SinConexion
    }
    if (lecturaEsp32 is LecturaEsp32.ConFix) {
        telefono.cancel()
        UbicacionInicio(lecturaEsp32.coordenada, FuenteUbicacionInicio.ESP32)
    } else {
        telefono.await()?.let {
            UbicacionInicio(
                Coordenada(it.latitude, it.longitude),
                FuenteUbicacionInicio.TELEFONO,
                esp32BuscandoSatelites = lecturaEsp32 == LecturaEsp32.BuscandoSatelites
            )
        }
    }
}

/** Conexión temporal con el ESP32 solo para leer un `P` con fix; se cierra al terminar. */
private suspend fun leerPosicionEsp32(contexto: Context): LecturaEsp32 = coroutineScope {
    val posicion = CompletableDeferred<Coordenada>()
    val conexion = ConexionEsp32(contexto, this, alRecibir = { mensaje ->
        if (mensaje is MensajeEsp32.Posicion && mensaje.fixValido) {
            posicion.complete(Coordenada(mensaje.latitud, mensaje.longitud))
        }
    })
    conexion.iniciar()
    try {
        val conectado = withTimeoutOrNull(ConfigGuia.ESPERA_CONEXION_ESP32_INICIO_MS) {
            conexion.estado.first { it.conectado || it.estado in estadosSinConexionPosible }
        }?.conectado == true
        if (!conectado) {
            LecturaEsp32.SinConexion
        } else {
            withTimeoutOrNull(ConfigGuia.ESPERA_FIX_ESP32_INICIO_MS) { posicion.await() }
                ?.let { LecturaEsp32.ConFix(it) }
                ?: LecturaEsp32.BuscandoSatelites
        }
    } finally {
        conexion.detener()
    }
}
