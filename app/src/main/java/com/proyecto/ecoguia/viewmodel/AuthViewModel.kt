
package com.proyecto.ecoguia.screen

import androidx.activity.compose.BackHandler

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll

import androidx.compose.foundation.text.KeyboardOptions

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.proyecto.ecoguia.R
import com.proyecto.ecoguia.data.remote.SupabaseClient
import com.proyecto.ecoguia.data.repository.VincularEncargadoRepository

import com.proyecto.ecoguia.navigation.BottomNavItem
import com.proyecto.ecoguia.navigation.EcoGuiaNavigationBar

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val fondoVincular = Color(0xFFEFF0F8)
private val oscuroVincular = Color(0xFF19171F)
private val moradoVincular = Color(0xFF5B66C5)
private val azulVincular = Color(0xFFC6D1F4)
private val grisVincular = Color(0xFF777783)

@Composable
fun VincularConCiegoScreen(
    onVolver: () -> Unit,
    onAjustesClick: () -> Unit,
    onVinculacionExitosa: () -> Unit = onVolver
) {

    val scope = rememberCoroutineScope()

    val repositorio = remember {
        VincularEncargadoRepository(
            SupabaseClient.client
        )
    }

    var codigo by remember {
        mutableStateOf("")
    }

    var vinculando by remember {
        mutableStateOf(false)
    }

    var mensajeError by remember {
        mutableStateOf("")
    }

    var vinculado by remember {
        mutableStateOf(false)
    }

    // Evitar salir mientras se procesa la vinculación
    BackHandler(
        enabled = vinculando
    ) {}

    // Confirmar vinculación exitosa
    if (vinculado) {

        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    text = "Vinculación exitosa",
                    fontWeight = FontWeight.Bold,
                    color = oscuroVincular
                )
            },
            text = {
                Text(
                    text = "La cuenta se vinculó correctamente. " +
                            "La vinculación permanecerá activa " +
                            "aunque cierres sesión.",
                    color = oscuroVincular
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vinculado = false
                        onVinculacionExitosa()
                    }
                ) {
                    Text(
                        text = "Aceptar",
                        color = moradoVincular
                    )
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(fondoVincular)
    ) {

        // Encabezado con flecha de regreso
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(69.dp)
                .background(oscuroVincular)
        ) {

            Text(
                text = "Vincular con ciego",
                modifier = Modifier.align(Alignment.Center),
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(56.dp)
                    .clickable(
                        enabled = !vinculando
                    ) {
                        onVolver()
                    },
                contentAlignment = Alignment.Center
            ) {

                Image(
                    painter = painterResource(
                        R.drawable.ic_flecha_derecha
                    ),
                    contentDescription = "Regresar",
                    modifier = Modifier
                        .size(26.dp)
                        .graphicsLayer {
                            rotationZ = 180f
                        },
                    colorFilter = ColorFilter.tint(
                        Color.White
                    )
                )
            }
        }

        // Contenido principal
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            Spacer(Modifier.height(12.dp))

            // Icono de vinculación
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(azulVincular),
                contentAlignment = Alignment.Center
            ) {

                Image(
                    painter = painterResource(
                        R.drawable.ic_vincular
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    colorFilter = ColorFilter.tint(
                        oscuroVincular
                    )
                )
            }

            Text(
                text = "Vincular una cuenta",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = oscuroVincular,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Ingresa el código de 6 dígitos " +
                        "generado por la persona no vidente.",
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = grisVincular,
                textAlign = TextAlign.Center
            )

            // Formulario de vinculación
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(17.dp))
                    .background(Color.White)
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                Text(
                    text = "Código de vinculación",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = oscuroVincular
                )

                // Solo permite seis dígitos
                OutlinedTextField(
                    value = codigo,
                    onValueChange = { nuevoValor ->

                        codigo = nuevoValor
                            .filter { it in '0'..'9' }
                            .take(6)

                        mensajeError = ""
                    },
                    enabled = !vinculando,
                    placeholder = {
                        Text("000000")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = moradoVincular,
                        unfocusedBorderColor =
                            Color(0xFFD1D5E4),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )

                Text(
                    text = "El código es válido durante " +
                            "5 minutos desde su generación.",
                    fontSize = 12.sp,
                    color = grisVincular,
                    textAlign = TextAlign.Center
                )

                // Mensajes de validación
                if (mensajeError.isNotEmpty()) {

                    Text(
                        text = mensajeError,
                        fontSize = 12.sp,
                        color = Color(0xFFB42318),
                        textAlign = TextAlign.Center
                    )
                }

                // Botón de vinculación
                Button(
                    onClick = {

                        if (codigo.length != 6) {

                            mensajeError =
                                "Ingresa los 6 dígitos del código."

                        } else {

                            vinculando = true
                            mensajeError = ""

                            scope.launch {

                                try {

                                    val resultado =
                                        repositorio.vincular(codigo)

                                    if (resultado.exito) {

                                        codigo = ""
                                        vinculado = true

                                    } else {

                                        mensajeError =
                                            resultado.mensaje
                                    }

                                } catch (
                                    e: CancellationException
                                ) {

                                    throw e

                                } catch (e: Exception) {

                                    mensajeError =
                                        "No se pudo completar la " +
                                                "vinculación. Comprueba " +
                                                "tu conexión e inténtalo nuevamente."

                                } finally {

                                    vinculando = false
                                }
                            }
                        }
                    },
                    enabled = !vinculando,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = moradoVincular
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {

                    if (vinculando) {

                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )

                        Spacer(Modifier.width(10.dp))

                        Text("Vinculando...")

                    } else {

                        Text(
                            text = "Vincular",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                text = "La vinculación permanecerá activa " +
                        "aunque ambos usuarios cierren sesión.",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = grisVincular,
                textAlign = TextAlign.Center
            )
        }

        // Barra de navegación inferior
        EcoGuiaNavigationBar(
            pantallaActual = BottomNavItem.INICIO,
            onInicioClick = {
                if (!vinculando) {
                    onVolver()
                }
            },
            onAjustesClick = {
                if (!vinculando) {
                    onAjustesClick()
                }
            }
        )
    }
}
