package com.proyecto.ecoguia.data.repository

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager

import androidx.core.content.ContextCompat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import java.util.UUID

data class DispositivoBluetooth(
    val nombre: String,
    val direccion: String
)

class BluetoothRepository(
    private val contexto: Context
) {

    private val administrador =
        contexto.getSystemService(
            Context.BLUETOOTH_SERVICE
        ) as BluetoothManager

    private val adaptador: BluetoothAdapter?
        get() = administrador.adapter

    private var socket: BluetoothSocket? = null

    private val uuidSPP: UUID = UUID.fromString(
        "00001101-0000-1000-8000-00805F9B34FB"
    )

    fun tienePermiso(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(
                    contexto,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
    }

    fun bluetoothActivado(): Boolean {
        if (!tienePermiso()) return false
        return adaptador?.isEnabled == true
    }

    @SuppressLint("MissingPermission")
    fun obtenerEmparejados(): List<DispositivoBluetooth> {
        if (!tienePermiso()) {
            throw SecurityException(
                "Debes autorizar el permiso de Bluetooth."
            )
        }

        if (!bluetoothActivado()) {
            throw IllegalStateException(
                "Primero activa Bluetooth en tu teléfono."
            )
        }

        return adaptador
            ?.bondedDevices
            ?.map { dispositivo ->
                DispositivoBluetooth(
                    nombre = dispositivo.name
                        ?: "Dispositivo sin nombre",
                    direccion = dispositivo.address
                )
            }
            ?.sortedBy { it.nombre }
            ?: emptyList()
    }

    @SuppressLint("MissingPermission")
    suspend fun conectar(
        direccion: String
    ): Boolean = withContext(Dispatchers.IO) {

        if (!tienePermiso()) {
            throw SecurityException(
                "Falta el permiso Bluetooth."
            )
        }

        val bluetooth = adaptador
            ?: throw IllegalStateException(
                "Este teléfono no tiene Bluetooth."
            )

        if (!bluetooth.isEnabled) {
            throw IllegalStateException(
                "Bluetooth está desactivado."
            )
        }

        val dispositivo: BluetoothDevice =
            bluetooth.getRemoteDevice(direccion)

        if (dispositivo.bondState != BluetoothDevice.BOND_BONDED) {
            throw IllegalStateException(
                "Primero empareja el ESP32 desde los ajustes de Android."
            )
        }

        desconectar()

        val nuevoSocket =
            dispositivo.createRfcommSocketToServiceRecord(
                uuidSPP
            )

        try {
            nuevoSocket.connect()
            socket = nuevoSocket
            true
        } catch (e: Exception) {
            nuevoSocket.close()
            throw e
        }
    }

    fun estaConectado(): Boolean {
        return socket?.isConnected == true
    }

    fun desconectar() {
        try {
            socket?.close()
        } catch (_: Exception) {
        } finally {
            socket = null
        }
    }
}
