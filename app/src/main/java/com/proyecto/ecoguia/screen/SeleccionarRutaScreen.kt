package com.proyecto.ecoguia.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.proyecto.ecoguia.components.AsistenteVozCard
import com.proyecto.ecoguia.data.repository.RutaDisponible
import com.proyecto.ecoguia.guia.Coordenada
import com.proyecto.ecoguia.guia.EstimadorTiempo
import com.proyecto.ecoguia.guia.ResultadoInicio
import com.proyecto.ecoguia.guia.ValidadorInicio
import com.proyecto.ecoguia.guia.PreferenciasGuia
import com.proyecto.ecoguia.data.repository.RutasRepository
import com.proyecto.ecoguia.data.repository.SeguimientoRepository
import com.proyecto.ecoguia.navigation.BottomNavItem
import com.proyecto.ecoguia.navigation.EcoGuiaNavigationBar
import com.proyecto.ecoguia.sensores.obtenerUbicacionInicio
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.text.NumberFormat
import java.util.Locale

private val localeEspanol = Locale.forLanguageTag("es-SV")

private val fondoRuta = Color(0xFFFAF9F7)
private val tintaRuta = Color(0xFF19171F)

@Composable
fun SeleccionarRutaScreen(
    onVolver: () -> Unit,
    onSeleccionarRuta: (rutaId: Long, seguimientoId: Long, nombreRuta: String) -> Unit,
    onAjustesClick: () -> Unit = {},
    onAsistenteVozClick: () -> Unit = {},
    onMensajeVoz: (String) -> Unit = {}
) {
    val rutasRepository = remember { RutasRepository() }
    val seguimientoRepository = remember { SeguimientoRepository() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var rutas by remember { mutableStateOf<List<RutaDisponible>>(emptyList()) }
    var rutaSeleccionadaId by remember { mutableStateOf<Long?>(null) }
    var rutaConfirmandoId by remember { mutableStateOf<Long?>(null) }
    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var mostrarSeleccionRequerida by remember { mutableStateOf(false) }
    var verificandoInicio by remember { mutableStateOf(false) }
    var avisoFueraDeInicio by remember { mutableStateOf<String?>(null) }
    val rutaSeleccionada = rutas.firstOrNull { it.id == rutaSeleccionadaId }
    val contexto = LocalContext.current

    // Solo se guarda el seguimiento si la persona está en el nodo de inicio, según el GPS del
    // ESP32 (con satélites) o, si no, el del teléfono.
    fun confirmarEnNodoDeInicio(ruta: RutaDisponible) {
        scope.launch {
            rutaConfirmandoId = ruta.id
            verificandoInicio = true
            error = null
            try {
                val ubicacion = obtenerUbicacionInicio(contexto)
                verificandoInicio = false
                val inicio = ruta.nodos.firstOrNull()
                val resultado = ValidadorInicio.validar(
                    posicion = ubicacion?.coordenada,
                    nodoInicio = inicio?.let { Coordenada(it.latitud, it.longitud) }
                )
                when (resultado) {
                    is ResultadoInicio.EnInicio -> {
                        val seguimientoId = seguimientoRepository.iniciarRuta(ruta.id)
                        onSeleccionarRuta(ruta.id, seguimientoId, ruta.nombre)
                    }
                    is ResultadoInicio.FueraDeInicio -> {
                        error = ValidadorInicio.MENSAJE_FUERA_DE_INICIO
                        val detalle = "Estás a ${formatearDistancia(resultado.distanciaMetros)} " +
                                "del nodo de inicio \"${inicio?.nombre.orEmpty()}\"."
                        avisoFueraDeInicio = detalle
                        onMensajeVoz("${ValidadorInicio.MENSAJE_FUERA_DE_INICIO}. $detalle")
                    }
                    ResultadoInicio.SinUbicacion -> {
                        error = ValidadorInicio.MENSAJE_SIN_UBICACION
                        onMensajeVoz(ValidadorInicio.MENSAJE_SIN_UBICACION)
                    }
                    ResultadoInicio.RutaSinNodos -> {
                        error = "No se puede confirmar: la ruta no tiene un orden válido de nodos."
                        onMensajeVoz(error.orEmpty())
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("EcoGuia", "Error al guardar seguimiento: ${e.message}", e)
                error = e.localizedMessage ?: "No se pudo iniciar la ruta."
                snackbarHostState.showSnackbar("No se pudo guardar el seguimiento")
            } finally {
                verificandoInicio = false
                rutaConfirmandoId = null
            }
        }
    }

    val lanzadorPermisoUbicacion = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val ruta = rutaSeleccionada
        if (!tieneUbicacionPrecisa(contexto)) {
            error = "Se necesita la ubicación precisa para comprobar que estás en el nodo de inicio."
        } else if (ruta != null) {
            confirmarEnNodoDeInicio(ruta)
        }
    }

    LaunchedEffect(rutasRepository) {
        try {
            rutas = rutasRepository.listarRutasDisponibles()
        } catch (e: Exception) {
            error = e.localizedMessage ?: "No se pudieron cargar las rutas."
        } finally {
            cargando = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(fondoRuta)
        ) {
        BarraSuperiorRuta(titulo = "Seleccionar ruta", onVolver = onVolver)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            AsistenteVozCard(onClick = onAsistenteVozClick)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Selecciona una ruta para ver su recorrido",
                modifier = Modifier
                    .background(Color(0xFFD8D8D8), RoundedCornerShape(7.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = tintaRuta
            )
            Spacer(Modifier.height(12.dp))

            MapaRutaComposable(
                ruta = rutaSeleccionada,
                descripcion = rutaSeleccionada?.let {
                    "Mapa con la ruta ${it.nombre}"
                } ?: "Mapa del campus de la Universidad de El Salvador",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(215.dp)
            )

            rutaSeleccionada?.let { ruta ->
                Text(
                    text = "Ruta seleccionada: ${ruta.nombre}",
                    modifier = Modifier.padding(top = 8.dp),
                    fontWeight = FontWeight.Bold,
                    color = tintaRuta
                )
                val velocidad = PreferenciasGuia.velocidad(LocalContext.current)
                Text(
                    // Distancia por aristas.distancia_m (o Haversine) a la velocidad de caminata configurada.
                    text = ruta.distanciaMetros?.let {
                        "Tiempo estimado de llegada: " +
                                EstimadorTiempo.formatear(EstimadorTiempo(velocidad).segundosPara(it))
                    } ?: "Tiempo estimado de llegada: No disponible",
                    modifier = Modifier.padding(top = 3.dp),
                    color = Color.DarkGray
                )
                if (ruta.nodos.size < 2) {
                    Text(
                        "No se pudo determinar el orden de los nodos de esta ruta; revisa sus aristas.",
                        modifier = Modifier.padding(top = 6.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            when {
                cargando -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                rutas.isEmpty() -> Text(
                    error ?: "No hay rutas guardadas disponibles.",
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = if (error == null) Color.DarkGray else MaterialTheme.colorScheme.error
                )
                else -> rutas.forEachIndexed { index, ruta ->
                    PuntoRutaRow(
                        nombre = ruta.nombre,
                        distancia = ruta.distanciaMetros?.let(::formatearDistancia)
                            ?: "Distancia no disponible",
                        color = listOf(
                            Color(0xFFE7B8D5),
                            Color(0xFF9BD0CE),
                            Color(0xFFCBEF72)
                        )[index % 3],
                        seleccionada = ruta.id == rutaSeleccionadaId,
                        onClick = {
                            // Seleccionar solo cambia la vista previa; no escribe seguimientos.
                            rutaSeleccionadaId = ruta.id
                            error = null
                        }
                    )
                }
            }

            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val ruta = rutaSeleccionada
                    if (ruta == null) {
                        mostrarSeleccionRequerida = true
                    } else if (ruta.nodos.size < 2) {
                        error = "No se puede confirmar: la ruta no tiene un orden válido de nodos."
                    } else if (permisosGuiaFaltantes(contexto).isNotEmpty()) {
                        // Ubicación para el teléfono y Bluetooth para leer el GPS del ESP32.
                        lanzadorPermisoUbicacion.launch(permisosGuia())
                    } else {
                        confirmarEnNodoDeInicio(ruta)
                    }
                },
                enabled = rutaConfirmandoId == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics {
                        contentDescription = if (verificandoInicio) {
                            "Verificando que estás en el nodo de inicio"
                        } else if (rutaConfirmandoId != null) {
                            "Confirmando ruta seleccionada"
                        } else {
                            "Confirmar ruta seleccionada"
                        }
                    }
            ) {
                Text(
                    when {
                        verificandoInicio -> "Verificando ubicación…"
                        rutaConfirmandoId != null -> "Iniciando…"
                        else -> "Confirmar ruta"
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        EcoGuiaNavigationBar(
            pantallaActual = BottomNavItem.INICIO,
            onInicioClick = onVolver,
            onAjustesClick = onAjustesClick
        )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
        )
    }

    avisoFueraDeInicio?.let { detalle ->
        AlertDialog(
            onDismissRequest = { avisoFueraDeInicio = null },
            title = { Text("No se puede iniciar el recorrido") },
            text = { Text("${ValidadorInicio.MENSAJE_FUERA_DE_INICIO}.\n\n$detalle") },
            confirmButton = {
                TextButton(
                    onClick = { avisoFueraDeInicio = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text("Entendido") }
            }
        )
    }

    if (mostrarSeleccionRequerida) {
        AlertDialog(
            onDismissRequest = { mostrarSeleccionRequerida = false },
            title = { Text("Selecciona una ruta") },
            text = { Text("Elige una ruta de la lista antes de confirmar.") },
            confirmButton = {
                TextButton(
                    onClick = { mostrarSeleccionRequerida = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text("Entendido") }
            }
        )
    }
}

@Composable
internal fun BarraSuperiorRuta(titulo: String, onVolver: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Color(0xFF19171F))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "‹",
            modifier = Modifier
                .width(48.dp)
                .heightIn(min = 48.dp)
                .clickable(onClick = onVolver)
                .semantics {
                    role = Role.Button
                    contentDescription = "Volver"
                },
            color = Color.White,
            fontSize = 32.sp
        )
        Spacer(Modifier.width(8.dp))
        Text(titulo, fontSize = 16.sp, color = Color.White)
    }
}

@Composable
private fun PuntoRutaRow(
    nombre: String,
    distancia: String,
    color: Color,
    seleccionada: Boolean,
    onClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = buildString {
                        append("$nombre, $distancia, botón")
                        if (seleccionada) append(", Seleccionada")
                    }
                }
                .padding(horizontal = 2.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⌖", modifier = Modifier.width(30.dp), color = Color(0xFF26323B), fontSize = 24.sp)
            Text(
                text = nombre,
                modifier = Modifier
                    .weight(1f)
                    .background(color, RoundedCornerShape(7.dp))
                    .padding(horizontal = 7.dp, vertical = 7.dp),
                fontSize = 14.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = distancia,
                modifier = Modifier
                    .background(Color(0xFF9BD8D6), RoundedCornerShape(7.dp))
                    .padding(horizontal = 5.dp, vertical = 5.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
        if (seleccionada) {
            Text(
                "Seleccionada",
                modifier = Modifier.padding(start = 34.dp, bottom = 5.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = tintaRuta
            )
        }
        HorizontalDivider(color = Color(0xFF282828), thickness = 0.7.dp)
    }
}

private fun formatearDistancia(metros: Double): String {
    val numero = NumberFormat.getNumberInstance(localeEspanol).apply {
        maximumFractionDigits = 0
    }.format(metros)
    return "$numero m"
}
