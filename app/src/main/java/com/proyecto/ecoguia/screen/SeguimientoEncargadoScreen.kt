package com.proyecto.ecoguia.screen

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mapbox.geojson.Point
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.Style
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.extension.compose.annotation.rememberIconImage
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.style.MapStyle
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.proyecto.ecoguia.R
import com.proyecto.ecoguia.data.repository.PersonaEnSeguimiento
import com.proyecto.ecoguia.data.repository.SeguimientoEncargadoSnapshot
import com.proyecto.ecoguia.data.repository.SeguimientoRepository
import com.proyecto.ecoguia.guia.EstimadorTiempo
import com.proyecto.ecoguia.navigation.BottomNavItem
import com.proyecto.ecoguia.navigation.EcoGuiaNavigationBar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val CAMPUS_UES_LATITUD = 13.718
private const val CAMPUS_UES_LONGITUD = -89.203
private const val EDAD_MAXIMA_SEGUIMIENTO_MS = 90_000L
// Centro provisional heredado de MapaUES; confirmar estas coordenadas del campus.

@OptIn(MapboxExperimental::class)
@Composable
fun SeguimientoEncargadoScreen(
    onVolver: () -> Unit,
    onAjustesClick: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { SeguimientoRepository() }
    val viewport = rememberMapViewportState {
        setCameraOptions {
            center(Point.fromLngLat(CAMPUS_UES_LONGITUD, CAMPUS_UES_LATITUD))
            zoom(17.0)
            pitch(60.0)
        }
    }

    var snapshot by remember { mutableStateOf(SeguimientoEncargadoSnapshot()) }
    var trazasPorPersona by remember { mutableStateOf<Map<String, List<Point>>>(emptyMap()) }
    var cargando by remember { mutableStateOf(true) }
    var errorRealtime by remember { mutableStateOf<String?>(null) }
    var personaSeleccionadaId by remember { mutableStateOf<String?>(null) }
    var mostrarUltimaUbicacion by remember { mutableStateOf(false) }
    var mostrarAvisoAyuda by remember { mutableStateOf(false) }
    var solicitudRecarga by remember { mutableStateOf(0) }
    var mostrarPermisoUbicacion by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
        )
    }
    var tienePermisoUbicacion by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var reloj by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val launcherPermisoUbicacion = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultados ->
        tienePermisoUbicacion =
            resultados[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    resultados[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(repository, solicitudRecarga) {
        try {
            repository.observarSeguimientoEncargado().collect {
                val trazasNuevas = trazasPorPersona.toMutableMap()
                it.personas.forEach personaLoop@{ persona ->
                    val posicion = persona.posicion ?: return@personaLoop
                    val punto = Point.fromLngLat(posicion.longitud, posicion.latitud)
                    val anteriores = trazasNuevas[persona.usuarioId].orEmpty()
                    val ultimo = anteriores.lastOrNull()
                    if (ultimo == null || ultimo.longitude() != punto.longitude() ||
                        ultimo.latitude() != punto.latitude()
                    ) {
                        trazasNuevas[persona.usuarioId] = (anteriores + punto).takeLast(MAX_PUNTOS_TRAZA)
                    }
                }
                trazasPorPersona = trazasNuevas
                snapshot = it
                cargando = false
                errorRealtime = null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cargando = false
            errorRealtime = e.localizedMessage
                ?: "No se pudo conectar con el seguimiento en tiempo real."
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000L)
            reloj = System.currentTimeMillis()
        }
    }

    if (mostrarPermisoUbicacion) {
        AlertDialog(
            onDismissRequest = { mostrarPermisoUbicacion = false },
            title = { Text("Mostrar mi ubicación") },
            text = {
                Text("El mapa puede mostrar tu posición para orientarte. " +
                        "EcoGuía solo necesita este permiso mientras usas la pantalla de rastreo.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        mostrarPermisoUbicacion = false
                        launcherPermisoUbicacion.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            )
                        )
                    }
                ) { Text("Continuar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarPermisoUbicacion = false }) {
                    Text("Ahora no")
                }
            }
        )
    }

    val personasVisibles = snapshot.personas.filter { persona ->
        val actualizacion = persona.actualizadoUtc?.let(::parsearFecha)
        persona.activo && persona.posicion != null && actualizacion != null &&
                reloj - actualizacion.toEpochMilli() <= EDAD_MAXIMA_SEGUIMIENTO_MS
    }
    val personaActual = personasVisibles.firstOrNull {
        it.usuarioId == personaSeleccionadaId
    } ?: personasVisibles.firstOrNull()

    LaunchedEffect(
        personaActual?.usuarioId,
        personaActual?.posicion?.latitud,
        personaActual?.posicion?.longitud
    ) {
        personaActual?.posicion?.let { posicion ->
            viewport.setCameraOptions {
                center(Point.fromLngLat(posicion.longitud, posicion.latitud))
                zoom(17.0)
                pitch(60.0)
            }
        }
    }

    if (mostrarUltimaUbicacion && personaActual != null) {
        AlertDialog(
            onDismissRequest = { mostrarUltimaUbicacion = false },
            title = { Text(personaActual.nombre) },
            text = {
                Text(
                    "Última ubicación recibida: " +
                            (personaActual.posicion?.fecha?.let(::formatearFecha) ?: "Sin datos")
                )
            },
            confirmButton = {
                TextButton(onClick = { mostrarUltimaUbicacion = false }) {
                    Text("Cerrar")
                }
            }
        )
    }

    if (mostrarAvisoAyuda) {
        AlertDialog(
            onDismissRequest = { mostrarAvisoAyuda = false },
            title = { Text("Enviar ayuda") },
            text = {
                Text("Esta cuenta todavía no tiene configurado un contacto o canal para enviar una solicitud de ayuda.")
            },
            confirmButton = {
                TextButton(onClick = { mostrarAvisoAyuda = false }) {
                    Text("Entendido")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAF9F7))
    ) {
        BarraSuperiorRuta(titulo = "Rastrear", onVolver = onVolver)

        if (personaActual != null) {
            Text(
                text = "Rastreando a “${personaActual.nombre}”",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 10.dp)
                    .background(Color(0xFF59B447), RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            MapboxMap(
                modifier = Modifier.fillMaxSize(),
                mapViewportState = viewport,
                style = { MapStyle(style = Style.STANDARD) }
            ) {
                if (tienePermisoUbicacion) {
                    MapEffect(Unit) { mapView ->
                        mapView.location.updateSettings {
                            locationPuck = createDefault2DPuck(withBearing = true)
                            enabled = true
                        }
                    }
                }

                val iconoPersona = rememberIconImage(
                    key = "marcador-persona-ecoguia",
                    painter = painterResource(R.drawable.ic_ubicacion)
                )

                personaActual?.ruta?.let { ruta ->
                    val puntosRuta = ruta.nodos.map { nodo ->
                        Point.fromLngLat(nodo.longitud, nodo.latitud)
                    }
                    if (puntosRuta.size >= 2) {
                        PolylineAnnotation(points = puntosRuta) {
                            lineColor = Color(0xFFE53935)
                            lineWidth = 5.0
                        }
                    }

                    ruta.nodos.forEach { nodo ->
                        val esUltimoNodo = nodo.id == ruta.ultimoNodoId
                        CircleAnnotation(
                            point = Point.fromLngLat(nodo.longitud, nodo.latitud)
                        ) {
                            circleRadius = if (esUltimoNodo) 9.0 else 5.0
                            circleColor = if (esUltimoNodo) {
                                Color(0xFF12C98A)
                            } else {
                                Color.White
                            }
                            circleStrokeColor = Color(0xFF26323B)
                            circleStrokeWidth = 2.0
                        }
                    }
                }

                personasVisibles.forEach { persona ->
                    val puntosTraza = trazasPorPersona[persona.usuarioId].orEmpty()
                    if (puntosTraza.size >= 2) {
                        PolylineAnnotation(points = puntosTraza) {
                            lineColor = Color(0xFF2367D1)
                            lineWidth = 4.0
                        }
                    }
                }

                personasVisibles.forEach { persona ->
                    val posicion = persona.posicion ?: return@forEach
                    PointAnnotation(
                        point = Point.fromLngLat(posicion.longitud, posicion.latitud)
                    ) {
                        iconImage = iconoPersona
                        textField = persona.nombre
                        interactionsState.onClicked {
                            personaSeleccionadaId = persona.usuarioId
                            mostrarUltimaUbicacion = true
                            true
                        }
                    }
                }
            }

            if (personasVisibles.isEmpty()) {
                val textoVacio = when {
                    cargando -> "Conectando con el seguimiento en tiempo real…"
                    errorRealtime != null -> "No se pudo cargar el seguimiento."
                    snapshot.personasAsignadas == 0 ->
                        "No hay personas no videntes vinculadas a tu cuenta."
                    snapshot.personas.any { it.activo && it.posicion == null } ->
                        "Esperando la primera ubicación compartida."
                    else -> "Nadie está compartiendo su ubicación en este momento."
                }
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!cargando && errorRealtime == null && snapshot.personasAsignadas == 0) {
                            Text(
                                "Seguimiento disponible",
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                        Text(textoVacio, textAlign = TextAlign.Center)
                        if (!cargando && errorRealtime == null && snapshot.personasAsignadas == 0) {
                            Text(
                                "Cuando una persona vinculada active compartir ubicación, " +
                                        "su posición aparecerá aquí.",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                        if (errorRealtime != null) {
                            Text(
                                errorRealtime.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                        if (!cargando) {
                            Button(
                                onClick = {
                                    cargando = true
                                    errorRealtime = null
                                    solicitudRecarga += 1
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Recargar búsqueda")
                            }
                        }
                    }
                }
            }

            if (!tienePermisoUbicacion) {
                Button(
                    onClick = {
                        launcherPermisoUbicacion.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            )
                        )
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(14.dp)
                ) {
                    Text("Mostrar mi ubicación")
                }
            }

            if (cargando && errorRealtime == null) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp)
                        .size(20.dp),
                    strokeWidth = 2.dp
                )
            }
        }

        if (personaActual != null) {
            PanelDetalleSeguimiento(
                persona = personaActual,
                reloj = reloj,
                puntosTraza = trazasPorPersona[personaActual.usuarioId].orEmpty(),
                onEnviarAyuda = { mostrarAvisoAyuda = true }
            )
        }

        EcoGuiaNavigationBar(
            pantallaActual = BottomNavItem.INICIO,
            onInicioClick = onVolver,
            onAjustesClick = onAjustesClick
        )
    }
}

@Composable
private fun PanelDetalleSeguimiento(
    persona: PersonaEnSeguimiento,
    reloj: Long,
    puntosTraza: List<Point>,
    onEnviarAyuda: () -> Unit
) {
    val ruta = persona.ruta
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Ruta seleccionada:",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📍", modifier = Modifier.padding(end = 6.dp))
            Text(
                text = ruta?.nombre ?: "Sin ruta activa",
                modifier = Modifier
                    .background(Color(0xFFE7B8D5), RoundedCornerShape(12.dp))
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                fontWeight = FontWeight.SemiBold
            )
        }
        ruta?.nodos
            ?.firstOrNull { it.id == ruta.ultimoNodoId }
            ?.let { nodo ->
                Text(
                    text = "Último nodo: ${nodo.nombre}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EtiquetaSeguimiento("Tiempo de llegada:")
            EtiquetaSeguimiento(estimarLlegada(persona, reloj))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFE0E0E0), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Compartiendo ubicación",
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Última ubicación: ${persona.posicion?.fecha?.let(::formatearFecha) ?: "Sin datos"}",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Recorrido observado en tiempo real desde que abriste el rastreo: " +
                        "${puntosTraza.size} ubicaciones. " +
                        "Actualización cada 5 segundos.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onEnviarAyuda,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD77B7E)
                )
            ) {
                Text("Enviar ayuda", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun estimarLlegada(persona: PersonaEnSeguimiento, ahoraMs: Long): String {
    val ruta = persona.ruta ?: return "No disponible"
    val posicion = persona.posicion ?: return "No disponible"
    if (ruta.nodos.isEmpty()) return "No disponible"

    val indiceUltimoNodo = ruta.ultimoNodoId?.let { id ->
        ruta.nodos.indexOfFirst { it.id == id }
    } ?: -1
    val indiceObjetivo = if (indiceUltimoNodo < 0) 0 else indiceUltimoNodo + 1
    if (indiceObjetivo >= ruta.nodos.size) return "Destino alcanzado"

    val objetivo = ruta.nodos[indiceObjetivo]
    val distanciaRestante = distanciaMetros(
        posicion.latitud,
        posicion.longitud,
        objetivo.latitud,
        objetivo.longitud
    ) + (indiceObjetivo until ruta.nodos.lastIndex).sumOf { indice ->
        val desde = ruta.nodos[indice]
        val hasta = ruta.nodos[indice + 1]
        distanciaMetros(desde.latitud, desde.longitud, hasta.latitud, hasta.longitud)
    }
    val estimador = EstimadorTiempo()
    val segundos = estimador.segundosPara(distanciaRestante)
    val horaLlegada = Instant.ofEpochMilli(ahoraMs)
        .plusSeconds(segundos)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("h:mm a", Locale.forLanguageTag("es-SV")))
    return "Aprox. $horaLlegada (${EstimadorTiempo.formatear(segundos)})"
}

private fun distanciaMetros(
    latitudA: Double,
    longitudA: Double,
    latitudB: Double,
    longitudB: Double
): Double {
    val resultado = FloatArray(1)
    Location.distanceBetween(latitudA, longitudA, latitudB, longitudB, resultado)
    return resultado[0].toDouble()
}

@Composable
private fun EtiquetaSeguimiento(texto: String) {
    Text(
        text = texto,
        modifier = Modifier
            .background(Color(0xFFD8D8D8), RoundedCornerShape(6.dp))
            .padding(horizontal = 9.dp, vertical = 7.dp),
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.bodyMedium
    )
}

private fun parsearFecha(fecha: String): Instant? =
    runCatching { Instant.parse(fecha) }.getOrNull()

private fun formatearFecha(fecha: String): String {
    val instante = parsearFecha(fecha) ?: return fecha
    val formato = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
        .withZone(ZoneId.systemDefault())
    return formato.format(instante)
}

private const val MAX_PUNTOS_TRAZA = 100
