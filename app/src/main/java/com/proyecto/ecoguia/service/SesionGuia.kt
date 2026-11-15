package com.proyecto.ecoguia.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.proyecto.ecoguia.bluetooth.ConexionEsp32Estado
import com.proyecto.ecoguia.guia.EstadoGuia
import com.proyecto.ecoguia.guia.FuentesElegidas
import com.proyecto.ecoguia.guia.RutaGuia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Estado observable de la sesión de guía que ejecuta [ServicioGuia]. */
data class EstadoSesionGuia(
    val activa: Boolean = false,
    val cargando: Boolean = false,
    val error: String? = null,
    val seguimientoId: Long? = null,
    val ruta: RutaGuia? = null,
    val simulacion: Boolean = false,
    val velocidadMetrosSegundo: Double? = null,
    val esp32: ConexionEsp32Estado = ConexionEsp32Estado(),
    val fuentes: FuentesElegidas? = null,
    val rumboTelefonoDisponible: Boolean = true,
    val gpsTelefonoDisponible: Boolean = true,
    val guia: EstadoGuia? = null,
    /** Hora (System.currentTimeMillis) del último envío correcto a posiciones_usuario. */
    val ultimoEnvioPosicionMs: Long? = null,
    val errorEnvioPosicion: String? = null
)

/** Punto de acceso para que la UI inicie, ajuste y detenga la guía y observe su estado. */
object SesionGuia {

    private val estadoInterno = MutableStateFlow(EstadoSesionGuia())
    val estado: StateFlow<EstadoSesionGuia> = estadoInterno.asStateFlow()

    internal fun actualizar(transformar: (EstadoSesionGuia) -> EstadoSesionGuia) {
        estadoInterno.update(transformar)
    }

    internal fun reiniciar() {
        estadoInterno.value = EstadoSesionGuia()
    }

    fun iniciar(contexto: Context, seguimientoId: Long?) {
        ContextCompat.startForegroundService(
            contexto,
            Intent(contexto, ServicioGuia::class.java)
                .setAction(ServicioGuia.ACTION_INICIAR)
                .putExtra(ServicioGuia.EXTRA_SEGUIMIENTO_ID, seguimientoId ?: -1L)
        )
    }

    /** Activa o desactiva la simulación sin reiniciar la ruta. */
    fun cambiarSimulacion(contexto: Context, activa: Boolean) {
        enviarAccion(contexto, ServicioGuia.ACTION_SIMULACION) {
            putExtra(ServicioGuia.EXTRA_SIMULACION, activa)
        }
    }

    fun cambiarVelocidad(contexto: Context, metrosSegundo: Double) {
        enviarAccion(contexto, ServicioGuia.ACTION_VELOCIDAD) {
            putExtra(ServicioGuia.EXTRA_VELOCIDAD, metrosSegundo)
        }
    }

    /** Envía la última posición y detiene la guía, el rastreo y la conexión con el ESP32. */
    fun detener(contexto: Context) {
        if (!estadoInterno.value.activa && !estadoInterno.value.cargando) return
        enviarAccion(contexto, ServicioGuia.ACTION_DETENER)
    }

    private fun enviarAccion(contexto: Context, accion: String, extras: Intent.() -> Unit = {}) {
        if (!estadoInterno.value.activa && !estadoInterno.value.cargando) return
        // El servicio ya está en primer plano: basta con startService.
        contexto.startService(Intent(contexto, ServicioGuia::class.java).setAction(accion).apply(extras))
    }
}
