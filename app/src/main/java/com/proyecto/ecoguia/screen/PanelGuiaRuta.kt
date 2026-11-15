package com.proyecto.ecoguia.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.proyecto.ecoguia.bluetooth.EstadoEsp32
import com.proyecto.ecoguia.guia.EstimadorTiempo
import com.proyecto.ecoguia.guia.FuentePosicion
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
                "Permisos para guiarte",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.semantics { heading() }
            )
            Text("• Ubicación precisa: saber dónde estás para indicarte el camino y compartir tu posición con tu encargado cada 10 segundos.")
            Text("• Dispositivos cercanos (Bluetooth): conectar con tu dispositivo EcoGuía para que vibre o suene al girar.")
            Text("• Notificaciones: mostrar que la guía sigue activa con la pantalla apagada.")
            Button(
                onClick = onConceder,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) { Text("Conceder permisos") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PanelGuiaRuta(sesion: EstadoSesionGuia) {
    val contexto = LocalContext.current
    val guia = sesion.guia

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (sesion.simulacion) {
            Text(
                "MODO SIMULACIÓN: posición y rumbo de prueba",
                color = Color(0xFF8A4B00),
                fontWeight = FontWeight.Bold
            )
        }

        // Indicación principal, en texto grande; TalkBack la anuncia cuando cambia.
        val textoIndicacion = when {
            sesion.cargando -> "Preparando la guía…"
            guia == null -> "Esperando datos de posición…"
            guia.finalizada -> "Has llegado al final de la ruta"
            guia.indicacion == null -> "Buscando posición y rumbo…"
            else -> guia.indicacion.texto
        }
        Text(
            textoIndicacion,
            fontSize = 32.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        guia?.siguientePunto?.let { punto ->
            Text(
                "Siguiente punto: ${punto.nombre}" +
                        (guia.distanciaSiguientePuntoMetros?.let { " a ${it.roundToInt()} m" } ?: ""),
                fontSize = 20.sp
            )
        }

        guia?.let {
            Tarjeta {
                Text("Tiempo estimado", fontWeight = FontWeight.Bold)
                Text(
                    "Restante: ${EstimadorTiempo.formatear(it.tiempoRestanteSegundos)} " +
                            "(${it.distanciaRestanteMetros.roundToInt()} m)",
                    fontSize = 22.sp
                )
                Text(
                    "Total de la ruta: ${EstimadorTiempo.formatear(it.tiempoTotalSegundos)} " +
                            "(${it.distanciaTotalMetros.roundToInt()} m)"
                )
            }
        }

        TarjetaObstaculo(sesion)

        // Mantener pulsada esta tarjeta activa o desactiva el modo simulación (botón oculto).
        Card(
            colors = CardDefaults.cardColors(containerColor = colorTarjeta),
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = { SesionGuia.cambiarSimulacion(contexto, !sesion.simulacion) },
                    onLongClickLabel = if (sesion.simulacion) "Desactivar simulación" else "Activar simulación"
                )
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Fuente activa", fontWeight = FontWeight.Bold)
                val fuentes = sesion.fuentes
                Text("Posición: ${fuentes?.fuentePosicion?.texto ?: FuentePosicion.NINGUNA.texto}")
                Text("Rumbo: ${fuentes?.fuenteRumbo?.texto ?: "Sin rumbo todavía"}" +
                        (fuentes?.rumbo?.let { " (${it.roundToInt()}°)" } ?: ""))
                Text(
                    sesion.esp32.mensaje,
                    color = if (sesion.esp32.estado == EstadoEsp32.CONECTADO) Color(0xFF1B5E20) else Color(0xFF8A4B00)
                )
                if (!sesion.gpsTelefonoDisponible) {
                    Text("Sin permiso de ubicación: el GPS del teléfono no está disponible.", color = MaterialTheme.colorScheme.error)
                }
                if (!sesion.rumboTelefonoDisponible) {
                    Text("Este teléfono no tiene brújula; el rumbo depende del dispositivo EcoGuía.", color = MaterialTheme.colorScheme.error)
                }
                Text(
                    sesion.ultimoEnvioPosicionMs?.let {
                        "Ubicación compartida con tu encargado: " +
                                DateFormat.getTimeInstance(DateFormat.MEDIUM, Locale.forLanguageTag("es-SV")).format(Date(it))
                    } ?: "Ubicación compartida con tu encargado: pendiente",
                    color = Color.DarkGray
                )
                sesion.errorEnvioPosicion?.let {
                    Text("No se pudo compartir la ubicación: $it", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        SelectorVelocidad(sesion)
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
                Text("Sin obstáculos registrados en lo que queda de la ruta.", fontSize = 18.sp)
                return@Column
            }
            Text("Siguiente obstáculo", fontWeight = FontWeight.Bold)
            Text(obstaculo.punto.nombre, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
            Text(
                buildString {
                    append(obstaculo.distanciaMetros?.let { "A ${it.roundToInt()} m" } ?: "Distancia desconocida")
                    obstaculo.indicacion?.let { append(", ${it.sentido}") }
                },
                fontSize = 24.sp
            )
            if (guia.obstaculosPendientes > 1) {
                Text("Quedan ${guia.obstaculosPendientes} obstáculos en la ruta.")
            }
        }
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
