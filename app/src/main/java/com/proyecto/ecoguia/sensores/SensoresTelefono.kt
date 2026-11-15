package com.proyecto.ecoguia.sensores

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.proyecto.ecoguia.guia.ConfigGuia
import com.proyecto.ecoguia.guia.Haversine

/**
 * GPS (FusedLocationProviderClient) y brújula del teléfono.
 * Funcionan siempre durante la sesión: son el respaldo de la guía y la fuente del rastreo.
 */
class SensoresTelefono(
    private val contexto: Context,
    private val alRecibirUbicacion: (Location) -> Unit,
    private val alRecibirRumbo: (Double) -> Unit
) {
    private val ubicacion = LocationServices.getFusedLocationProviderClient(contexto)
    private val sensores = contexto.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private var ultimaUbicacion: Location? = null
    private var gpsActivo = false
    private var brujulaActiva = false

    // Respaldo con acelerómetro + magnetómetro si no existe el sensor de rotación.
    private val gravedad = FloatArray(3)
    private val campoMagnetico = FloatArray(3)
    private var hayGravedad = false
    private var hayCampo = false

    private val callbackUbicacion = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { posicion ->
                if (posicion.latitude in -90.0..90.0 && posicion.longitude in -180.0..180.0) {
                    ultimaUbicacion = posicion
                    alRecibirUbicacion(posicion)
                }
            }
        }
    }

    private val listenerSensores = object : SensorEventListener {
        override fun onSensorChanged(evento: SensorEvent) {
            val matriz = FloatArray(9)
            when (evento.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR ->
                    SensorManager.getRotationMatrixFromVector(matriz, evento.values)
                Sensor.TYPE_ACCELEROMETER -> {
                    filtrar(evento.values, gravedad); hayGravedad = true
                    if (!hayCampo || !SensorManager.getRotationMatrix(matriz, null, gravedad, campoMagnetico)) return
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    filtrar(evento.values, campoMagnetico); hayCampo = true
                    if (!hayGravedad || !SensorManager.getRotationMatrix(matriz, null, gravedad, campoMagnetico)) return
                }
                else -> return
            }
            val orientacion = FloatArray(3)
            SensorManager.getOrientation(matriz, orientacion)
            val rumboMagnetico = Math.toDegrees(orientacion[0].toDouble())
            alRecibirRumbo(Haversine.normalizarRumbo(rumboMagnetico + declinacion()))
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun tienePermisoUbicacion(): Boolean =
        ContextCompat.checkSelfPermission(contexto, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    /** Devuelve false si falta el permiso de ubicación precisa. */
    @SuppressLint("MissingPermission")
    fun iniciarGps(): Boolean {
        if (gpsActivo) return true
        if (!tienePermisoUbicacion()) return false
        val solicitud = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            ConfigGuia.INTERVALO_GPS_TELEFONO_MS
        )
            .setMinUpdateIntervalMillis(ConfigGuia.INTERVALO_GPS_TELEFONO_MS)
            .build()
        ubicacion.requestLocationUpdates(solicitud, callbackUbicacion, Looper.getMainLooper())
        gpsActivo = true
        return true
    }

    /** Devuelve false si el teléfono no tiene sensores para calcular el rumbo. */
    fun iniciarBrujula(): Boolean {
        if (brujulaActiva) return true
        val rotacion = sensores.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensores.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        brujulaActiva = if (rotacion != null) {
            sensores.registerListener(listenerSensores, rotacion, SensorManager.SENSOR_DELAY_UI)
        } else {
            val acelerometro = sensores.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val magnetometro = sensores.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            acelerometro != null && magnetometro != null &&
                    sensores.registerListener(listenerSensores, acelerometro, SensorManager.SENSOR_DELAY_UI) &&
                    sensores.registerListener(listenerSensores, magnetometro, SensorManager.SENSOR_DELAY_UI)
        }
        return brujulaActiva
    }

    fun detener() {
        if (gpsActivo) ubicacion.removeLocationUpdates(callbackUbicacion)
        if (brujulaActiva) sensores.unregisterListener(listenerSensores)
        gpsActivo = false
        brujulaActiva = false
    }

    /** Corrige el norte magnético al norte geográfico, que es el que usa el rumbo de la ruta. */
    private fun declinacion(): Float {
        val posicion = ultimaUbicacion ?: return 0f
        return GeomagneticField(
            posicion.latitude.toFloat(),
            posicion.longitude.toFloat(),
            posicion.altitude.toFloat(),
            System.currentTimeMillis()
        ).declination
    }

    private fun filtrar(entrada: FloatArray, salida: FloatArray) {
        for (i in 0..2) salida[i] = salida[i] + FILTRO * (entrada[i] - salida[i])
    }

    private companion object {
        const val FILTRO = 0.15f
    }
}
