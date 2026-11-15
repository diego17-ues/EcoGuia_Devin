package com.proyecto.ecoguia.guia

import android.content.Context

/** Ajustes de la guía que la persona puede cambiar desde la app (se guardan en el teléfono). */
object PreferenciasGuia {

    private const val ARCHIVO = "ecoguia_guia"
    private const val CLAVE_VELOCIDAD = "velocidad_caminata_ms"
    private const val CLAVE_SIMULACION = "modo_simulacion"

    const val VELOCIDAD_MINIMA_MS = 0.3
    const val VELOCIDAD_MAXIMA_MS = 2.5

    fun velocidad(contexto: Context): Double =
        contexto.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)
            .getFloat(CLAVE_VELOCIDAD, ConfigGuia.VELOCIDAD_CAMINATA_POR_DEFECTO_MS.toFloat())
            .toDouble()
            .coerceIn(VELOCIDAD_MINIMA_MS, VELOCIDAD_MAXIMA_MS)

    fun guardarVelocidad(contexto: Context, metrosSegundo: Double) {
        contexto.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE).edit()
            .putFloat(CLAVE_VELOCIDAD, metrosSegundo.coerceIn(VELOCIDAD_MINIMA_MS, VELOCIDAD_MAXIMA_MS).toFloat())
            .apply()
    }

    fun simulacion(contexto: Context): Boolean =
        contexto.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)
            .getBoolean(CLAVE_SIMULACION, ConfigGuia.SIMULACION_ACTIVA_POR_DEFECTO)

    fun guardarSimulacion(contexto: Context, activa: Boolean) {
        contexto.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE).edit()
            .putBoolean(CLAVE_SIMULACION, activa)
            .apply()
    }
}
