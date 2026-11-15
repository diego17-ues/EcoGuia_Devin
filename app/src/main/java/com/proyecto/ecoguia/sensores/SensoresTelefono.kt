package com.proyecto.ecoguia.sensores

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.proyecto.ecoguia.guia.ConfigGuia

/**
 * GPS del teléfono (FusedLocationProviderClient). Funciona siempre durante la sesión: es la
 * fuente de la ubicación que ve el encargado y el respaldo cuando el GPS del ESP32 no tiene fix.
 */
class SensoresTelefono(
    private val contexto: Context,
    private val alRecibirUbicacion: (Location) -> Unit
) {
    private val ubicacion = LocationServices.getFusedLocationProviderClient(contexto)
    private var gpsActivo = false

    private val callbackUbicacion = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { posicion ->
                if (posicion.latitude in -90.0..90.0 && posicion.longitude in -180.0..180.0) {
                    alRecibirUbicacion(posicion)
                }
            }
        }
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

    fun detener() {
        if (gpsActivo) ubicacion.removeLocationUpdates(callbackUbicacion)
        gpsActivo = false
    }
}
