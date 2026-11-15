package com.proyecto.ecoguia.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.proyecto.ecoguia.data.repository.RutaActivaUsuario
import com.proyecto.ecoguia.data.repository.SeguimientoRepository
import com.proyecto.ecoguia.service.SesionGuia
import androidx.compose.ui.platform.LocalContext
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private val localeEspanol = Locale.forLanguageTag("es-SV")

@Composable
fun RutaActualCard(
    ruta: RutaActivaUsuario?,
    cargando: Boolean,
    onAbrir: () -> Unit,
    onRutaFinalizada: () -> Unit
) {
    val repository = remember { SeguimientoRepository() }
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var finalizando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Ruta actual", fontWeight = FontWeight.Bold)

            when {
                cargando -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                ruta == null -> Text("No hay una ruta activa. Selecciona una ruta para comenzar.")
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(onClick = onAbrir)
                            .semantics(mergeDescendants = true) {
                                role = Role.Button
                                contentDescription = buildString {
                                    append("Ruta actual ${ruta.nombreRuta}. ")
                                    append("Progreso ${ruta.progresoPorcentaje} por ciento. ")
                                    append(ruta.siguienteNodoNombre?.let { "Siguiente punto: $it. " }
                                        ?: "Sin siguiente punto. ")
                                    append(ruta.distanciaSiguienteNodoMetros?.let {
                                        "A ${formatearDistancia(it)}. Toca para ver la ruta en curso."
                                    } ?: "Distancia al siguiente punto no disponible. Toca para ver la ruta en curso.")
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("${ruta.nombreRuta} · ${ruta.progresoPorcentaje}%")
                        Text(
                            ruta.siguienteNodoNombre?.let { "Siguiente punto: $it" }
                                ?: "No hay siguiente punto disponible"
                        )
                        Text(
                            ruta.distanciaSiguienteNodoMetros?.let {
                                "Distancia al siguiente punto: ${formatearDistancia(it)}"
                            } ?: "Distancia al siguiente punto: No disponible"
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            enabled = !finalizando,
                            onClick = {
                                scope.launch {
                                    finalizando = true
                                    error = null
                                    try {
                                        repository.finalizarRuta(ruta.seguimientoId)
                                        SesionGuia.detener(contexto)
                                        onRutaFinalizada()
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
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .semantics {
                                    contentDescription = if (finalizando) {
                                        "Finalizando ruta"
                                    } else {
                                        "Finalizar ruta"
                                    }
                                }
                        ) {
                            Text(if (finalizando) "Finalizando…" else "Finalizar ruta")
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
            SnackbarHost(hostState = snackbarHostState)
        }
    }
}

private fun formatearDistancia(metros: Double): String {
    val numero = NumberFormat.getNumberInstance(localeEspanol).apply {
        maximumFractionDigits = 0
    }.format(metros)
    return "$numero m"
}
