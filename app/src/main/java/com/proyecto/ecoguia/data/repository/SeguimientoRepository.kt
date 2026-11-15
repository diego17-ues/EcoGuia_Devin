package com.proyecto.ecoguia.data.repository

import android.location.Location
import com.proyecto.ecoguia.data.remote.SeguimientoRealtimeClient
import com.proyecto.ecoguia.data.remote.SupabaseClient
import com.proyecto.ecoguia.data.remote.SupabaseNoVidenteClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

enum class SeguimientoEstado(val valor: String) {
    EN_CURSO("en_curso"),
    FINALIZADO("finalizado"),
    DETENIDO("detenido")
}

@Serializable
private data class PosicionParaGuardar(
    val usuario_id: String,
    val latitud: Double,
    val longitud: Double,
    val precision_metros: Float,
    val fecha: String
)

@Serializable
private data class SeguimientoParaIniciar(
    val ruta_id: Long,
    val usuario_id: String
)

@Serializable
private data class SeguimientoEstadoUpdate(
    val estado: String,
    val finalizado_utc: String
)

@Serializable
private data class PersonaAsignadaFila(
    val id: String,
    val nombre_completo: String
)

@Serializable
private data class SeguimientoFila(
    val id: Long,
    val ruta_id: Long,
    val usuario_id: String,
    val estado: String,
    val iniciado_utc: String,
    val ultimo_nodo_id: Long? = null,
    val finalizado_utc: String? = null
)

@Serializable
private data class PosicionSeguimientoFila(
    val usuario_id: String,
    val latitud: Double,
    val longitud: Double,
    val precision_metros: Float,
    val fecha: String
)

@Serializable
private data class RutaFila(
    val id: Long,
    val nombre: String
)

@Serializable
private data class NodoRutaFila(
    val id: Long,
    val ruta_id: Long,
    val nombre: String,
    val latitud: Double,
    val longitud: Double
)

@Serializable
private data class ActualizarUltimoNodo(
    val ultimo_nodo_id: Long
)

data class PersonaEnSeguimiento(
    val usuarioId: String,
    val nombre: String,
    val activo: Boolean,
    val actualizadoUtc: String?,
    val posicion: PosicionSeguimientoFilaPublica?,
    val ruta: RutaEnSeguimiento? = null
)

data class RutaEnSeguimiento(
    val id: Long,
    val nombre: String,
    val nodos: List<NodoEnRuta>,
    val ultimoNodoId: Long?
)

data class NodoEnRuta(
    val id: Long,
    val nombre: String,
    val latitud: Double,
    val longitud: Double
)

data class RutaActivaUsuario(
    val seguimientoId: Long,
    val rutaId: Long,
    val nombreRuta: String,
    val ultimoNodoNombre: String?,
    val ultimoNodoId: Long? = null,
    val nodos: List<NodoEnRuta> = emptyList(),
    val progresoPorcentaje: Int = 0,
    val siguienteNodoNombre: String? = null,
    val distanciaSiguienteNodoMetros: Double? = null
)

data class ResumenInicio(
    val mes: YearMonth,
    val metrosRecorridosMes: Double,
    val metrosPorDia: List<Double>,
    val ultimaRutaNombre: String?,
    val rutaActiva: RutaActivaUsuario?,
    val tieneRutasFinalizadas: Boolean
)

data class PosicionSeguimientoFilaPublica(
    val latitud: Double,
    val longitud: Double,
    val precisionMetros: Float,
    val fecha: String
)

data class SeguimientoEncargadoSnapshot(
    val personasAsignadas: Int = 0,
    val personas: List<PersonaEnSeguimiento> = emptyList()
)

class SeguimientoRepository {
    private val clienteNoVidente = SupabaseNoVidenteClient.client

    suspend fun iniciarRuta(rutaId: Long): Long {
        val usuarioId = NoVidenteRepository().obtenerIdAutenticado()
        val tablaSeguimientos = clienteNoVidente.postgrest["seguimientos"]

        buscarSeguimientoActivo(usuarioId)?.let { seguimientoAnterior ->
            actualizarEstadoSeguimiento(
                seguimientoAnterior.id,
                SeguimientoEstado.DETENIDO
            )
        }

        val seguimientoInsertado = tablaSeguimientos.insert(
            SeguimientoParaIniciar(
                ruta_id = rutaId,
                usuario_id = usuarioId
            )
        ) {
            select()
        }.decodeSingle<SeguimientoFila>()

        return seguimientoInsertado.id
    }

