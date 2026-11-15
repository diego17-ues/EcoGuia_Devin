package com.proyecto.ecoguia.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.proyecto.ecoguia.guia.AvisoRuta
import com.proyecto.ecoguia.guia.ConfigGuia
import com.proyecto.ecoguia.guia.Indicacion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

enum class EstadoEsp32 {
    DETENIDO,
    SIN_PERMISO,
    BLUETOOTH_NO_DISPONIBLE,
    BLUETOOTH_APAGADO,
    NO_EMPAREJADO,
    CONECTANDO,
    CONECTADO,
    RECONECTANDO
}

data class ConexionEsp32Estado(
    val estado: EstadoEsp32 = EstadoEsp32.DETENIDO,
    val mensaje: String = "Dispositivo EcoGuía sin conectar."
) {
    val conectado: Boolean get() = estado == EstadoEsp32.CONECTADO
}

/**
 * Conexión Bluetooth Classic (RFCOMM/SPP) con el ESP32.
 *
 * - Busca el ESP32 por nombre entre los dispositivos ya emparejados (no escanea).
 * - Lee líneas en una corrutina de E/S, nunca en el hilo principal.
 * - Reintenta la conexión cada [ConfigGuia.INTERVALO_RECONEXION_MS] mientras esté iniciada.
 * - Envía la letra de estado L/R/A en cuanto cambia y la repite cada segundo como latido (o "H"
 *   si no hay estado, p. ej. sin MPU o con la ruta terminada). El firmware sale de MODO_APP tras
 *   3 s sin mensajes de la app, y repetir la letra mantiene el estado del buzzer aunque trate
 *   cualquier carácter como estado.
 * - Los avisos N (nodo alcanzado) y F (destino final) se envían una sola vez; después se pausan
 *   las letras de estado [ConfigGuia.PAUSA_TRAS_AVISO_MS] para que el buzzer termine el aviso.
 *   Si F no se pudo enviar, se reenvía al reconectar.
 */
