package com.proyecto.ecoguia.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.proyecto.ecoguia.data.repository.NodoRuta
import com.proyecto.ecoguia.data.repository.RutaActivaUsuario
import com.proyecto.ecoguia.data.repository.RutaDisponible
import com.proyecto.ecoguia.data.repository.SeguimientoRepository
import com.proyecto.ecoguia.service.SesionGuia
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private val localeEspanol = Locale.forLanguageTag("es-SV")

@Composable
fun RutaEnCursoScreen(
    onVolver: () -> Unit,
    onFinalizada: () -> Unit,
    seguimientoIdInicial: Long? = null
) {
    val repository = remember { SeguimientoRepository() }
    val scope = rememberCoroutineScope()
    var ruta by remember { mutableStateOf<RutaActivaUsuario?>(null) }
    var cargando by remember { mutableStateOf(true) }
    var finalizando by remember { mutableStateOf(false) }
    var mostrarConfirmacionCancelacion by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val contexto = LocalContext.current
    val sesion by SesionGuia.estado.collectAsState()
    var faltanPermisos by remember { mutableStateOf(permisosGuiaFaltantes(contexto).isNotEmpty()) }
    var avisoPermisos by remember { mutableStateOf<String?>(null) }

    val lanzadorPermisos = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        faltanPermisos = false
        avisoPermisos = if (!tieneUbicacionPrecisa(contexto)) {
            "Sin ubicación precisa no se puede iniciar la guía ni compartir tu posición. " +
                    "Actívala en los permisos de la aplicación."
        } else null
    }

    // La guía (servicio en primer plano) arranca cuando hay ruta activa y permiso de ubicación.
    val seguimientoActivo = ruta?.seguimientoId
    LaunchedEffect(seguimientoActivo, faltanPermisos) {
        if (seguimientoActivo != null && !faltanPermisos && tieneUbicacionPrecisa(contexto)) {
            SesionGuia.iniciar(contexto, seguimientoActivo)
        }
    }

    LaunchedEffect(repository) {
        while (isActive) {
            try {
                ruta = repository.obtenerRutaActivaUsuario()
                // Si la ruta terminó desde otra pantalla, también se detiene la guía.
                if (ruta == null) SesionGuia.detener(contexto)
                error = null
            } catch (e: Exception) {
                error = e.localizedMessage ?: "No se pudo cargar la ruta en curso."
            } finally {
                cargando = false
            }
            delay(10_000)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFAF9F7))
        ) {
            BarraSuperiorRuta("Ruta en curso", onVolver)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
            when {
                cargando -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                ruta == null -> Text(error ?: "No hay una ruta activa.")
                else -> {
                    val actual = ruta!!
                    Text(actual.nombreRuta, fontWeight = FontWeight.Bold)
                    if (faltanPermisos) {
                        TarjetaPermisosGuia { lanzadorPermisos.launch(permisosGuia()) }
                    }
                    avisoPermisos?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    sesion.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (sesion.error != null && !sesion.activa && !sesion.cargando && !faltanPermisos) {
                        TextButton(
                            onClick = { SesionGuia.iniciar(contexto, actual.seguimientoId) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Reintentar la guía")
                        }
                    }
                    if (sesion.activa || sesion.cargando) {
                        PanelGuiaRuta(sesion)
                    }
                    Text("Progreso: ${actual.progresoPorcentaje}%")
                    // Datos guardados en Supabase (se refrescan cada 10 s); la guía en vivo está arriba.
                    if (!sesion.activa) Text(
                        actual.siguienteNodoNombre?.let { "Siguiente punto: $it" }
                            ?: "Has llegado al último punto de la ruta."
                    )
                    if (!sesion.activa) Text(
                        actual.distanciaSiguienteNodoMetros?.let {
                            "Distancia hasta el siguiente punto: ${formatearDistancia(it)}"
                        } ?: "Distancia hasta el siguiente punto: No disponible"
                    )
                    MapaRutaComposable(
                        ruta = actual.toRutaDisponible(),
                        descripcion = "Mapa de la ruta en curso ${actual.nombreRuta}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                finalizando = true
                                error = null
                                try {
                                    val seguimientoId = ruta?.seguimientoId
                                        ?: seguimientoIdInicial
                                        ?: throw IllegalStateException("No hay un seguimiento activo para finalizar.")
                                    SesionGuia.detener(contexto)
                                    repository.finalizarRuta(seguimientoId)
                                    onFinalizada()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Log.e("EcoGuia", "Error al guardar seguimiento: ${e.message}", e)
                                    error = e.localizedMessage ?: "No se pudo finalizar la ruta."
                                    snackbarHostState.showSnackbar("No se pudo guardar el seguimiento")
                                } finally {
                                    finalizando = false
                                }
                            }
                        },
                        enabled = !finalizando,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = "Finalizar ruta en curso" }
                    ) {
                        Text(if (finalizando) "Finalizando…" else "Finalizar ruta")
                    }
                }
            }
            if (ruta != null) {
                TextButton(
                    onClick = { mostrarConfirmacionCancelacion = true },
                    enabled = !finalizando,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancelar ruta")
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(8.dp))
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }

    if (mostrarConfirmacionCancelacion) {
        AlertDialog(
            onDismissRequest = { mostrarConfirmacionCancelacion = false },
            title = { Text("¿Cancelar ruta?") },
            text = { Text("El recorrido se marcará como detenido.") },
            confirmButton = {
                TextButton(
                    enabled = !finalizando,
                    onClick = {
                        scope.launch {
                            finalizando = true
                            error = null
                            try {
                                val seguimientoId = ruta?.seguimientoId
                                    ?: seguimientoIdInicial
                                    ?: throw IllegalStateException("No hay un seguimiento activo para cancelar.")
                                SesionGuia.detener(contexto)
                                repository.detenerRuta(seguimientoId)
                                mostrarConfirmacionCancelacion = false
                                onFinalizada()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.e("EcoGuia", "Error al guardar seguimiento: ${e.message}", e)
                                error = e.localizedMessage ?: "No se pudo cancelar la ruta."
                                snackbarHostState.showSnackbar("No se pudo guardar el seguimiento")
                            } finally {
                                finalizando = false
                            }
                        }
                    }
                ) {
                    Text("Cancelar ruta")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarConfirmacionCancelacion = false }) {
                    Text("Continuar ruta")
                }
            }
        )
    }
}

private fun RutaActivaUsuario.toRutaDisponible() = RutaDisponible(
    id = rutaId,
    nombre = nombreRuta,
    nodos = nodos.map { NodoRuta(it.id, it.nombre, it.latitud, it.longitud) }
)

private fun formatearDistancia(metros: Double): String {
    val numero = NumberFormat.getNumberInstance(localeEspanol).apply {
        maximumFractionDigits = 0
    }.format(metros)
    return "$numero m"
}
