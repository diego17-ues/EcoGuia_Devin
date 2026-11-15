
package com.proyecto.ecoguia.screen

import android.speech.tts.TextToSpeech

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.proyecto.ecoguia.R
import com.proyecto.ecoguia.components.recordarBiometria
import com.proyecto.ecoguia.data.repository.NoVidenteRepository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

import java.util.Locale

private val fondo = Color(0xFFAFBCE9)
private val amarillo = Color(0xFFFFC21C)
private val textoOscuro = Color(0xFF19192D)
private val gris = Color(0xFF777783)
private val lila = Color(0xFFE7E4F9)
private val azulBoton = Color(0xFF5269E9)

@Composable
fun LoginNoVidenteScreen(
    onVolver: () -> Unit,
    onIniciarSesion: () -> Unit
) {
    val contexto = LocalContext.current

    val repositorio = remember {
        NoVidenteRepository()
    }

    val scope = rememberCoroutineScope()

    var verificando by remember {
        mutableStateOf(false)
    }

    var mensajeError by remember {
        mutableStateOf("")
    }

    // Motor de voz exclusivo de esta pantalla
    var motorVoz by remember {
        mutableStateOf<TextToSpeech?>(null)
    }

    var vozPreparada by remember {
        mutableStateOf(false)
    }

    DisposableEffect(contexto) {

        val motor = TextToSpeech(
            contexto.applicationContext
        ) { estado ->
            vozPreparada =
                estado == TextToSpeech.SUCCESS
        }

        motorVoz = motor
        motor.language = Locale("es", "SV")
        onDispose {
            motor.stop()
            motor.shutdown()
            motorVoz = null
        }
    }

    fun hablar(
        mensaje: String,
        identificador: String
    ) {
        if (vozPreparada) {
            motorVoz?.speak(
                mensaje,
                TextToSpeech.QUEUE_FLUSH,
                null,
                identificador
            )
        }
    }

    // Verificación biométrica con avisos por voz
    val solicitarBiometria = recordarBiometria(
        onExito = {
            verificando = false

            // MainActivity anunciará el acceso exitoso
            // después de cambiar de pantalla.
            onIniciarSesion()
        },
        onError = { error ->
            verificando = false
            mensajeError = error
        },
        onFallo = {
            hablar(
                "Huella no reconocida, vuelva a ingresarla",
                "huella_no_reconocida"
            )
        },
        onCancel = {
            verificando = false
        }
    )

    // Mostrar errores de acceso
    if (mensajeError.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = {
                mensajeError = ""
            },
            title = {
                Text(
                    text = "No se pudo acceder",
                    fontWeight = FontWeight.Bold,
                    color = textoOscuro
                )
            },
            text = {
                Text(
                    text = mensajeError,
                    color = textoOscuro
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        mensajeError = ""
                    }
                ) {
                    Text("Aceptar")
                }
            },
            containerColor = Color(0xFFFAF9F7),
            shape = RoundedCornerShape(20.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(fondo)
    ) {

        // Flecha para regresar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 20.dp,
                    top = 15.dp
                )
        ) {
            Image(
                painter = painterResource(
                    id = R.drawable.ic_flecha_derecha
                ),
                contentDescription = "Volver",
                colorFilter = ColorFilter.tint(
                    textoOscuro
                ),
                modifier = Modifier
                    .size(28.dp)
                    .graphicsLayer {
                        rotationZ = 180f
                    }
                    .clickable(
                        enabled = !verificando
                    ) {
                        onVolver()
                    }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(
                        rememberScrollState()
                    )
                    .padding(
                        horizontal = 28.dp,
                        vertical = 20.dp
                    ),
                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {

                // Logo EcoGuía
                Box(
                    modifier = Modifier
                        .size(95.dp)
                        .background(
                            amarillo,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "EcoGuía",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.Black
                    )
                }

                Spacer(Modifier.height(23.dp))

                Text(
                    text = "Iniciar sesión",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = textoOscuro
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Accede de forma segura con tu huella digital",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = textoOscuro,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(35.dp))

                // Toda la tarjeta blanca es táctil
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(
                            RoundedCornerShape(22.dp)
                        )
                        .background(Color.White)
                        .clickable(
                            enabled = !verificando,
                            role = Role.Button,
                            onClickLabel = "Verificar huella"
                        ) {

                            verificando = true

                            // Indicar por voz que debe ingresar la huella
                            hablar(
                                "Ingrese su huella",
                                "solicitar_huella"
                            )

                            scope.launch {
                                try {

                                    // Comprobar la sesión no vidente
                                    // antes de solicitar la huella.
                                    repositorio
                                        .validarSesionNoVidente()

                                    // Abrir el diálogo de Android.
                                    solicitarBiometria()

                                } catch (
                                    e: CancellationException
                                ) {
                                    verificando = false
                                    throw e

                                } catch (
                                    e: Exception
                                ) {
                                    verificando = false

                                    mensajeError =
                                        e.localizedMessage
                                            ?: "Revisa tu conexión e inténtalo nuevamente."
                                }
                            }
                        }
                        .padding(
                            horizontal = 22.dp,
                            vertical = 28.dp
                        ),
                    horizontalAlignment =
                        Alignment.CenterHorizontally
                ) {

                    // Imagen de huella
                    Box(
                        modifier = Modifier
                            .size(105.dp)
                            .clip(CircleShape)
                            .background(lila),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(
                                id = R.drawable.ic_huella
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(62.dp),
                            colorFilter = ColorFilter.tint(
                                textoOscuro
                            )
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    Text(
                        text = "Coloca tu huella",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = textoOscuro
                    )

                    Spacer(Modifier.height(9.dp))

                    Text(
                        text = "Utiliza la huella registrada en tu teléfono para continuar.",
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        textAlign = TextAlign.Center,
                        color = gris
                    )

                    Spacer(Modifier.height(26.dp))

                    // Área azul informativa
                    // No tiene un evento de clic independiente
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(51.dp)
                            .clip(
                                RoundedCornerShape(13.dp)
                            )
                            .background(
                                if (verificando)
                                    Color(0xFF9CA9DB)
                                else
                                    azulBoton
                            ),
                        contentAlignment = Alignment.Center
                    ) {

                        if (verificando) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(23.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Verificar huella",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = "Toca cualquier parte de este recuadro para verificar tu huella.",
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = gris,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
