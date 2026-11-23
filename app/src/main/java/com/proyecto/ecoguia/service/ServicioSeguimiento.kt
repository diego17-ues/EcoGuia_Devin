package com.proyecto.ecoguia.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.proyecto.ecoguia.R
import com.proyecto.ecoguia.data.repository.CoordenadaCapturada
import com.proyecto.ecoguia.data.repository.NoVidenteRepository
import com.proyecto.ecoguia.data.repository.SeguimientoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object EstadoServicioSeguimiento {
    private val estadoInterno = MutableStateFlow(false)
    val activo = estadoInterno.asStateFlow()

    internal fun actualizar(activo: Boolean) {
        estadoInterno.value = activo
    }
}

class ServicioSeguimiento : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ubicacion by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val seguimientoRepository by lazy { SeguimientoRepository() }
    private var usuarioId: String? = null
    private var actualizacionesSolicitadas = false
    private var detenerSolicitado = false
    private var trabajoInicio: Job? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val id = usuarioId ?: return
            result.locations.forEach { posicion ->
                if (!posicion.esValidaParaGuardar()) return@forEach
                val coordenada = CoordenadaCapturada(
                    latitud = posicion.latitude,
                    longitud = posicion.longitude,
                    precisionMetros = posicion.accuracy
                )
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        seguimientoRepository.guardarPosicion(id, coordenada)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        crearCanalNotificacion()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DETENER) {
            detenerSeguimiento()
            return START_NOT_STICKY
        }

        iniciarComoServicioEnPrimerPlano()
        EstadoServicioSeguimiento.actualizar(true)
        iniciarCaptura()
        return START_STICKY
    }

    private fun iniciarCaptura() {
        if (actualizacionesSolicitadas) return

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            detenerSeguimiento()
            return
        }

        detenerSolicitado = false
        trabajoInicio = scope.launch {
            try {
                val id = withContext(Dispatchers.IO) {
                    NoVidenteRepository().obtenerIdAutenticado()
                }
                if (detenerSolicitado) return@launch
                usuarioId = id
                pedirActualizaciones()
            } catch (_: Exception) {
                EstadoServicioSeguimiento.actualizar(false)
                stopSelf()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun pedirActualizaciones() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            detenerSeguimiento()
            return
        }

        val solicitud = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            INTERVALO_ACTUALIZACION_MS
        )
            .setMinUpdateIntervalMillis(INTERVALO_ACTUALIZACION_MS)
            .setWaitForAccurateLocation(true)
            .build()

        ubicacion.requestLocationUpdates(
            solicitud,
            callback,
            Looper.getMainLooper()
        ).addOnSuccessListener {
            actualizacionesSolicitadas = true
        }.addOnFailureListener {
            detenerSeguimiento()
        }
    }

    private fun detenerSeguimiento() {
        detenerSolicitado = true
        trabajoInicio?.cancel()
        if (usuarioId == null) {
            retirarServicio()
            return
        }
        retirarServicio()
    }

    private fun retirarServicio() {
        ubicacion.removeLocationUpdates(callback)
        actualizacionesSolicitadas = false
        EstadoServicioSeguimiento.actualizar(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun iniciarComoServicioEnPrimerPlano() {
        val notification = crearNotificacion()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CHANNEL_ID,
                "Actividad de EcoGuía",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificación del servicio activo de EcoGuía."
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(canal)
        }
    }

    private fun crearNotificacion(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ubicacion)
            .setContentTitle("EcoGuía")
            .setContentText("Servicio en ejecución.")
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        ubicacion.removeLocationUpdates(callback)
        EstadoServicioSeguimiento.actualizar(false)
        scope.cancel()
        super.onDestroy()
    }

    private fun Location.esValidaParaGuardar(): Boolean =
        hasAccuracy() && accuracy.isFinite() && accuracy >= 0f &&
                latitude in -90.0..90.0 && longitude in -180.0..180.0

    companion object {
        const val ACTION_INICIAR = "com.proyecto.ecoguia.action.INICIAR_SEGUIMIENTO"
        const val ACTION_DETENER = "com.proyecto.ecoguia.action.DETENER_SEGUIMIENTO"
        private const val INTERVALO_ACTUALIZACION_MS = 5_000L
        private const val CHANNEL_ID = "ecoguia_tracking_service"
        private const val NOTIFICATION_ID = 2301
    }
}
