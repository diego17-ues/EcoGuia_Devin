
package com.proyecto.ecoguia.screen

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proyecto.ecoguia.R
import com.proyecto.ecoguia.data.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val azul = Color(0xFFAFBCE9)
private val texto = Color(0xFF19192D)
private val lila = Color(0xFFE7E4F9)
private val fondo = Color(0xFFFAF9F7)
private val gris = Color(0xFF777783)

@Composable
fun LoginEncargadoScreen(
    onVolver: () -> Unit,
    onIniciarSesion: () -> Unit
) {
    val repo = remember { AuthRepository() }
    val scope = rememberCoroutineScope()

    var usuario by remember {
        mutableStateOf("")
    }

    var contrasena by remember {
        mutableStateOf("")
    }

    var visible by remember {
        mutableStateOf(false)
    }

    var cargando by remember {
        mutableStateOf(false)
    }

    // MENSAJES EN VENTANA EMERGENTE
    var tituloDialogo by remember {
        mutableStateOf("")
    }

    var mensajeDialogo by remember {
        mutableStateOf("")
    }

    fun mostrarAviso(
        titulo: String,
        mensaje: String
    ) {
        tituloDialogo = titulo
        mensajeDialogo = mensaje
    }

    // =====================================
    // VENTANA DE VALIDACIÓN O ERROR
    // =====================================

    if (mensajeDialogo.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = {
                mensajeDialogo = ""
            },
            title = {
                Text(
                    text = tituloDialogo,
                    fontWeight = FontWeight.Bold,
                    color = texto
                )
            },
            text = {
                Text(
                    text = mensajeDialogo,
                    color = texto
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        mensajeDialogo = ""
                    }
                ) {
                    Text(
                        text = "Aceptar",
                        color = Color(0xFF5269E9),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            containerColor = fondo,
            shape = RoundedCornerShape(20.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(fondo)
    ) {

        // =====================================
        // ENCABEZADO
        // =====================================

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .background(azul)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                text = "‹",
                fontSize = 34.sp,
                color = texto,
                modifier = Modifier.clickable(
                    enabled = !cargando
                ) {
                    onVolver()
                }
            )

            Spacer(Modifier.width(15.dp))

            Text(
                text = "Iniciar sesión",
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = texto
            )
        }

        // =====================================
        // CONTENIDO
        // =====================================

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = 25.dp,
                    vertical = 24.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // LOGO ECOGUIA
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .background(
                        Color(0xFFFFC21C),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "EcoGuía",
                    color = Color.Black,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Bienvenido de nuevo",
                color = texto,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(9.dp))

            Text(
                text = "Ingresa como persona encargada",
                color = gris,
                fontSize = 13.sp
            )

            Spacer(Modifier.height(28.dp))

            // =====================================
            // FORMULARIO
            // =====================================

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        lila,
                        RoundedCornerShape(20.dp)
                    )
                    .padding(18.dp)
            ) {

                Text(
                    text = "Nombre de usuario",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = texto
                )

                Spacer(Modifier.height(6.dp))

                OutlinedTextField(
                    value = usuario,
                    onValueChange = {
                        usuario = it
                    },
                    placeholder = {
                        Text(
                            text = "Ingresa tu usuario",
                            fontSize = 13.sp
                        )
                    },
                    singleLine = true,
                    enabled = !cargando,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Contraseña",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = texto
                )

                Spacer(Modifier.height(6.dp))

                OutlinedTextField(
                    value = contrasena,
                    onValueChange = {
                        contrasena = it
                    },
                    placeholder = {
                        Text(
                            text = "Ingresa tu contraseña",
                            fontSize = 13.sp
                        )
                    },
                    visualTransformation =
                        if (visible)
                            VisualTransformation.None
                        else
                            PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !cargando,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                visible = !visible
                            },
                            enabled = !cargando
                        ) {
                            Image(
                                painter = painterResource(
                                    id = if (visible)
                                        R.drawable.ic_ojo_cerrado
                                    else
                                        R.drawable.ic_ojo
                                ),
                                contentDescription =
                                    if (visible)
                                        "Ocultar contraseña"
                                    else
                                        "Mostrar contraseña",
                                colorFilter = ColorFilter.tint(
                                    texto
                                ),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                )

                Spacer(Modifier.height(22.dp))

                // =====================================
                // BOTÓN INICIAR SESIÓN
                // =====================================

                Button(
                    onClick = {

                        val usuarioLimpio =
                            usuario.trim().lowercase()

                        when {

                            usuarioLimpio.isEmpty() &&
                                    contrasena.isEmpty() -> {

                                mostrarAviso(
                                    "Datos incompletos",
                                    "Ingresa tu nombre de usuario " +
                                            "y tu contraseña para continuar."
                                )
                            }

                            usuarioLimpio.isEmpty() -> {

                                mostrarAviso(
                                    "Datos incompletos",
                                    "Debes ingresar tu nombre de usuario."
                                )
                            }

                            contrasena.isEmpty() -> {

                                mostrarAviso(
                                    "Datos incompletos",
                                    "Debes ingresar tu contraseña."
                                )
                            }

                            else -> {

                                cargando = true

                                scope.launch {
                                    try {
                                        repo.iniciarSesionEncargado(
                                            usuarioLimpio,
                                            contrasena
                                        )

                                        // ACCESO CORRECTO
                                        onIniciarSesion()

                                    } catch (e: CancellationException) {
                                        throw e

                                    } catch (e: Exception) {

                                        mostrarAviso(
                                            "No se pudo iniciar sesión",
                                            e.localizedMessage
                                                ?: "Comprueba tus credenciales " +
                                                "e inténtalo nuevamente."
                                        )

                                    } finally {
                                        cargando = false
                                    }
                                }
                            }
                        }
                    },
                    enabled = !cargando,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(47.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = azul
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {

                    if (cargando) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = texto
                        )
                    } else {
                        Text(
                            text = "Iniciar sesión",
                            color = texto,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            Text(
                text = "Volver",
                color = texto,
                modifier = Modifier.clickable(
                    enabled = !cargando
                ) {
                    onVolver()
                }
            )
        }
    }
}
