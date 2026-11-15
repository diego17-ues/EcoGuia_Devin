package com.proyecto.ecoguia.screen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.proyecto.ecoguia.service.EstadoServicioSeguimiento
import com.proyecto.ecoguia.service.ServicioSeguimiento

@Composable
fun CompartirUbicacionCard() {
    val context = LocalContext.current
    val compartiendo by EstadoServicioSeguimiento.activo.collectAsState()
    var mostrarConsentimiento by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }

    fun iniciarServicio() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, ServicioSeguimiento::class.java)
                .setAction(ServicioSeguimiento.ACTION_INICIAR)
        )
    }

    val permisoUbicacion = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permisos ->
        if (permisos[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            iniciarServicio()
        } else {
            mensaje = "EcoGuía necesita ubicación precisa para iniciar el rastreo. " +
                    "Puedes activarla en los permisos de la aplicación."
        }
    }

    if (mostrarConsentimiento) {
        AlertDialog(
            onDismissRequest = { mostrarConsentimiento = false },
            title = { Text("Compartir ubicación") },
            text = {
                Text(
                    "EcoGuía enviará tu ubicación y precisión a tu encargado " +
                            "vinculado cada 5 segundos. El servicio seguirá activo " +
                            "con la pantalla apagada y puedes detenerlo cuando quieras. " +
                            "Android mostrará una notificación neutra mientras el servicio esté activo."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        mostrarConsentimiento = false
                        val tienePermiso = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        if (tienePermiso) {
                            iniciarServicio()
                        } else {
                            permisoUbicacion.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                )
                            )
                        }
                    }
                ) { Text("Aceptar y activar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarConsentimiento = false }) {
                    Text("Ahora no")
                }
            }
        )
    }

    if (mensaje != null) {
        AlertDialog(
            onDismissRequest = { mensaje = null },
            title = { Text("No se pudo activar") },
            text = { Text(mensaje.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { mensaje = null }) { Text("Aceptar") }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (compartiendo) "Compartiendo ubicación" else "Compartir ubicación",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (compartiendo) {
                        "Tu encargado puede ver tu posición actual."
                    } else {
                        "Solo se comparte mientras el servicio está activo."
                    },
                    modifier = Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = compartiendo,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics {
                        contentDescription = if (compartiendo) {
                            "Detener compartir ubicación"
                        } else {
                            "Activar compartir ubicación"
                        }
                    },
                onCheckedChange = { activar ->
                    if (activar) {
                        mostrarConsentimiento = true
                    } else {
                        context.startService(
                            Intent(context, ServicioSeguimiento::class.java)
                                .setAction(ServicioSeguimiento.ACTION_DETENER)
                        )
                    }
                }
            )
        }
    }
}
