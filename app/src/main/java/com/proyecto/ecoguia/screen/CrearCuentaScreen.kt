
package com.proyecto.ecoguia.screen

import android.util.Patterns
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.proyecto.ecoguia.R
import com.proyecto.ecoguia.components.recordarBiometria
import com.proyecto.ecoguia.data.repository.AuthRepository
import com.proyecto.ecoguia.data.repository.NoVidenteRepository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val azul = Color(0xFFAFBCE9)
private val amarillo = Color(0xFFFFC21C)
private val textoOscuro = Color(0xFF19192D)
private val lila = Color(0xFFE7E4F9)
private val fondoBlanco = Color(0xFFFAF9F7)
private val gris = Color(0xFF777783)
private val azulBorde = Color(0xFF8C9FF0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrearCuentaScreen(
    onVolver: () -> Unit,
    onCrearCuenta: (String) -> Unit
) {
    val repoEncargado = remember {
        AuthRepository()
    }

    val repoNoVidente = remember {
        NoVidenteRepository()
    }

    val scope = rememberCoroutineScope()

    var rol by rememberSaveable {
        mutableStateOf("no_vidente")
    }

    var menuAbierto by remember {
        mutableStateOf(false)
    }

    var nombre by rememberSaveable {
        mutableStateOf("")
    }

    var sexo by rememberSaveable {
        mutableStateOf("")
    }

    var usuario by rememberSaveable {
        mutableStateOf("")
    }

    var correo by rememberSaveable {
        mutableStateOf("")
    }

    var contrasena by rememberSaveable {
        mutableStateOf("")
    }

    var confirmar by rememberSaveable {
        mutableStateOf("")
    }

    var mostrarContrasena by rememberSaveable {
        mutableStateOf(false)
    }

    var mostrarConfirmar by rememberSaveable {
        mutableStateOf(false)
    }

    var registrando by remember {
        mutableStateOf(false)
    }

    var huellaVerificada by remember {
        mutableStateOf(false)
    }

    var tituloDialogo by remember {
        mutableStateOf("")
    }

    var mensajeDialogo by remember {
        mutableStateOf("")
    }

    var registroCompletado by remember {
        mutableStateOf(false)
    }

    fun mostrarAviso(
        titulo: String,
        mensaje: String
    ) {
        tituloDialogo = titulo
        mensajeDialogo = mensaje
    }

    // =====================================
    // BIOMETRÍA REAL DE ANDROID
    // =====================================

    val solicitarBiometria = recordarBiometria(
        onExito = {
            huellaVerificada = true

            mostrarAviso(
                "Huella verificada",
                "La verificación biométrica fue correcta. " +
                        "Ahora puedes crear tu cuenta."
            )
        },
        onError = { error ->
            huellaVerificada = false

            mostrarAviso(
                "Verificación biométrica",
                error
            )
        }
    )

    // =====================================
    // VENTANA DE ADVERTENCIAS
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
                    color = textoOscuro
                )
            },
            text = {
                Text(
                    text = mensajeDialogo,
                    color = textoOscuro
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
                        color = Color(0xFF5269E9)
                    )
                }
            },
            containerColor = fondoBlanco,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // =====================================
    // REGISTRO COMPLETADO
    // =====================================

    if (registroCompletado) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    text = if (rol == "encargado")
                        "Comprueba tu correo"
                    else
                        "Cuenta creada",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (rol == "encargado") {
                        "Solicitamos el registro en Supabase. " +
                                "Si la cuenta es nueva, recibirás " +
                                "un correo de confirmación. " +
                                "Después inicia sesión con " +
                                "tu usuario y contraseña."
                    } else {
                        "Tu cuenta de persona no vidente " +
                                "se registró en Supabase. " +
                                "Ahora podrás ingresar utilizando " +
                                "la biometría de este teléfono."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        registroCompletado = false
                        onCrearCuenta(rol)
                    }
                ) {
                    Text(
                        text = "Continuar",
                        color = Color(0xFF5269E9)
                    )
                }
            },
            containerColor = fondoBlanco,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // =====================================
    // PANTALLA
    // =====================================

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(fondoBlanco)
    ) {

        // ENCABEZADO
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
                color = textoOscuro,
                modifier = Modifier.clickable(
                    enabled = !registrando
                ) {
                    onVolver()
                }
            )

            Spacer(Modifier.width(15.dp))

            Text(
                text = "Crear cuenta",
                fontSize = 17.sp,
                color = textoOscuro,
                fontWeight = FontWeight.Medium
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(
                    horizontal = 20.dp,
                    vertical = 17.dp
                ),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            // LOGO ECOGUIA
            Box(
                modifier = Modifier
                    .size(85.dp)
                    .background(
                        amarillo,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "EcoGuía",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.Black
                )
            }

            Spacer(Modifier.height(15.dp))

            Text(
                text = "Crea tu cuenta",
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                color = textoOscuro
            )

            Spacer(Modifier.height(20.dp))

            // =====================================
            // COMBOBOX DEL TIPO DE CUENTA
            // =====================================

            Column(
                modifier = Modifier.fillMaxWidth()
            ) {

                Text(
                    text = "Tipo de cuenta",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textoOscuro
                )

                Spacer(Modifier.height(7.dp))

                ExposedDropdownMenuBox(
                    expanded = menuAbierto && !registrando,
                    onExpandedChange = {
                        if (!registrando) {
                            menuAbierto = !menuAbierto
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {

                    OutlinedTextField(
                        value = if (rol == "no_vidente")
                            "Persona con discapacidad visual"
                        else
                            "Persona encargada",
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        textStyle = TextStyle(
                            fontSize = 13.sp,
                            color = textoOscuro
                        ),
                        trailingIcon = {
                            ExposedDropdownMenuDefaults
                                .TrailingIcon(
                                    expanded = menuAbierto
                                )
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedContainerColor =
                                    Color.White,
                                unfocusedContainerColor =
                                    Color.White,
                                focusedBorderColor =
                                    azulBorde,
                                unfocusedBorderColor =
                                    azulBorde
                            )
                    )

                    ExposedDropdownMenu(
                        expanded = menuAbierto && !registrando,
                        onDismissRequest = {
                            menuAbierto = false
                        }
                    ) {


                        DropdownMenuItem(
                            text = {
                                Text("Persona con discapacidad visual")
                            },
                            onClick = {
                                if (rol != "no_vidente") {
                                    rol = "no_vidente"

                                    // Limpiar todos los campos
                                    nombre = ""
                                    sexo = ""
                                    usuario = ""
                                    correo = ""
                                    contrasena = ""
                                    confirmar = ""

                                    // Restablecer verificación biométrica
                                    huellaVerificada = false

                                    // Ocultar las contraseñas
                                    mostrarContrasena = false
                                    mostrarConfirmar = false
                                }

                                menuAbierto = false
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                Text("Persona encargada")
                            },
                            onClick = {
                                if (rol != "encargado") {
                                    rol = "encargado"

                                    // Limpiar todos los campos
                                    nombre = ""
                                    sexo = ""
                                    usuario = ""
                                    correo = ""
                                    contrasena = ""
                                    confirmar = ""

                                    // Restablecer verificación biométrica
                                    huellaVerificada = false

                                    // Ocultar las contraseñas
                                    mostrarContrasena = false
                                    mostrarConfirmar = false
                                }

                                menuAbierto = false
                            }
                        )

                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            // =====================================
            // FORMULARIO DINÁMICO
            // =====================================

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(20.dp)
                    )
                    .background(lila)
                    .padding(15.dp)
            ) {

                CampoRegistro(
                    titulo = "Nombre completo",
                    valor = nombre,
                    placeholder = "Ingresa tu nombre",
                    onValueChange = {
                        nombre = it
                    },
                    habilitado = !registrando
                )

                // CAMPOS DEL ENCARGADO
                if (rol == "encargado") {

                    Spacer(Modifier.height(13.dp))

                    CampoRegistro(
                        titulo = "Nombre de usuario",
                        valor = usuario,
                        placeholder =
                            "Crea un nombre de usuario",
                        onValueChange = {
                            usuario = it
                        },
                        habilitado = !registrando
                    )

                    Spacer(Modifier.height(13.dp))

                    CampoRegistro(
                        titulo = "Contraseña",
                        valor = contrasena,
                        placeholder =
                            "Ingresa una contraseña",
                        onValueChange = {
                            contrasena = it
                        },
                        esContrasena = true,
                        mostrar = mostrarContrasena,
                        onMostrarChange = {
                            mostrarContrasena =
                                !mostrarContrasena
                        },
                        habilitado = !registrando
                    )

                    Spacer(Modifier.height(13.dp))

                    CampoRegistro(
                        titulo = "Confirmar contraseña",
                        valor = confirmar,
                        placeholder =
                            "Repite tu contraseña",
                        onValueChange = {
                            confirmar = it
                        },
                        esContrasena = true,
                        mostrar = mostrarConfirmar,
                        onMostrarChange = {
                            mostrarConfirmar =
                                !mostrarConfirmar
                        },
                        habilitado = !registrando
                    )

                    Spacer(Modifier.height(13.dp))

                    CampoRegistro(
                        titulo = "Correo electrónico",
                        valor = correo,
                        placeholder =
                            "Ingresa tu correo electrónico",
                        onValueChange = {
                            correo = it
                        },
                        tipoTeclado = KeyboardType.Email,
                        habilitado = !registrando
                    )
                }

                Spacer(Modifier.height(17.dp))

                // =====================================
                // SEXO
                // =====================================

                Text(
                    text = "Sexo",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textoOscuro
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.SpaceEvenly
                ) {
                    listOf(
                        "masculino" to "Masculino",
                        "femenino" to "Femenino"
                    ).forEach { opcion ->

                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically,
                            modifier = Modifier.clickable(
                                enabled = !registrando
                            ) {
                                sexo = opcion.first
                            }
                        ) {
                            RadioButton(
                                selected =
                                    sexo == opcion.first,
                                onClick = {
                                    sexo = opcion.first
                                },
                                enabled = !registrando,
                                modifier = Modifier.size(38.dp),
                                colors =
                                    RadioButtonDefaults.colors(
                                        selectedColor =
                                            Color(0xFF5269E9)
                                    )
                            )

                            Text(
                                text = opcion.second,
                                fontSize = 12.sp,
                                color = gris
                            )
                        }
                    }
                }

                // =====================================
                // ACCESO BIOMÉTRICO NO VIDENTE
                // =====================================

                if (rol == "no_vidente") {

                    Spacer(Modifier.height(18.dp))

                    Text(
                        text = "Acceso mediante huella",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textoOscuro
                    )

                    Spacer(Modifier.height(10.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(
                                RoundedCornerShape(13.dp)
                            )
                            .background(
                                Color(0xFFC8CFEA)
                            )
                            .clickable(
                                enabled = !registrando
                            ) {
                                solicitarBiometria()
                            }
                            .padding(
                                vertical = 17.dp,
                                horizontal = 10.dp
                            ),
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {

                        Image(
                            painter = painterResource(
                                id = R.drawable.ic_huella
                            ),
                            contentDescription =
                                "Activar acceso con huella",
                            modifier = Modifier.size(42.dp),
                            colorFilter = ColorFilter.tint(
                                Color.Black
                            )
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = if (huellaVerificada)
                                "Huella verificada"
                            else
                                "Activar acceso con huella",
                            fontSize = 13.sp,
                            fontWeight =
                                FontWeight.SemiBold,
                            color = textoOscuro
                        )

                        Text(
                            text = if (huellaVerificada)
                                "Verificación biométrica completada"
                            else
                                "Utiliza la huella configurada en tu teléfono",
                            fontSize = 11.sp,
                            color = gris,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // =====================================
                // BOTÓN CREAR CUENTA
                // =====================================

                Box(
                    modifier = Modifier
                        .align(
                            Alignment.CenterHorizontally
                        )
                        .widthIn(min = 165.dp)
                        .height(43.dp)
                        .clip(
                            RoundedCornerShape(12.dp)
                        )
                        .background(azul)
                        .clickable(
                            enabled = !registrando
                        ) {

                            val nombreLimpio = nombre.trim()

                            if (
                                nombreLimpio.length !in 2..150
                            ) {
                                mostrarAviso(
                                    "Revisa tus datos",
                                    "El nombre debe tener " +
                                            "entre 2 y 150 caracteres."
                                )

                            } else if (sexo.isEmpty()) {
                                mostrarAviso(
                                    "Datos incompletos",
                                    "Selecciona tu sexo."
                                )

                            } else if (rol == "no_vidente") {

                                // REGISTRO REAL NO VIDENTE
                                if (!huellaVerificada) {
                                    mostrarAviso(
                                        "Verificación pendiente",
                                        "Primero toca Activar acceso " +
                                                "con huella y completa " +
                                                "la verificación de Android."
                                    )
                                } else {
                                    registrando = true

                                    scope.launch {
                                        try {
                                            repoNoVidente
                                                .registrarNoVidente(
                                                    nombre = nombreLimpio,
                                                    sexo = sexo
                                                )

                                            registroCompletado = true

                                        } catch (
                                            e: CancellationException
                                        ) {
                                            throw e

                                        } catch (
                                            e: Exception
                                        ) {
                                            mostrarAviso(
                                                "No se pudo crear la cuenta",
                                                e.localizedMessage
                                                    ?: "Comprueba tu conexión."
                                            )
                                        } finally {
                                            registrando = false
                                        }
                                    }
                                }

                            } else {
                                // REGISTRO REAL ENCARGADO
                                val usuarioLimpio = usuario
                                    .trim()
                                    .lowercase()

                                val correoLimpio = correo
                                    .trim()
                                    .lowercase()

                                val faltanDatos =
                                    usuarioLimpio.isEmpty() ||
                                            contrasena.isEmpty() ||
                                            confirmar.isEmpty() ||
                                            correoLimpio.isEmpty()

                                val error = when {
                                    faltanDatos ->
                                        "Completa todos los campos."

                                    !usuarioLimpio.matches(
                                        Regex(
                                            "^[a-z0-9_]{3,30}$"
                                        )
                                    ) ->
                                        "El usuario debe tener entre " +
                                                "3 y 30 letras, números o _."

                                    !Patterns.EMAIL_ADDRESS
                                        .matcher(correoLimpio)
                                        .matches() ->
                                        "Ingresa un correo válido."

                                    contrasena.length < 8 ->
                                        "La contraseña debe tener " +
                                                "al menos 8 caracteres."

                                    contrasena != confirmar ->
                                        "Las contraseñas no coinciden."

                                    else -> null
                                }

                                if (error != null) {
                                    mostrarAviso(
                                        "Revisa tus datos",
                                        error
                                    )
                                } else {
                                    registrando = true

                                    scope.launch {
                                        try {
                                            repoEncargado
                                                .registrarEncargado(
                                                    nombre = nombreLimpio,
                                                    sexo = sexo,
                                                    usuario = usuarioLimpio,
                                                    correo = correoLimpio,
                                                    contrasena = contrasena
                                                )

                                            registroCompletado = true

                                        } catch (
                                            e: CancellationException
                                        ) {
                                            throw e

                                        } catch (
                                            e: Exception
                                        ) {
                                            mostrarAviso(
                                                "No se pudo crear la cuenta",
                                                e.localizedMessage
                                                    ?: "Comprueba tu conexión."
                                            )
                                        } finally {
                                            registrando = false
                                        }
                                    }
                                }
                            }
                        }
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (registrando) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = textoOscuro,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Crear cuenta",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = textoOscuro
                        )
                    }
                }
            }
        }
    }
}


// =====================================
// CAMPO REUTILIZABLE
// =====================================

@Composable
private fun CampoRegistro(
    titulo: String,
    valor: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    esContrasena: Boolean = false,
    mostrar: Boolean = false,
    onMostrarChange: () -> Unit = {},
    tipoTeclado: KeyboardType = KeyboardType.Text,
    habilitado: Boolean = true
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {

        Text(
            text = titulo,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = textoOscuro
        )

        Spacer(Modifier.height(6.dp))

        OutlinedTextField(
            value = valor,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = habilitado,
            placeholder = {
                Text(
                    text = placeholder,
                    fontSize = 12.sp,
                    color = gris
                )
            },
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 13.sp,
                color = textoOscuro
            ),
            visualTransformation =
                if (esContrasena && !mostrar)
                    PasswordVisualTransformation()
                else
                    VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (esContrasena)
                    KeyboardType.Password
                else
                    tipoTeclado
            ),
            trailingIcon = if (esContrasena) {
                {
                    IconButton(
                        onClick = onMostrarChange,
                        enabled = habilitado
                    ) {
                        Image(
                            painter = painterResource(
                                id = if (mostrar)
                                    R.drawable.ic_ojo_cerrado
                                else
                                    R.drawable.ic_ojo
                            ),
                            contentDescription =
                                if (mostrar)
                                    "Ocultar contraseña"
                                else
                                    "Mostrar contraseña",
                            modifier = Modifier.size(22.dp),
                            colorFilter = ColorFilter.tint(
                                textoOscuro
                            )
                        )
                    }
                }
            } else null,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = azulBorde,
                unfocusedBorderColor = Color(0xFFCDD1E6)
            )
        )
    }
}
