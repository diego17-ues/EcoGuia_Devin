
package com.proyecto.ecoguia.data.repository

import com.proyecto.ecoguia.data.remote.SupabaseNoVidenteClient
import android.location.Location

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add

// Información de cada nodo temporal
data class NodoParaGuardar(
    val nombre: String,
    val tipo: String,
    val descripcion: String,
    val latitud: Double,
    val longitud: Double
)

// Respuesta de Supabase al guardar la ruta
@Serializable
data class ResultadoRutaGuardada(
    val ruta_id: Long,
    val total_nodos: Int,
    val total_aristas: Int,
    val estado: String
)

@Serializable
data class RutaDisponible(
    val id: Long,
    val nombre: String,
    val descripcion: String? = null,
    val nodos: List<NodoRuta> = emptyList(),
    val distanciaMetros: Double? = null
)

@Serializable
data class NodoRuta(
    val id: Long,
    val nombre: String,
    val latitud: Double,
    val longitud: Double
)

@Serializable
private data class NodoRutaOrdenFila(
    val id: Long,
    val ruta_id: Long,
    val nombre: String,
    val latitud: Double,
    val longitud: Double
)

@Serializable
private data class AristaRutaFila(
    val ruta_id: Long,
    val origen: Long,
    val destino: Long
)

class RutasRepository {

    private val supabase =
        SupabaseNoVidenteClient.client

    private val usuarioRepository =
        NoVidenteRepository()

    suspend fun listarRutasDisponibles(): List<RutaDisponible> {
        usuarioRepository.validarSesionNoVidente()
        val rutas = supabase.postgrest["rutas"].select {
            order("nombre", Order.ASCENDING)
        }.decodeList<RutaDisponible>()
        return cargarGeometria(rutas)
    }

    suspend fun obtenerRutaDisponible(rutaId: Long): RutaDisponible? {
        usuarioRepository.validarSesionNoVidente()
        val ruta = supabase.postgrest["rutas"].select {
            filter { eq("id", rutaId) }
        }.decodeList<RutaDisponible>().firstOrNull() ?: return null
        return cargarGeometria(listOf(ruta)).firstOrNull()
    }

    private suspend fun cargarGeometria(rutas: List<RutaDisponible>): List<RutaDisponible> {
        if (rutas.isEmpty()) return emptyList()

        val rutaIds = rutas.map { it.id }
        val nodosPorRuta = supabase.postgrest["nodos"].select {
            filter { isIn("ruta_id", rutaIds) }
        }.decodeList<NodoRutaOrdenFila>().groupBy { it.ruta_id }
        val aristasPorRuta = supabase.postgrest["aristas"].select {
            filter { isIn("ruta_id", rutaIds) }
        }.decodeList<AristaRutaFila>().groupBy { it.ruta_id }

        return rutas.map { ruta ->
            val ordenados = ordenarNodos(
                nodosPorRuta[ruta.id].orEmpty(),
                aristasPorRuta[ruta.id].orEmpty()
            )
            ruta.copy(
                nodos = ordenados,
                distanciaMetros = ordenados.takeIf { it.size >= 2 }
                    ?.let(::distanciaTotalMetros)
            )
        }
    }

    private fun ordenarNodos(
        filas: List<NodoRutaOrdenFila>,
        aristas: List<AristaRutaFila>
    ): List<NodoRuta> {
        if (filas.size < 2 || aristas.isEmpty()) return emptyList()

        val nodos = filas.associateBy { it.id }
        val salientes = aristas
            .filter { it.origen in nodos && it.destino in nodos }
            .groupBy { it.origen }
        val entrantes = aristas
            .filter { it.origen in nodos && it.destino in nodos }
            .groupBy { it.destino }

        // TODO: el esquema no define el orden esperado para rutas ramificadas o desconectadas.
        if (salientes.values.any { it.size != 1 } || entrantes.values.any { it.size > 1 }) {
            return emptyList()
        }
        val inicio = filas.singleOrNull { entrantes[it.id].isNullOrEmpty() }
            ?: return emptyList()
        val recorrido = mutableListOf<NodoRutaOrdenFila>()
        val visitados = mutableSetOf<Long>()
        var actual: NodoRutaOrdenFila? = inicio
        while (actual != null && visitados.add(actual.id)) {
            recorrido += actual
            val siguienteId = salientes[actual.id]?.singleOrNull()?.destino
            actual = siguienteId?.let(nodos::get)
        }
        if (recorrido.size != filas.size || recorrido.size != aristas.size + 1) {
            return emptyList()
        }
        return recorrido.map {
            NodoRuta(it.id, it.nombre, it.latitud, it.longitud)
        }
    }

    private fun distanciaTotalMetros(nodos: List<NodoRuta>): Double =
        nodos.zipWithNext().sumOf { (desde, hasta) ->
            val resultado = FloatArray(1)
            Location.distanceBetween(
                desde.latitud,
                desde.longitud,
                hasta.latitud,
                hasta.longitud,
                resultado
            )
            resultado[0].toDouble()
        }

    // Guardar todos los nodos en una sola operación
    suspend fun finalizarRuta(
        nombreRuta: String,
        descripcionRuta: String,
        nodos: List<NodoParaGuardar>
    ): ResultadoRutaGuardada {

        require(nombreRuta.trim().length in 2..150) {
            "El nombre de la ruta debe tener entre 2 y 150 caracteres."
        }

        require(descripcionRuta.length <= 1000) {
            "La descripción de la ruta es demasiado larga."
        }

        require(nodos.size in 2..100) {
            "Debes agregar entre 2 y 100 puntos."
        }

        nodos.forEach { nodo ->

            require(nodo.nombre.trim().length in 2..150) {
                "Hay un punto con nombre inválido."
            }

            require(
                nodo.latitud.isFinite() &&
                        nodo.longitud.isFinite() &&
                        nodo.latitud in -90.0..90.0 &&
                        nodo.longitud in -180.0..180.0
            ) {
                "Hay un punto con coordenadas inválidas."
            }
        }

        // Utilizar la sesión autenticada de la persona no vidente
        usuarioRepository.validarSesionNoVidente()

        // Construir el listado de nodos
        val listaNodos = buildJsonArray {

            nodos.forEach { nodo ->

                add(
                    buildJsonObject {

                        put("nombre", nodo.nombre.trim())
                        put("tipo", nodo.tipo.trim())
                        put(
                            "descripcion",
                            nodo.descripcion.trim()
                        )

                        put("latitud", nodo.latitud)
                        put("longitud", nodo.longitud)
                    }
                )
            }
        }

        // Construir los parámetros de la función SQL
        val parametros = buildJsonObject {

            put(
                "p_nombre_ruta",
                nombreRuta.trim()
            )

            put(
                "p_descripcion_ruta",
                descripcionRuta.trim()
            )

            put("p_nodos", listaNodos)
        }

        // Ejecutar una sola función para guardar la ruta completa
        return supabase.postgrest.rpc(
            function = "ecoguia_finalizar_ruta",
            parameters = parametros
        ).decodeAs<ResultadoRutaGuardada>()
    }
}