class ConexionEsp32(
    private val contexto: Context,
    private val scope: CoroutineScope,
    private val alRecibir: (MensajeEsp32) -> Unit,
    private val nombreDispositivo: String = ConfigGuia.NOMBRE_DISPOSITIVO_ESP32
) {
    private val estadoInterno = MutableStateFlow(ConexionEsp32Estado())
    val estado: StateFlow<ConexionEsp32Estado> = estadoInterno.asStateFlow()

    private val uuidSpp: UUID = UUID.fromString(ConfigGuia.UUID_SPP)
    private val bloqueoEscritura = Any()

    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var salida: OutputStream? = null
    @Volatile private var ultimaIndicacionEnviada: Indicacion? = null
    @Volatile private var indicacionDeseada: Indicacion? = null
    @Volatile private var pausaHastaMs = 0L
    @Volatile private var destinoFinalPendiente = false
    private var trabajo: Job? = null

    fun iniciar() {
        if (trabajo?.isActive == true) return
        trabajo = scope.launch(Dispatchers.IO) { bucleConexion() }
    }

    fun detener() {
        trabajo?.cancel()
        trabajo = null
        cerrarSocket()
        ultimaIndicacionEnviada = null
        ultimaLetraEnviada = null
        pausaHastaMs = 0L
        destinoFinalPendiente = false
        estadoInterno.value = ConexionEsp32Estado()
    }

    /** Última letra escrita al ESP32 (L, R, A, N, F o H), para mostrarla en pantalla. */
    @Volatile var ultimaLetraEnviada: Char? = null
        private set

    /** Guarda la indicación y la envía de inmediato solo si es distinta a la última enviada. */
    fun enviarIndicacion(indicacion: Indicacion?) {
        indicacionDeseada = indicacion
        if (indicacion == null || indicacion == ultimaIndicacionEnviada || enPausa()) return
        if (escribirLinea(indicacion.letra.toString())) {
            ultimaIndicacionEnviada = indicacion
        }
    }

    /** Envía N o F una sola vez. Tras el aviso se vuelve a enviar la letra de estado vigente. */
    fun enviarAviso(aviso: AvisoRuta) {
        // La pausa se marca antes de escribir y bajo el mismo bloqueo que el latido,
        // para que el latido no pise el aviso con la letra anterior.
        synchronized(bloqueoEscritura) {
            pausaHastaMs = SystemClock.elapsedRealtime() + ConfigGuia.PAUSA_TRAS_AVISO_MS
            if (escribirLinea(aviso.letra.toString())) {
                // Se vuelve a contar desde que terminó la escritura, por si fue lenta.
                pausaHastaMs = SystemClock.elapsedRealtime() + ConfigGuia.PAUSA_TRAS_AVISO_MS
                if (aviso == AvisoRuta.DESTINO_FINAL) destinoFinalPendiente = false
                ultimaIndicacionEnviada = null
            } else {
                pausaHastaMs = 0L
                if (aviso == AvisoRuta.DESTINO_FINAL) destinoFinalPendiente = true
            }
        }
    }

    /** Al empezar otra ruta se descarta el F pendiente de la anterior. */
    fun descartarAvisosPendientes() {
        destinoFinalPendiente = false
    }

    private fun enPausa(): Boolean = SystemClock.elapsedRealtime() < pausaHastaMs

    /** Lo que se repite cada segundo: la indicación vigente o, si no hay, "H". */
    private fun mensajeLatido(): String = ParserMensajesEsp32.mensajeLatido(indicacionDeseada)

    private suspend fun bucleConexion() {
        var primerIntento = true
        while (scope.isActive && trabajo?.isActive != false) {
            val dispositivo = buscarDispositivo()
            if (dispositivo != null) {
                actualizar(
                    if (primerIntento) EstadoEsp32.CONECTANDO else EstadoEsp32.RECONECTANDO,
                    "Conectando con $nombreDispositivo…"
                )
                try {
                    conectarYLeer(dispositivo)
                } catch (e: IOException) {
                    Log.w(TAG, "Conexión con el ESP32 perdida: ${e.message}")
                } catch (e: SecurityException) {
                    actualizar(EstadoEsp32.SIN_PERMISO, "Falta el permiso de Bluetooth.")
                } finally {
                    cerrarSocket()
                }
                if (estadoInterno.value.estado != EstadoEsp32.SIN_PERMISO) {
                    actualizar(
                        EstadoEsp32.RECONECTANDO,
                        "Sin conexión con el dispositivo. Reintentando cada 3 s…"
                    )
                }
            }
            primerIntento = false
            delay(ConfigGuia.INTERVALO_RECONEXION_MS)
        }
    }

    @SuppressLint("MissingPermission")
    private fun buscarDispositivo(): BluetoothDevice? {
        if (!tienePermisoConexion()) {
            actualizar(
                EstadoEsp32.SIN_PERMISO,
                "Autoriza \"Dispositivos cercanos\" para conectar el dispositivo EcoGuía."
            )
            return null
        }
        val adaptador = (contexto.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adaptador == null) {
            actualizar(EstadoEsp32.BLUETOOTH_NO_DISPONIBLE, "Este teléfono no tiene Bluetooth.")
            return null
        }
        if (!adaptador.isEnabled) {
            actualizar(EstadoEsp32.BLUETOOTH_APAGADO, "Activa Bluetooth para usar el dispositivo EcoGuía.")
            return null
        }
        val dispositivo = adaptador.bondedDevices.orEmpty().firstOrNull { it.name == nombreDispositivo }
        if (dispositivo == null) {
            actualizar(
                EstadoEsp32.NO_EMPAREJADO,
                "\"$nombreDispositivo\" no está emparejado. Enciende el dispositivo y pulsa " +
                        "\"Emparejar dispositivo\"."
            )
        }
        return dispositivo
    }

    @SuppressLint("MissingPermission")
    private suspend fun conectarYLeer(dispositivo: BluetoothDevice) {
        val nuevoSocket = dispositivo.createRfcommSocketToServiceRecord(uuidSpp)
        socket = nuevoSocket
        nuevoSocket.connect()
        salida = nuevoSocket.outputStream
        // Tras reconectar se reenvía la indicación vigente.
        ultimaIndicacionEnviada = null
        actualizar(EstadoEsp32.CONECTADO, "Conectado a $nombreDispositivo.")
        pausaHastaMs = 0L
        if (destinoFinalPendiente) enviarAviso(AvisoRuta.DESTINO_FINAL)
        indicacionDeseada?.let { enviarIndicacion(it) }

        val latido = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val enviado = synchronized(bloqueoEscritura) { enPausa() || escribirLinea(mensajeLatido()) }
                if (!enviado) break
                delay(ConfigGuia.INTERVALO_LATIDO_MS)
            }
        }
        try {
            val lector = nuevoSocket.inputStream.bufferedReader(Charsets.US_ASCII)
            while (true) {
                val linea = lector.readLine() ?: throw IOException("El ESP32 cerró la conexión")
                when (val mensaje = ParserMensajesEsp32.parsear(linea)) {
                    is MensajeEsp32.Invalido ->
                        Log.w(TAG, "Línea ignorada del ESP32 (${mensaje.motivo}): \"${mensaje.linea}\"")
                    else -> alRecibir(mensaje)
                }
            }
        } finally {
            latido.cancel()
        }
    }

    private fun escribirLinea(texto: String): Boolean {
        val flujo = salida ?: return false
        return try {
            synchronized(bloqueoEscritura) {
                flujo.write("$texto\n".toByteArray(Charsets.US_ASCII))
                flujo.flush()
            }
            ultimaLetraEnviada = texto.firstOrNull()
            true
        } catch (e: IOException) {
            Log.w(TAG, "No se pudo enviar \"$texto\" al ESP32: ${e.message}")
            // Cerrar el socket desbloquea la lectura y dispara la reconexión.
            cerrarSocket()
            false
        }
    }

    private fun cerrarSocket() {
        salida = null
        try {
            socket?.close()
        } catch (_: IOException) {
        } finally {
            socket = null
        }
    }

    private fun tienePermisoConexion(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(
                    contexto,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED

    private fun actualizar(estado: EstadoEsp32, mensaje: String) {
        estadoInterno.value = ConexionEsp32Estado(estado, mensaje)
    }

    private companion object {
        const val TAG = "EcoGuiaEsp32"
    }
}
