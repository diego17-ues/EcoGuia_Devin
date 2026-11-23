package com.proyecto.ecoguia.screen

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.proyecto.ecoguia.bluetooth.EmparejamientoEsp32
import com.proyecto.ecoguia.bluetooth.EstadoEsp32
import com.proyecto.ecoguia.guia.EstadoValidacion
import com.proyecto.ecoguia.guia.EstimadorTiempo
import com.proyecto.ecoguia.guia.FuentePosicion
import com.proyecto.ecoguia.guia.FuenteRumbo
import com.proyecto.ecoguia.guia.PreferenciasGuia
import com.proyecto.ecoguia.service.EstadoSesionGuia
import com.proyecto.ecoguia.service.SesionGuia
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val colorTarjeta = Color(0xFFFFFFFF)
private val colorAviso = Color(0xFFFFF4E5)
private val colorObstaculo = Color(0xFFFFE9E6)

/** Permisos que necesita la sesión de guía según la versión de Android. */
internal fun permisosGuia(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_CONNECT)
        add(Manifest.permission.BLUETOOTH_SCAN)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

internal fun permisosGuiaFaltantes(contexto: Context): List<String> =
    permisosGuia().filter {
        ContextCompat.checkSelfPermission(contexto, it) != PackageManager.PERMISSION_GRANTED
    }

internal fun tieneUbicacionPrecisa(contexto: Context): Boolean =
    ContextCompat.checkSelfPermission(contexto, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

/** Explica por qué se piden los permisos antes de mostrar el diálogo del sistema. */
@Composable
internal fun TarjetaPermisosGuia(onConceder: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = colorAviso),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Permisos para la ruta",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.semantics { heading() }
            )
            Text("• Ubicación precisa: mostrar tu avance en la ruta y compartir tu posición con tu encargado cada 5 segundos.")
            Text("• Dispositivos cercanos (Bluetooth): conectar con tu dispositivo EcoGuía, que te guía con su buzzer.")
            Text("• Notificaciones: mostrar que la ruta sigue activa con la pantalla apagada.")
            Button(
                onClick = onConceder,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) { Text("Conceder permisos") }
        }
    }
}

/**
 * Panel de la ruta en curso. La app no guía a la persona: muestra la ruta y el progreso, el
 * estado del dispositivo EcoGuía (que guía con el buzzer) y el envío de la ubicación al encargado.
 */
@Composable
internal fun PanelGuiaRuta(sesion: EstadoSesionGuia) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (sesion.simulacion) {
            Text(
                "MODO SIMULACIÓN: posición y rumbo de prueba",
                color = Color(0xFF8A4B00),
                fontWeight = FontWeight.Bold
            )
        }
        TarjetaProgreso(sesion)
        TarjetaValidacion(sesion)
        TarjetaObstaculo(sesion)
        TarjetaDispositivo(sesion)
        TarjetaUbicacion(sesion)
        TarjetaSimulacion(sesion)
        SelectorVelocidad(sesion)
    }
}