    suspend fun finalizarRuta(seguimientoId: Long) {
        actualizarEstadoSeguimiento(seguimientoId, SeguimientoEstado.FINALIZADO)
    }

    suspend fun detenerRuta(seguimientoId: Long) {
        actualizarEstadoSeguimiento(seguimientoId, SeguimientoEstado.DETENIDO)
    }

    suspend fun obtenerRutaActivaUsuario(): RutaActivaUsuario? {
        val usuarioId = NoVidenteRepository().obtenerIdAutenticado()
        val seguimiento = buscarSeguimientoActivo(usuarioId) ?: return null
        val ruta = RutasRepository().obtenerRutaDisponible(seguimiento.ruta_id) ?: return null
        val nodos = ruta.nodos.map { nodo ->
            NodoEnRuta(nodo.id, nodo.nombre, nodo.latitud, nodo.longitud)
        }
        val indiceActual = nodos.indexOfFirst { it.id == seguimiento.ultimo_nodo_id }
        val nodoActual = nodos.getOrNull(indiceActual)
        val siguiente = nodos.getOrNull(if (indiceActual < 0) 0 else indiceActual + 1)
        val progreso = if (nodos.size > 1 && indiceActual >= 0) {
            (indiceActual * 100.0 / (nodos.size - 1)).toInt().coerceIn(0, 100)
        } else {
            0
        }
        val posicion = clienteNoVidente.postgrest["posiciones_usuario"].select {
            filter { eq("usuario_id", usuarioId) }
            limit(1)
        }.decodeList<PosicionSeguimientoFila>().firstOrNull()
        val distanciaSiguiente = if (posicion != null && siguiente != null) {
            distanciaMetros(
                posicion.latitud,
                posicion.longitud,
                siguiente.latitud,
                siguiente.longitud
            )
        } else null

        return RutaActivaUsuario(
            seguimientoId = seguimiento.id,
            rutaId = ruta.id,
            nombreRuta = ruta.nombre,
            ultimoNodoNombre = nodoActual?.nombre,
            ultimoNodoId = seguimiento.ultimo_nodo_id,
            nodos = nodos,
            progresoPorcentaje = progreso,
            siguienteNodoNombre = siguiente?.nombre,
            distanciaSiguienteNodoMetros = distanciaSiguiente
        )
    }

