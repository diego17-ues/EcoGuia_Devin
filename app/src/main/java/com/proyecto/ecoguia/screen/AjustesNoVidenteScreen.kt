
package com.proyecto.ecoguia.screen

import android.media.AudioManager
import android.media.ToneGenerator

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

import androidx.compose.runtime.*

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.proyecto.ecoguia.R
import com.proyecto.ecoguia.components.GenerarCodigoVinculacion
import com.proyecto.ecoguia.navigation.BottomNavItem
import com.proyecto.ecoguia.navigation.EcoGuiaNavigationBar

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val fondoAjustes = Color(0xFFEFF0F8)
private val oscuroAjustes = Color(0xFF19171F)
private val azulAjustes = Color(0xFFC6D1F4)
private val grisAjustes = Color(0xFF777783)
private val amarilloAjustes = Color(0xFFFFC21C)
private val moradoAjustes = Color(0xFF5B66C5)

@Composable
fun AjustesNoVidenteScreen(
    onInicioClick: () -> Unit
) {

    var pitidosAbierto by remember {
        mutableStateOf(false)
    }

    var guiaAbierta by remember {
        mutableStateOf(false)
    }

    var vincularAbierto by remember {
        mutableStateOf(false)
    }

    var mensaje by remember {
        mutableStateOf("")
    }

    val scope = rememberCoroutineScope()

    var sonidoJob by remember {
        mutableStateOf<Job?>(null)
    }

    // NUEVO TONO ELECTRÓNICO
    val generador = remember {
        try {
            ToneGenerator(
                AudioManager.STREAM_MUSIC,
                80
            )
        } catch (_: Exception) {
            null
        }
    }

    // Liberar los recursos al salir
    DisposableEffect(generador) {

        onDispose {
            sonidoJob?.cancel()
            generador?.stopTone()
            generador?.release()
        }
    }

    // REPRODUCIR PATRONES DEL ESP32
    //
    // Los valores representan milisegundos.
    // Alternan sonido, silencio, sonido...

    fun reproducirPitidos(
        patron: List<Int>
    ) {

        if (generador == null) {
            mensaje = "No se pudo reproducir el sonido."
            return
        }

        sonidoJob?.cancel()
        generador.stopTone()

        sonidoJob = scope.launch {

            try {

                patron.forEachIndexed { indice, duracion ->

                    if (indice % 2 == 0) {

                        // Tono diferente al anterior
                        generador.startTone(
                            ToneGenerator.TONE_DTMF_5,
                            duracion
                        )

                    } else {

                        generador.stopTone()
                    }

                    delay(duracion.toLong())

                    generador.stopTone()
                }

            } catch (e: CancellationException) {

                throw e

            } finally {

                generador.stopTone()
            }
        }
    }

    // VENTANA DE MENSAJES

    if (mensaje.isNotEmpty()) {

        AlertDialog(
            onDismissRequest = {
                mensaje = ""
            },
            title = {

                Text(
                    text = "EcoGuía",
                    fontWeight = FontWeight.Bold,
                    color = oscuroAjustes
                )
            },
            text = {

                Text(
                    text = mensaje,
                    color = oscuroAjustes
                )
            },
            confirmButton = {

                TextButton(
                    onClick = {
                        mensaje = ""
                    }
                ) {

                    Text(
                        text = "Aceptar",
                        color = moradoAjustes
                    )
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // PANTALLA PRINCIPAL

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(fondoAjustes)
    ) {

        // ENCABEZADO

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(69.dp)
                .background(oscuroAjustes),
            contentAlignment = Alignment.Center
        ) {

            Text(
                text = "Ajustes",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // CONTENIDO DESPLAZABLE

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = 20.dp,
                    vertical = 20.dp
                ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {

            // =====================================
            // 1. SIGNIFICADO DE LOS PITIDOS
            // =====================================

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(17.dp))
                    .background(Color.White)
            ) {

                EncabezadoAjuste(
                    titulo = "Significado de los pitidos",
                    icono = R.drawable.ic_pitidos,
                    abierto = pitidosAbierto,
                    onClick = {
                        pitidosAbierto = !pitidosAbierto
                    }
                )

                if (pitidosAbierto) {

                    Column(
                        modifier = Modifier.padding(
                            horizontal = 13.dp,
                            vertical = 13.dp
                        ),
                        verticalArrangement =
                            Arrangement.spacedBy(12.dp)
                    ) {

                        // IZQUIERDA
                        OpcionSonidoAjuste(
                            etiqueta = "2 pitidos cortos",
                            significado = "Girar a la izquierda",
                            onClick = {
                                reproducirPitidos(
                                    listOf(50, 50, 50)
                                )
                            }
                        )

                        // DERECHA
                        OpcionSonidoAjuste(
                            etiqueta = "1 pitido largo",
                            significado = "Girar a la derecha",
                            onClick = {
                                reproducirPitidos(
                                    listOf(300)
                                )
                            }
                        )

                        // NODO ALCANZADO
                        OpcionSonidoAjuste(
                            etiqueta = "2 cortos y 1 largo",
                            significado = "Nodo alcanzado",
                            onClick = {
                                reproducirPitidos(
                                    listOf(
                                        100, 100, 100,
                                        100, 400
                                    )
                                )
                            }
                        )

                        // DESTINO ALCANZADO
                        OpcionSonidoAjuste(
                            etiqueta = "3 cortos y 1 largo",
                            significado = "Destino alcanzado",
                            onClick = {
                                reproducirPitidos(
                                    listOf(
                                        150, 100, 150,
                                        100, 150, 100,
                                        700
                                    )
                                )
                            }
                        )

                        // ALERTA DE CONEXIÓN
                        OpcionSonidoAjuste(
                            etiqueta = "3 pitidos rápidos",
                            significado = "Alerta de conexión",
                            onClick = {
                                reproducirPitidos(
                                    listOf(
                                        80, 80, 80,
                                        80, 80
                                    )
                                )
                            }
                        )

                        // ESPERANDO GPS
                        OpcionSonidoAjuste(
                            etiqueta = "1 pitido muy corto",
                            significado = "Esperando señal GPS",
                            onClick = {
                                reproducirPitidos(
                                    listOf(50)
                                )
                            }
                        )

                        Spacer(Modifier.height(3.dp))

                        Text(
                            text = "Cuando avanzas correctamente, " +
                                    "no se emiten pitidos de giro.",
                            fontSize = 12.sp,
                            color = oscuroAjustes
                        )

                        Text(
                            text = "Toca una opción para escuchar " +
                                    "el sonido de ejemplo.",
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = grisAjustes
                        )
                    }
                }
            }

            // =====================================
            // 2. GUÍA RÁPIDA
            // =====================================

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(17.dp))
                    .background(Color.White)
            ) {

                EncabezadoAjuste(
                    titulo = "Guía rápida",
                    icono = R.drawable.ic_guia_rapida,
                    abierto = guiaAbierta,
                    onClick = {
                        guiaAbierta = !guiaAbierta
                    }
                )

                if (guiaAbierta) {

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 14.dp,
                                vertical = 14.dp
                            )
                    ) {

                        Text(
                            text = "EcoGuía ayuda a las personas " +
                                    "con discapacidad visual a " +
                                    "seleccionar rutas y recibir " +
                                    "indicaciones durante sus recorridos. " +
                                    "También permite utilizar un " +
                                    "asistente de voz y vincular una " +
                                    "cuenta con una persona encargada.",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = grisAjustes
                        )

                        Spacer(Modifier.height(17.dp))

                        TextoGuiaAjuste(
                            titulo = "1. Asistente de voz",
                            descripcion =
                                "Permite acceder a las funciones " +
                                        "de EcoGuía mediante instrucciones " +
                                        "habladas, sin necesidad de " +
                                        "utilizar los controles visuales."
                        )

                        Spacer(Modifier.height(14.dp))

                        TextoGuiaAjuste(
                            titulo = "2. Elegir ruta",
                            descripcion =
                                "Permite seleccionar uno de los " +
                                        "destinos disponibles para " +
                                        "iniciar un recorrido."
                        )

                        Spacer(Modifier.height(14.dp))

                        TextoGuiaAjuste(
                            titulo = "3. Agregar ruta",
                            descripcion =
                                "Permite incorporar nuevas rutas " +
                                        "para utilizarlas posteriormente."
                        )

                        Spacer(Modifier.height(14.dp))

                        TextoGuiaAjuste(
                            titulo = "4. Ruta actual",
                            descripcion =
                                "Permite consultar la ruta " +
                                        "seleccionada y seguir las " +
                                        "indicaciones durante el recorrido."
                        )

                        Spacer(Modifier.height(14.dp))

                        TextoGuiaAjuste(
                            titulo = "5. Vincular con encargado",
                            descripcion =
                                "Permite generar un código para " +
                                        "que una persona encargada pueda " +
                                        "vincular su cuenta con la " +
                                        "de la persona no vidente."
                        )
                    }
                }
            }

            // =====================================
            // 3. VINCULAR CON ENCARGADO
            // =====================================

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(17.dp))
                    .background(Color.White)
            ) {

                EncabezadoAjuste(
                    titulo = "Vincular con encargado",
                    icono = R.drawable.ic_vincular,
                    abierto = vincularAbierto,
                    onClick = {
                        vincularAbierto = !vincularAbierto
                    }
                )

                if (vincularAbierto) {

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 14.dp,
                                vertical = 19.dp
                            ),
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {

                        // COMPONENTE REAL DE VINCULACIÓN
                        // Conserva las funciones de Supabase,
                        // el estado y la desvinculación.

                        GenerarCodigoVinculacion()
                    }
                }
            }
        }

        // =====================================
        // BARRA INFERIOR
        // =====================================

        EcoGuiaNavigationBar(
            pantallaActual = BottomNavItem.AJUSTES,
            onInicioClick = onInicioClick,
            onAjustesClick = {}
        )
    }
}

