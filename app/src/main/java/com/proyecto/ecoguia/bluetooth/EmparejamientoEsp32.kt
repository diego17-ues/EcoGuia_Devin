package com.proyecto.ecoguia.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.IntentCompat
import com.proyecto.ecoguia.guia.ConfigGuia
import java.util.regex.Pattern

/**
 * Emparejamiento del ESP32 desde la app con el servicio del sistema CompanionDeviceManager:
 * Android busca "EcoGuia_ESP32", muestra su propio diálogo y la app crea el vínculo Bluetooth.
 * Si el teléfono no ofrece ese servicio, se abre Ajustes > Bluetooth.
 */
object EmparejamientoEsp32 {

    private const val TAG = "EcoGuiaEmparejar"

    fun intentActivarBluetooth(): Intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)

    fun intentAjustesBluetooth(): Intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)

    /**
     * Pide al sistema que busque el dispositivo. [alEncontrar] recibe el diálogo del sistema que se
     * debe lanzar con StartIntentSenderForResult; [alFallar] indica que hay que usar Ajustes.
     */
    @Suppress("DEPRECATION")
    fun buscar(
        contexto: Context,
        alEncontrar: (IntentSender) -> Unit,
        alFallar: (String) -> Unit,
        nombre: String = ConfigGuia.NOMBRE_DISPOSITIVO_ESP32
    ) {
        val gestor = contexto.getSystemService(CompanionDeviceManager::class.java)
        if (gestor == null) {
            alFallar("Este teléfono no permite emparejar desde la app.")
            return
        }
        val solicitud = AssociationRequest.Builder()
            .addDeviceFilter(
                BluetoothDeviceFilter.Builder()
                    .setNamePattern(Pattern.compile(Pattern.quote(nombre)))
                    .build()
            )
            .setSingleDevice(true)
            .build()
        try {
            gestor.associate(solicitud, object : CompanionDeviceManager.Callback() {
                // En Android 13+ el sistema llama a onAssociationPending, que por defecto llama a este.
                override fun onDeviceFound(chooserLauncher: IntentSender) = alEncontrar(chooserLauncher)

                override fun onFailure(error: CharSequence?) =
                    alFallar(error?.toString() ?: "No se encontró \"$nombre\". ¿Está encendido y cerca?")
            }, null)
        } catch (e: RuntimeException) {
            Log.w(TAG, "No se pudo iniciar la búsqueda", e)
            alFallar("No se pudo buscar el dispositivo desde la app.")
        }
    }

    /** Crea el vínculo Bluetooth con el dispositivo elegido en el diálogo del sistema. */
    @SuppressLint("MissingPermission")
    fun emparejar(resultado: Intent?): Boolean {
        val dispositivo = dispositivoDe(resultado) ?: return false
        return try {
            dispositivo.bondState == BluetoothDevice.BOND_BONDED || dispositivo.createBond()
        } catch (e: SecurityException) {
            Log.w(TAG, "Falta el permiso de Bluetooth para emparejar", e)
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun dispositivoDe(resultado: Intent?): BluetoothDevice? {
        resultado ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            IntentCompat.getParcelableExtra(resultado, CompanionDeviceManager.EXTRA_ASSOCIATION, AssociationInfo::class.java)
                ?.associatedDevice?.bluetoothDevice?.let { return it }
        }
        return IntentCompat.getParcelableExtra(resultado, CompanionDeviceManager.EXTRA_DEVICE, BluetoothDevice::class.java)
    }
}