    suspend fun obtenerResumenInicio(): ResumenInicio {
        val usuarioId = NoVidenteRepository().obtenerIdAutenticado()
        val mesActual = YearMonth.now()
        val inicioMes = mesActual.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val inicioMesSiguiente = mesActual.plusMonths(1).atDay(1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant()

        val finalizados = clienteNoVidente.postgrest["seguimientos"].select {
            filter {
                eq("usuario_id", usuarioId)
                eq("estado", SeguimientoEstado.FINALIZADO.valor)
            }
            order("finalizado_utc", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
        }.decodeList<SeguimientoFila>()
        val rutas = RutasRepository().listarRutasDisponibles().associateBy { it.id }
        val dias = MutableList(mesActual.lengthOfMonth()) { 0.0 }
        var metrosMes = 0.0

        finalizados.forEach { seguimiento ->
            val finalizado = seguimiento.finalizado_utc
                ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?: return@forEach
            if (finalizado >= inicioMes && finalizado < inicioMesSiguiente) {
                val metrosRuta = rutas[seguimiento.ruta_id]?.distanciaMetros ?: 0.0
                metrosMes += metrosRuta
                val dia = finalizado.atZone(ZoneId.systemDefault()).dayOfMonth
                dias[dia - 1] += metrosRuta
            }
        }

        val ultimaRutaNombre = finalizados.firstOrNull()?.let { rutas[it.ruta_id]?.nombre }
        return ResumenInicio(
            mes = mesActual,
            metrosRecorridosMes = metrosMes,
            metrosPorDia = dias,
            ultimaRutaNombre = ultimaRutaNombre,
            rutaActiva = obtenerRutaActivaUsuario(),
            tieneRutasFinalizadas = finalizados.isNotEmpty()
        )
    }

    private suspend fun buscarSeguimientoActivo(usuarioId: String): SeguimientoFila? =
        clienteNoVidente.postgrest["seguimientos"].select {
            filter {
                eq("usuario_id", usuarioId)
                eq("estado", SeguimientoEstado.EN_CURSO.valor)
            }
            order("iniciado_utc", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
            limit(1)
        }.decodeList<SeguimientoFila>().firstOrNull()

    private suspend fun actualizarEstadoSeguimiento(
        seguimientoId: Long,
        estado: SeguimientoEstado
    ) {
        clienteNoVidente.postgrest["seguimientos"].update(
            SeguimientoEstadoUpdate(
                estado = estado.valor,
                finalizado_utc = Instant.now().toString()
            )
        ) {
            filter {
                eq("id", seguimientoId)
            }
        }
    }

    suspend fun guardarPosicion(
        usuarioId: String,
        coordenada: CoordenadaCapturada
    ) {
        val fecha = Instant.now().toString()
        clienteNoVidente.postgrest["posiciones_usuario"].upsert(
            PosicionParaGuardar(
                usuario_id = usuarioId,
                latitud = coordenada.latitud,
                longitud = coordenada.longitud,
                precision_metros = coordenada.precisionMetros,
                fecha = fecha
            )
        ) {
            onConflict = "usuario_id"
        }

        actualizarUltimoNodo(usuarioId, coordenada)
    }

    private suspend fun actualizarUltimoNodo(
        usuarioId: String,
        coordenada: CoordenadaCapturada
    ) {
        val seguimiento = buscarSeguimientoActivo(usuarioId) ?: return
        val nodos = clienteNoVidente.postgrest["nodos"].select {
            filter { eq("ruta_id", seguimiento.ruta_id) }
            order("id", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
        }.decodeList<NodoRutaFila>()

        val nodoCercano = nodos.minByOrNull { nodo ->
            distanciaMetros(
                coordenada.latitud,
                coordenada.longitud,
                nodo.latitud,
                nodo.longitud
            )
        } ?: return
        val distancia = distanciaMetros(
            coordenada.latitud,
            coordenada.longitud,
            nodoCercano.latitud,
            nodoCercano.longitud
        )

        if (distancia > DISTANCIA_PROXIMIDAD_NODO_METROS ||
            nodoCercano.id == seguimiento.ultimo_nodo_id
        ) {
            return
        }

        clienteNoVidente.postgrest["seguimientos"].update(
            ActualizarUltimoNodo(ultimo_nodo_id = nodoCercano.id)
        ) {
            filter {
                eq("id", seguimiento.id)
                eq("estado", SeguimientoEstado.EN_CURSO.valor)
            }
        }
    }

    fun observarSeguimientoEncargado(): Flow<SeguimientoEncargadoSnapshot> = flow {
        val sesion = SupabaseClient.client.auth.currentSessionOrNull()
            ?: error("Inicia sesión como encargado para ver el seguimiento.")
        val cliente = SeguimientoRealtimeClient.client
        cliente.auth.importSession(sesion)
        val encargadoId = cliente.auth
            .retrieveUserForCurrentSession(updateSession = true)
            .id

        val canal = cliente.channel("seguimiento-$encargadoId")
        val cambiosPosiciones = canal.postgresChangeFlow<PostgresAction>("public") {
            table = "posiciones_usuario"
        }
        val cambiosEstado = canal.postgresChangeFlow<PostgresAction>("public") {
            table = "seguimientos"
        }

        coroutineScope {
            val refrescos = Channel<Unit>(Channel.CONFLATED)
            // Registra ambos cambios en el canal antes de unirlo a Realtime.
            val observadorPosiciones = launch(start = CoroutineStart.UNDISPATCHED) {
                cambiosPosiciones.collect { refrescos.trySend(Unit) }
            }
            val observadorEstado = launch(start = CoroutineStart.UNDISPATCHED) {
                cambiosEstado.collect { refrescos.trySend(Unit) }
            }

            try {
                canal.subscribe(blockUntilSubscribed = true)
                refrescos.trySend(Unit)
                for (ignored in refrescos) {
                    emit(cargarSnapshot(cliente, encargadoId))
                }
            } finally {
                observadorPosiciones.cancel()
                observadorEstado.cancel()
                refrescos.close()
                withContext(NonCancellable) {
                    runCatching { canal.unsubscribe() }
                }
            }
        }
    }

    private suspend fun cargarSnapshot(
        cliente: io.github.jan.supabase.SupabaseClient,
        encargadoId: String
    ): SeguimientoEncargadoSnapshot {
        val personas = cliente.postgrest["usuarios"].select {
            filter {
                eq("encargado_id", encargadoId)
                eq("rol", "no_vidente")
            }
        }.decodeList<PersonaAsignadaFila>()

        if (personas.isEmpty()) return SeguimientoEncargadoSnapshot()

        val ids = personas.map { it.id }
        val estados = cliente.postgrest["seguimientos"].select {
            filter {
                isIn("usuario_id", ids)
                eq("estado", SeguimientoEstado.EN_CURSO.valor)
            }
            order("iniciado_utc", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
        }.decodeList<SeguimientoFila>()
            .groupBy { it.usuario_id }
            .mapValues { (_, filas) -> filas.first() }

        val activos = personas.filter { estados.containsKey(it.id) }
        val posiciones = coroutineScope {
            activos.map { persona ->
                async(Dispatchers.IO) {
                    val posicion = cliente.postgrest["posiciones_usuario"].select {
                        filter { eq("usuario_id", persona.id) }
                        limit(1)
                    }.decodeList<PosicionSeguimientoFila>().firstOrNull()

                    persona.id to posicion
                }
            }.awaitAll().toMap()
        }
        val rutas = coroutineScope {
            activos.map { persona ->
                val seguimiento = estados[persona.id] ?: return@map null
                async(Dispatchers.IO) {
                    val ruta = cliente.postgrest["rutas"].select {
                        filter { eq("id", seguimiento.ruta_id) }
                    }.decodeList<RutaFila>().firstOrNull() ?: return@async persona.id to null

                    val nodos = cliente.postgrest["nodos"].select {
                        filter { eq("ruta_id", ruta.id) }
                        order("id", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                    }.decodeList<NodoRutaFila>()

                    persona.id to RutaEnSeguimiento(
                        id = ruta.id,
                        nombre = ruta.nombre,
                        nodos = nodos.map { nodo ->
                            NodoEnRuta(
                                id = nodo.id,
                                nombre = nodo.nombre,
                                latitud = nodo.latitud,
                                longitud = nodo.longitud
                            )
                        },
                        ultimoNodoId = seguimiento.ultimo_nodo_id
                    )
                }
            }.filterNotNull().awaitAll().toMap()
        }

        return SeguimientoEncargadoSnapshot(
            personasAsignadas = personas.size,
            personas = personas.map { persona ->
                val estado = estados[persona.id]
                val posicion = posiciones[persona.id]
                PersonaEnSeguimiento(
                    usuarioId = persona.id,
                    nombre = persona.nombre_completo,
                    activo = estado != null,
                    actualizadoUtc = posicion?.fecha ?: estado?.iniciado_utc,
                    posicion = posicion?.let {
                        PosicionSeguimientoFilaPublica(
                            latitud = it.latitud,
                            longitud = it.longitud,
                            precisionMetros = it.precision_metros,
                            fecha = it.fecha
                        )
                    },
                    ruta = rutas[persona.id]
                )
            }
        )
    }

    private fun distanciaMetros(
        latitudA: Double,
        longitudA: Double,
        latitudB: Double,
        longitudB: Double
    ): Double {
        val resultado = FloatArray(1)
        Location.distanceBetween(
            latitudA,
            longitudA,
            latitudB,
            longitudB,
            resultado
        )
        return resultado[0].toDouble()
    }

    private companion object {
        const val DISTANCIA_PROXIMIDAD_NODO_METROS = 15.0
    }
}
