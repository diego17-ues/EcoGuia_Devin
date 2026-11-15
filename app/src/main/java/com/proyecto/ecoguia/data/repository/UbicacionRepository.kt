
package com.proyecto.ecoguia.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock

import androidx.core.content.ContextCompat

import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

data class CoordenadaCapturada(
    val latitud: Double,
    val longitud: Double,
    val precisionMetros: Float
)

class UbicacionRepository(
    private val contexto: Context
) {

    private val ubicacion =
        LocationServices.getFusedLocationProviderClient(
            contexto
        )

    private val precisionDeseada = 5f
    private val precisionMaxima = 25f
    private val tiempoMaximo = 20000L

    // Comprobar que se autorizó la ubicación precisa
    fun tienePermisoPreciso(): Boolean {

        return ContextCompat.checkSelfPermission(
            contexto,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Comprobar que la ubicación del teléfono está activada
    private fun ubicacionActivada(): Boolean {

        val administrador =
            contexto.getSystemService(
                Context.LOCATION_SERVICE
            ) as LocationManager

        return administrador.isProviderEnabled(
            LocationManager.GPS_PROVIDER
        ) || administrador.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    // Verificar que la medición sea válida y reciente
    private fun medicionValida(
        posicion: Location
    ): Boolean {

        if (!posicion.hasAccuracy()) {
            return false
        }

        if (
            !posicion.accuracy.isFinite() ||
            posicion.accuracy < 0f
        ) {
            return false
        }

        if (
            posicion.latitude !in -90.0..90.0 ||
            posicion.longitude !in -180.0..180.0
        ) {
            return false
        }

        val antiguedadNanos =
            SystemClock.elapsedRealtimeNanos() -
                    posicion.elapsedRealtimeNanos

        // Ignorar ubicaciones demasiado antiguas
        if (
            antiguedadNanos < 0L ||
            antiguedadNanos > 5000000000L
        ) {
            return false
        }

        return true
    }

    // Obtener varias mediciones de ubicación
    @Suppress("MissingPermission")
    private fun escucharUbicaciones() = callbackFlow<Location> {

        val solicitud = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L
        )
            .setMinUpdateIntervalMillis(1000L)
            .setWaitForAccurateLocation(true)
            .build()

        val callback = object : LocationCallback() {

            override fun onLocationResult(
                resultado: LocationResult
            ) {

                resultado.locations.forEach { posicion ->
                    trySend(posicion)
                }
            }
        }

        // Solicitar actualizaciones al teléfono
        val registro = try {

            ubicacion.requestLocationUpdates(
                solicitud,
                callback,
                android.os.Looper.getMainLooper()
            )

        } catch (e: Exception) {

            close(e)
            null
        }

        registro?.addOnFailureListener { error ->
            close(error)
        }

        // Detener las actualizaciones al terminar
        awaitClose {
            if (registro != null) {
                ubicacion.removeLocationUpdates(callback)
            }
        }
    }

    // Capturar la mejor coordenada disponible
    suspend fun capturarCoordenada(): CoordenadaCapturada {

        if (!tienePermisoPreciso()) {
            throw SecurityException(
                "Debes permitir la ubicación precisa."
            )
        }

        if (!ubicacionActivada()) {
            throw IllegalStateException(
                "Activa la ubicación de tu teléfono."
            )
        }

        var mejorPosicion: Location? = null

        // Buscar durante un máximo de 20 segundos
        withTimeoutOrNull(tiempoMaximo) {

            escucharUbicaciones().first { posicion ->

                if (!medicionValida(posicion)) {

                    false

                } else {

                    // Guardar la medición más precisa
                    val anterior = mejorPosicion

                    if (
                        anterior == null ||
                        posicion.accuracy < anterior.accuracy
                    ) {
                        mejorPosicion = posicion
                    }

                    // Finalizar si alcanza 5 metros o menos
                    posicion.accuracy <= precisionDeseada
                }
            }
        }

        val resultado = mejorPosicion
            ?: throw IllegalStateException(
                "No se pudo obtener una ubicación reciente. " +
                        "Comprueba la señal e inténtalo nuevamente."
            )

        // Evitar aceptar ubicaciones demasiado imprecisas
        if (resultado.accuracy > precisionMaxima) {

            throw IllegalStateException(
                "La mejor precisión obtenida fue de " +
                        "${resultado.accuracy.toInt()} metros. " +
                        "La señal todavía es demasiado imprecisa. " +
                        "Inténtalo en un lugar más abierto."
            )
        }

        return CoordenadaCapturada(
            latitud = resultado.latitude,
            longitud = resultado.longitude,
            precisionMetros = resultado.accuracy
        )
    }
}