// =====================================
// ENCABEZADO DESPLEGABLE
// =====================================

@Composable
private fun EncabezadoAjuste(
    titulo: String,
    icono: Int,
    abierto: Boolean,
    onClick: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(azulAjustes)
            .clickable(
                role = Role.Button,
                onClick = onClick
            )
            .padding(
                horizontal = 14.dp,
                vertical = 11.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Image(
            painter = painterResource(id = icono),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            colorFilter = ColorFilter.tint(
                oscuroAjustes
            )
        )

        Spacer(Modifier.width(10.dp))

        Text(
            text = titulo,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = oscuroAjustes
        )

        Image(
            painter = painterResource(
                id = R.drawable.ic_expandir
            ),
            contentDescription =
                if (abierto) "Contraer" else "Desplegar",
            modifier = Modifier
                .size(25.dp)
                .graphicsLayer {
                    rotationZ = if (abierto) 180f else 0f
                },
            colorFilter = ColorFilter.tint(
                oscuroAjustes
            )
        )
    }
}

// =====================================
// OPCIÓN PARA REPRODUCIR PITIDOS
// =====================================

@Composable
private fun OpcionSonidoAjuste(
    etiqueta: String,
    significado: String,
    onClick: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(Color.White)
            .border(
                width = 1.dp,
                color = amarilloAjustes,
                shape = RoundedCornerShape(9.dp)
            )
            .clickable(
                role = Role.Button,
                onClickLabel =
                    "Escuchar $etiqueta. $significado",
                onClick = onClick
            )
            .padding(
                horizontal = 12.dp,
                vertical = 11.dp
            ),
        verticalArrangement =
            Arrangement.spacedBy(5.dp)
    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            Image(
                painter = painterResource(
                    id = R.drawable.ic_pitidos
                ),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(
                    oscuroAjustes
                )
            )

            Spacer(Modifier.width(9.dp))

            Text(
                text = etiqueta,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = oscuroAjustes
            )
        }

        Text(
            text = significado,
            fontSize = 12.sp,
            color = grisAjustes,
            modifier = Modifier.padding(start = 29.dp)
        )
    }
}

// =====================================
// TEXTO DE LA GUÍA RÁPIDA
// =====================================

@Composable
private fun TextoGuiaAjuste(
    titulo: String,
    descripcion: String
) {

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {

        Text(
            text = titulo,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = oscuroAjustes
        )

        Spacer(Modifier.height(5.dp))

        Text(
            text = descripcion,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = grisAjustes
        )
    }
}