@Composable
private fun TarjetaProgreso(sesion: EstadoSesionGuia) {
    val guia = sesion.guia
    Tarjeta {
        Text("Progreso de la ruta", fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        if (guia == null) {
            Text(if (sesion.cargando) "Preparando la ruta…" else "Esperando la posición…", fontSize = 18.sp)
            return@Tarjeta
        }
        val avance = if (guia.distanciaTotalMetros > 0)
            ((guia.distanciaTotalMetros - guia.distanciaRestanteMetros) / guia.distanciaTotalMetros).coerceIn(0.0, 1.0)
        else 0.0
        val porcentaje = if (guia.finalizada) 100 else (avance * 100).roundToInt()
        Text(
            if (guia.finalizada) "Ruta completada" else "$porcentaje % recorrido",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        LinearProgressIndicator(
            progress = { porcentaje / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Progreso de la ruta: $porcentaje por ciento" }
        )
        guia.siguientePunto?.takeUnless { guia.finalizada }?.let { punto ->
            Text(
                "Siguiente punto: ${punto.nombre}" +
                        (guia.distanciaSiguientePuntoMetros?.let { " a ${it.roundToInt()} m" } ?: ""),
                fontSize = 18.sp
            )
        }
        Text(
            "Restante: ${EstimadorTiempo.formatear(guia.tiempoRestanteSegundos)} " +
                    "(${guia.distanciaRestanteMetros.roundToInt()} m)",
            fontSize = 18.sp
        )
        Text(
            "Total de la ruta: ${EstimadorTiempo.formatear(guia.tiempoTotalSegundos)} " +
                    "(${guia.distanciaTotalMetros.roundToInt()} m)"
        )
        Text("Posición tomada de: ${sesion.fuentes?.fuentePosicion?.texto ?: FuentePosicion.NINGUNA.texto}", color = Color.DarkGray)
    }
}

private val colorEnRuta = Color(0xFF1B5E20)
private val colorDesvio = Color(0xFF8A4B00)

/**
 * Resultado de validar el rumbo del MPU contra la ruta. Solo informa: la corrección la da el
 * buzzer del dispositivo con las letras que le envía la app.
 */
@Composable
private fun TarjetaValidacion(sesion: EstadoSesionGuia) {
    val validacion = sesion.validacion ?: return
    val conectado = sesion.esp32.conectado
    Tarjeta {
        Text("Rumbo validado con el MPU", fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        val (texto, color) = when (validacion.estado) {
            EstadoValidacion.EN_RUTA -> "Vas por la ruta correcta (A)" to colorEnRuta
            EstadoValidacion.DESVIADO -> {
                val lado = if (validacion.indicacion?.letra == 'L') "L" else "R"
                val grados = validacion.errorRumboGrados?.let { " de ${kotlin.math.abs(it).roundToInt()}°" } ?: ""
                "Desvío$grados: el dispositivo corrige con $lado" to colorDesvio
            }
            EstadoValidacion.SIN_MPU ->
                if (conectado && !sesion.simulacion) "Esperando el rumbo del MPU…" to Color.DarkGray
                else "Sin garantía de rumbo: el dispositivo no está conectado. " +
                        "Solo se marca la posición con el GPS del teléfono." to colorDesvio
            EstadoValidacion.SIN_POSICION -> "Esperando la posición…" to Color.DarkGray
            EstadoValidacion.RUTA_TERMINADA -> "Destino alcanzado (F)" to colorEnRuta
        }
        Text(texto, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        validacion.desvioLateralMetros?.takeIf { kotlin.math.abs(it) >= 1.0 }?.let {
            Text(
                "A ${kotlin.math.abs(it).roundToInt()} m ${if (it > 0) "a la derecha" else "a la izquierda"} de la línea de la ruta",
                color = Color.DarkGray
            )
        }
        if (sesion.esp32BuscandoSatelites) {
            Text(
                "El dispositivo está buscando satélites; mientras tanto se usa el GPS del teléfono.",
                color = Color.DarkGray
            )
        }
    }
}

@Composable
private fun TarjetaObstaculo(sesion: EstadoSesionGuia) {
    val guia = sesion.guia ?: return
    val obstaculo = guia.obstaculo
    Card(
        colors = CardDefaults.cardColors(containerColor = if (obstaculo != null) colorObstaculo else colorTarjeta),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (obstaculo == null) {
                Text("Sin obstáculos registrados en lo que queda de la ruta.")
                return@Column
            }
            Text("Siguiente obstáculo: ${obstaculo.punto.nombre}", fontWeight = FontWeight.Bold)
            Text(obstaculo.distanciaMetros?.let { "A ${it.roundToInt()} m" } ?: "Distancia desconocida")
            if (guia.obstaculosPendientes > 1) {
                Text("Quedan ${guia.obstaculosPendientes} obstáculos en la ruta.")
            }
        }
    }
}

/** Encender, emparejar y ver el estado del dispositivo EcoGuía con pocos toques. */
@Composable
private fun TarjetaDispositivo(sesion: EstadoSesionGuia) {
    val contexto = LocalContext.current
    var aviso by remember { mutableStateOf<String?>(null) }
    val lanzadorActivar = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    val lanzadorEmparejar = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { resultado ->
        aviso = when {
            resultado.resultCode != Activity.RESULT_OK -> null
            EmparejamientoEsp32.emparejar(resultado.data) ->
                "Confirma el emparejamiento si Android lo pide. La app se conectará sola en unos segundos."
            else -> {
                abrir(contexto, EmparejamientoEsp32.intentAjustesBluetooth())
                "Empareja \"EcoGuia_ESP32\" en Ajustes > Bluetooth y vuelve a la app."
            }
        }
    }
    val esp32 = sesion.esp32

    Tarjeta {
        FilaInterruptor(
            titulo = "Dispositivo EcoGuía",
            subtitulo = "Te guía con el buzzer; la app le responde si te desvías.",
            activo = sesion.dispositivoActivo,
            alCambiar = { SesionGuia.cambiarDispositivo(contexto, it) }
        )
        if (!sesion.dispositivoActivo) {
            Text("Desactivado: solo se muestra el progreso y se comparte tu ubicación.", color = Color.DarkGray)
            return@Tarjeta
        }
        Text(
            esp32.mensaje,
            fontWeight = FontWeight.Bold,
            color = if (esp32.conectado) Color(0xFF1B5E20) else Color(0xFF8A4B00)
        )
        when (esp32.estado) {
            EstadoEsp32.BLUETOOTH_APAGADO -> BotonAccion("Activar Bluetooth") {
                try {
                    lanzadorActivar.launch(EmparejamientoEsp32.intentActivarBluetooth())
                } catch (_: RuntimeException) {
                    abrir(contexto, EmparejamientoEsp32.intentAjustesBluetooth())
                }
            }
            EstadoEsp32.NO_EMPAREJADO -> BotonAccion("Emparejar dispositivo") {
                aviso = "Buscando \"EcoGuia_ESP32\"…"
                EmparejamientoEsp32.buscar(
                    contexto,
                    alEncontrar = { lanzadorEmparejar.launch(IntentSenderRequest.Builder(it).build()) },
                    alFallar = { motivo ->
                        aviso = "$motivo Puedes emparejarlo en Ajustes > Bluetooth."
                        abrir(contexto, EmparejamientoEsp32.intentAjustesBluetooth())
                    }
                )
            }
            EstadoEsp32.SIN_PERMISO -> BotonAccion("Abrir permisos de la app") {
                abrir(
                    contexto,
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", contexto.packageName, null))
                )
            }
            else -> Unit
        }
        if (esp32.conectado) {
            val fuentes = sesion.fuentes
            Text(
                "Rumbo del MPU: " + (fuentes?.rumbo?.takeIf { fuentes.fuenteRumbo == FuenteRumbo.ESP32 }
                    ?.let { "${it.roundToInt()}°" } ?: "esperando datos…")
            )
            Text("Respuesta enviada al dispositivo: ${describirLetra(sesion.ultimaLetraEsp32)}")
        }
        aviso?.let { Text(it, color = Color.DarkGray) }
    }
}

private fun describirLetra(letra: Char?): String = when (letra) {
    'L' -> "L (izquierda, 3 pitidos)"
    'R' -> "R (derecha, 1 pitido)"
    'A' -> "A (vas bien, sigue adelante)"
    'N' -> "N (nodo alcanzado)"
    'F' -> "F (destino final)"
    'H' -> "H (latido: conexión abierta, sin indicación)"
    else -> "ninguna todavía"
}

@Composable
private fun TarjetaUbicacion(sesion: EstadoSesionGuia) {
    Tarjeta {
        Text("Ubicación para tu encargado", fontWeight = FontWeight.Bold)
        Text(
            if (sesion.simulacion) "En simulación se comparte la posición simulada."
            else "Se comparte el GPS del teléfono cada 5 segundos."
        )
        if (!sesion.gpsTelefonoDisponible) {
            Text("Sin permiso de ubicación: no se puede compartir tu posición.", color = MaterialTheme.colorScheme.error)
        }
        Text(
            when {
                sesion.compartidaPorOtroServicio -> "Se está enviando con \"Compartir ubicación\"."
                sesion.ultimoEnvioPosicionMs != null -> "Último envío: " +
                        DateFormat.getTimeInstance(DateFormat.MEDIUM, Locale.forLanguageTag("es-SV"))
                            .format(Date(sesion.ultimoEnvioPosicionMs))
                else -> "Último envío: pendiente"
            },
            color = Color.DarkGray
        )
        sesion.errorEnvioPosicion?.let {
            Text("No se pudo compartir la ubicación: $it", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun TarjetaSimulacion(sesion: EstadoSesionGuia) {
    val contexto = LocalContext.current
    Tarjeta {
        FilaInterruptor(
            titulo = "Modo simulación",
            subtitulo = "Recorre la ruta sin caminar, para probar sin GPS. Si el dispositivo está conectado, recibe L/R/A, N y F.",
            activo = sesion.simulacion,
            alCambiar = { SesionGuia.cambiarSimulacion(contexto, it) }
        )
    }
}

@Composable
private fun FilaInterruptor(titulo: String, subtitulo: String, activo: Boolean, alCambiar: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = activo, role = Role.Switch, onValueChange = alCambiar)
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(titulo, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(subtitulo, color = Color.DarkGray)
        }
        Switch(checked = activo, onCheckedChange = null)
    }
}

@Composable
private fun BotonAccion(texto: String, alPulsar: () -> Unit) {
    OutlinedButton(
        onClick = alPulsar,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
    ) { Text(texto) }
}

private fun abrir(contexto: Context, intent: Intent) {
    try {
        contexto.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    }
}

@Composable
private fun SelectorVelocidad(sesion: EstadoSesionGuia) {
    val contexto = LocalContext.current
    var velocidad by remember {
        mutableFloatStateOf((sesion.velocidadMetrosSegundo ?: PreferenciasGuia.velocidad(contexto)).toFloat())
    }
    Tarjeta {
        Text("Velocidad de caminata: ${"%.1f".format(Locale.forLanguageTag("es-SV"), velocidad)} m/s", fontWeight = FontWeight.Bold)
        Slider(
            value = velocidad,
            onValueChange = { velocidad = (it * 10).roundToInt() / 10f },
            onValueChangeFinished = {
                PreferenciasGuia.guardarVelocidad(contexto, velocidad.toDouble())
                SesionGuia.cambiarVelocidad(contexto, velocidad.toDouble())
            },
            valueRange = PreferenciasGuia.VELOCIDAD_MINIMA_MS.toFloat()..PreferenciasGuia.VELOCIDAD_MAXIMA_MS.toFloat(),
            modifier = Modifier.semantics { contentDescription = "Velocidad de caminata para calcular el tiempo" }
        )
    }
}

@Composable
private fun Tarjeta(contenido: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = colorTarjeta),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            contenido()
        }
    }
}
