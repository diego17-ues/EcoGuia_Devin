
package com.proyecto.ecoguia

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.view.HapticFeedbackConstants
import android.widget.Toast

import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import com.mapbox.common.MapboxOptions
import com.proyecto.ecoguia.service.SesionGuia
import com.proyecto.ecoguia.data.repository.AuthRepository
import com.proyecto.ecoguia.screen.*
import com.proyecto.ecoguia.ui.theme.EcoGuiaTheme

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

import java.util.Locale
import java.text.Normalizer

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configurar Mapbox antes de mostrar los mapas
        MapboxOptions.accessToken =
            getString(R.string.mapbox_access_token).trim()

        setContent {

            EcoGuiaTheme {

                val contexto = LocalContext.current
                val vista = LocalView.current

                var pantalla by remember {
                    mutableStateOf("seleccion")
                }

                var seguimientoIdActual by remember {
                    mutableStateOf<Long?>(null)
                }

                val repoEncargado = remember {
                    AuthRepository()
                }

                val scope = rememberCoroutineScope()

                var cerrandoSesion by remember {
                    mutableStateOf(false)
                }

                var mostrarConfirmacionSalida by remember {
                    mutableStateOf(false)
                }

                var ultimoAtras by remember {
                    mutableLongStateOf(0L)
                }

                val tiempoConfirmacion = 10000L

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

                    onDispose {
                        motor.stop()
                        motor.shutdown()
                        motorVoz = null
                    }
                }

                fun hablar(mensaje: String) {

                    val motor = motorVoz

                    if (motor != null && vozPreparada) {

                        motor.language = Locale("es", "SV")

                        motor.speak(
                            mensaje,
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            "ecoguia_navegacion"
                        )

                    } else {

                        Toast.makeText(
                            contexto,
                            mensaje,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                // Controlar el regreso entre pantallas
                fun volver() {

                    if (cerrandoSesion) return

                    when (pantalla) {

                        "principal_encargado" -> {
                            mostrarConfirmacionSalida = true
                        }

                        "ajustes_encargado" -> {
                            pantalla = "principal_encargado"
                        }

                        // Regresar desde vinculación
                        "vincular_encargado" -> {
                            pantalla = "principal_encargado"
                        }

                        // Regresar desde rastreo
                        "seguimiento" -> {
                            pantalla = "principal_encargado"
                        }

                        // Regresar desde agregar ruta
                        "agregar_ruta" -> {
                            pantalla = "principal_ciego"
                        }

                        // Regresar desde seleccionar ruta
                        "seleccionar_ruta" -> {
                            pantalla = "principal_ciego"
                        }

                        // Regresar desde ruta en curso
                        "ruta_en_curso" -> {
                            pantalla = "principal_ciego"
                        }

                        "ajustes_no_vidente" -> {
                            ultimoAtras = 0L
                            pantalla = "principal_ciego"
                        }

                        "principal_ciego" -> {

                            val ahora =
                                SystemClock.elapsedRealtime()

                            if (
                                ultimoAtras != 0L &&
                                ahora - ultimoAtras <=
                                tiempoConfirmacion
                            ) {

                                ultimoAtras = 0L
                                motorVoz?.stop()
                                pantalla = "seleccion"

                            } else {

                                ultimoAtras = ahora

                                vista.performHapticFeedback(
                                    HapticFeedbackConstants.LONG_PRESS
                                )

                                hablar(
                                    "Para salir, presiona atrás nuevamente."
                                )
                            }
                        }

                        else -> {
                            ultimoAtras = 0L
                            pantalla = "seleccion"
                        }
                    }
                }

                BackHandler(
                    enabled = pantalla != "seleccion"
                ) {
                    if (mostrarConfirmacionSalida) {
                        mostrarConfirmacionSalida = false
                    } else {
                        volver()
                    }
                }

                // El asistente se activa solo al tocar su tarjeta.
                // El reconocimiento lo realiza una actividad de voz del dispositivo.
                val lanzadorVoz = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { resultado ->
                    if (resultado.resultCode == Activity.RESULT_OK) {
                        val opciones = resultado.data?.getStringArrayListExtra(
                            RecognizerIntent.EXTRA_RESULTS
                        )
                        val fraseOriginal = opciones?.firstOrNull().orEmpty()
                        val frase = Normalizer.normalize(
                            fraseOriginal.lowercase(Locale.ROOT),
                            Normalizer.Form.NFD
                        ).replace(Regex("\\p{M}+"), "")

                        if (frase.isBlank()) {
                            hablar("No pude entenderte. Inténtalo de nuevo.")
                        } else {
                            val esEncargado = pantalla == "principal_encargado"
                            val esNoVidente = pantalla == "principal_ciego" ||
                                    pantalla == "seleccionar_ruta"

                            when {
                                frase.contains("ayuda") ||
                                        frase.contains("que puedo decir") ||
                                        frase.contains("que puedes hacer") ||
                                        frase.contains("comandos") -> {
                                    hablar(
                                        if (esEncargado)
                                            "Puedes decir: rastrear, vincular con no vidente, abrir ajustes, o volver."
                                        else
                                            "Puedes decir: elegir ruta, agregar ruta, ruta actual, abrir ajustes, ir al inicio, o volver."
                                    )
                                }

                                frase.contains("volver") || frase.contains("atras") ||
                                        frase.contains("regresar") -> {
                                    volver()
                                }

                                frase.contains("inicio") || frase.contains("pantalla principal") -> {
                                    pantalla = if (esEncargado) "principal_encargado"
                                    else "principal_ciego"
                                    hablar("Abriendo inicio")
                                }

                                frase.contains("ajustes") || frase.contains("configuracion") -> {
                                    pantalla = if (esEncargado) "ajustes_encargado"
                                    else "ajustes_no_vidente"
                                    hablar("Abriendo ajustes")
                                }

                                esEncargado && frase.contains("rastrear") -> {
                                    pantalla = "seguimiento"
                                    hablar("Abriendo rastreo")
                                }

                                esEncargado && (frase.contains("vincular") ||
                                        frase.contains("vinculacion")) -> {
                                    pantalla = "vincular_encargado"
                                    hablar("Abriendo vinculación")
                                }

                                esNoVidente && (frase.contains("elegir ruta") ||
                                        frase.contains("seleccionar ruta") ||
                                        frase.contains("buscar ruta") ||
                                        frase.contains("iniciar ruta")) -> {
                                    pantalla = "seleccionar_ruta"
                                    hablar("Elige una ruta y confirma su inicio en pantalla")
                                }

                                esNoVidente && (frase.contains("agregar ruta") ||
                                        frase.contains("crear ruta") ||
                                        frase.contains("nueva ruta")) -> {
                                    pantalla = "agregar_ruta"
                                    hablar("Abriendo agregar ruta")
                                }

                                esNoVidente && (frase.contains("ruta actual") ||
                                        frase.contains("ruta en curso") ||
                                        frase.contains("mi ruta")) -> {
                                    pantalla = "ruta_en_curso"
                                    hablar("Abriendo ruta en curso")
                                }

                                frase.contains("donde estoy") ||
                                        frase.contains("mi ubicacion") -> {
                                    hablar("La consulta de ubicación por voz aún no está disponible")
                                }

                                else -> {
                                    hablar("No reconozco ese comando. Di ayuda para escuchar las opciones")
                                }
                            }
                        }
                    }
                }

                fun iniciarAsistenteVoz() {
                    // No se registra audio en EcoGuía; se invoca el reconocedor del sistema.
                    val solicitud = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                        )
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-SV")
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-SV")
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "¿Qué necesitas hacer en EcoGuía?")
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    }
                    try {
                        lanzadorVoz.launch(solicitud)
                    } catch (_: ActivityNotFoundException) {
                        hablar("Este teléfono no tiene disponible el reconocimiento de voz")
                    } catch (_: Exception) {
                        hablar("No se pudo abrir el reconocimiento de voz")
                    }
                }

                // Confirmación para cerrar sesión del encargado
                if (
                    mostrarConfirmacionSalida &&
                    pantalla == "principal_encargado"
                ) {

                    AlertDialog(
                        onDismissRequest = {
                            if (!cerrandoSesion) {
                                mostrarConfirmacionSalida = false
                            }
                        },
                        title = {
                            Text(
                                text = "¿Deseas salir?",
                                color = Color(0xFF19192D),
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Text(
                                text = "¿Estás seguro de que quieres " +
                                        "salir de tu cuenta de encargado?",
                                color = Color(0xFF19192D)
                            )
                        },
                        confirmButton = {

                            TextButton(
                                enabled = !cerrandoSesion,
                                onClick = {

                                    cerrandoSesion = true

                                    scope.launch {
                                        try {

                                            repoEncargado.cerrarSesion()

                                            mostrarConfirmacionSalida =
                                                false

                                            pantalla = "seleccion"

                                        } catch (
                                            e: CancellationException
                                        ) {
                                            throw e

                                        } catch (e: Exception) {

                                            mostrarConfirmacionSalida =
                                                false

                                            Toast.makeText(
                                                contexto,
                                                "No se pudo cerrar la sesión.",
                                                Toast.LENGTH_LONG
                                            ).show()

                                        } finally {
                                            cerrandoSesion = false
                                        }
                                    }
                                }
                            ) {
                                Text(
                                    text = "Sí, salir",
                                    color = Color(0xFF5269E9),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(
                                enabled = !cerrandoSesion,
                                onClick = {
                                    mostrarConfirmacionSalida = false
                                }
                            ) {
                                Text(
                                    text = "Cancelar",
                                    color = Color(0xFF777783)
                                )
                            }
                        },
                        containerColor = Color(0xFFFAF9F7),
                        shape = RoundedCornerShape(20.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                ) {

                    when (pantalla) {

                        // Selección del tipo de usuario
                        "seleccion" -> SeleccionRolScreen(
                            onNoVidenteClick = {
                                pantalla = "no_vidente"
                            },
                            onEncargadoClick = {
                                pantalla = "encargado"
                            },
                            onCrearCuentaClick = {
                                pantalla = "registro"
                            }
                        )

                        // Registro de cuentas
                        "registro" -> CrearCuentaScreen(
                            onVolver = {
                                pantalla = "seleccion"
                            },
                            onCrearCuenta = { rol ->
                                pantalla =
                                    if (rol == "encargado")
                                        "encargado"
                                    else
                                        "no_vidente"
                            }
                        )

                        // Inicio de sesión del encargado
                        "encargado" -> LoginEncargadoScreen(
                            onVolver = {
                                pantalla = "seleccion"
                            },
                            onIniciarSesion = {
                                pantalla = "principal_encargado"
                            }
                        )

                        // Inicio de sesión de la persona no vidente
                        "no_vidente" -> LoginNoVidenteScreen(
                            onVolver = {
                                pantalla = "seleccion"
                            },
                            onIniciarSesion = {
                                ultimoAtras = 0L
                                pantalla = "principal_ciego"

                                hablar(
                                    "Has ingresado correctamente"
                                )
                            }
                        )

                        // Pantalla principal del encargado
                        "principal_encargado" ->
                            HomeEncargadoScreen(
                                onRastrearClick = {
                                    pantalla = "seguimiento"
                                },

                                // Abrir pantalla de vinculación
                                onVincularClick = {
                                    pantalla = "vincular_encargado"
                                },

                                onAjustesClick = {
                                    pantalla = "ajustes_encargado"
                                },
                                onAsistenteVozClick = { iniciarAsistenteVoz() }
                            )

                        // Pantalla para vincular con persona no vidente
                        "vincular_encargado" ->
                            VincularConCiegoScreen(
                                onVolver = {
                                    pantalla = "principal_encargado"
                                },
                                onAjustesClick = {
                                    pantalla = "ajustes_encargado"
                                },
                                onVinculacionExitosa = {
                                    pantalla = "principal_encargado"
                                }
                            )

                        // Rastreo del encargado con Mapbox
                        "seguimiento" ->
                            SeguimientoEncargadoScreen(
                                onVolver = {
                                    pantalla = "principal_encargado"
                                },
                                onAjustesClick = {
                                    pantalla = "ajustes_encargado"
                                }
                            )

                        // Ajustes del encargado
                        "ajustes_encargado" ->
                            AjustesEncargadoScreen(
                                onInicioClick = {
                                    pantalla = "principal_encargado"
                                }
                            )

                        // Pantalla principal de la persona no vidente
                        "principal_ciego" ->
                            HomeNoVidenteScreen(
                                onAjustesClick = {
                                    ultimoAtras = 0L
                                    pantalla = "ajustes_no_vidente"
                                },
                                onElegirRutaClick = {
                                    pantalla = "seleccionar_ruta"
                                },
                                onAgregarRutaClick = {
                                    pantalla = "agregar_ruta"
                                },
                                onRutaActualClick = {
                                    pantalla = "ruta_en_curso"
                                },
                                onAsistenteVozClick = { iniciarAsistenteVoz() }
                            )

                        // Ajustes de la persona no vidente
                        "ajustes_no_vidente" ->
                            AjustesNoVidenteScreen(
                                onInicioClick = {
                                    ultimoAtras = 0L
                                    pantalla = "principal_ciego"
                                }
                            )

                        // Seleccionar una ruta
                        "seleccionar_ruta" ->
                            SeleccionarRutaScreen(
                                onVolver = {
                                    pantalla = "principal_ciego"
                                },
                                onSeleccionarRuta = { _, seguimientoId, nombreRuta ->
                                    seguimientoIdActual = seguimientoId
                                    // La guía arranca ya, para validar el rumbo con el MPU desde el
                                    // primer paso y no recién al abrir "Ruta en curso".
                                    SesionGuia.iniciar(contexto, seguimientoId)
                                    pantalla = "principal_ciego"
                                    hablar("Has iniciado la ruta $nombreRuta")
                                },
                                onAjustesClick = {
                                    pantalla = "ajustes_no_vidente"
                                },
                                onAsistenteVozClick = { iniciarAsistenteVoz() }
                            )

                        // Visualizar ruta en curso
                        "ruta_en_curso" ->
                            RutaEnCursoScreen(
                                seguimientoIdInicial = seguimientoIdActual,
                                onVolver = {
                                    pantalla = "principal_ciego"
                                },
                                onFinalizada = {
                                    seguimientoIdActual = null
                                    pantalla = "principal_ciego"
                                }
                            )

                        // Agregar rutas
                        "agregar_ruta" ->
                            AgregarRutaScreen(
                                onInicioClick = {
                                    pantalla = "principal_ciego"
                                },
                                onAjustesClick = {
                                    pantalla = "ajustes_no_vidente"
                                }
                            )

                        else -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Pantalla no disponible")
                        }
                    }
                }
            }
        }
    }
}
