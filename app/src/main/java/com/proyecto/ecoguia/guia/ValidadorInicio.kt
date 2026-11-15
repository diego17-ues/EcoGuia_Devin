package com.proyecto.ecoguia.guia

/** Resultado de comprobar si la persona está en el nodo de inicio antes de comenzar. */
sealed class ResultadoInicio {
    data class EnInicio(val distanciaMetros: Double) : ResultadoInicio()
    data class FueraDeInicio(val distanciaMetros: Double) : ResultadoInicio()
    object SinUbicacion : ResultadoInicio()
    object RutaSinNodos : ResultadoInicio()
}

/**
 * El recorrido solo puede empezar sobre el primer nodo de la ruta: EcoGuía no lleva a la persona
 * hasta el inicio (no es un navegador general), y el ESP32 solo guía dentro de la ruta.
 */
object ValidadorInicio {

    const val MENSAJE_FUERA_DE_INICIO =
        "No se puede iniciar el recorrido, ubicate en el nodo de inicio para comenzar"

    const val MENSAJE_SIN_UBICACION =
        "No se pudo obtener tu ubicación con el GPS del dispositivo EcoGuía ni con el del teléfono. " +
                "Activa la ubicación y vuelve a intentarlo en el nodo de inicio."

    fun validar(
        posicion: Coordenada?,
        nodoInicio: Coordenada?,
        radioMetros: Double = ConfigGuia.RADIO_NODO_INICIO_METROS
    ): ResultadoInicio {
        if (nodoInicio == null) return ResultadoInicio.RutaSinNodos
        if (posicion == null) return ResultadoInicio.SinUbicacion
        val distancia = Haversine.distanciaMetros(posicion, nodoInicio)
        return if (distancia <= radioMetros) {
            ResultadoInicio.EnInicio(distancia)
        } else {
            ResultadoInicio.FueraDeInicio(distancia)
        }
    }
}
