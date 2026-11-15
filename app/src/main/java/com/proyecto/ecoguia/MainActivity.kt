
package com.proyecto.ecoguia

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.view.HapticFeedbackConstants

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
import com.proyecto.ecoguia.data.repository.RutasRepository
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

                var rutaIniciadaNombre by remember {
                    mutableStateOf<String?>(null)
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

                var mensajeVozPendiente by remember {
                    mutableStateOf<String?>(null)
                }

                var rutasDisponiblesVoz by remember {
                    mutableStateOf<List<String>>(emptyList())
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

                fun hablar(mensaje: String, agregarACola: Boolean = false) {

                    val motor = motorVoz

                    if (motor != null && vozPreparada) {

                        motor.language = Locale("es", "SV")

                        motor.speak(
                            mensaje,
                            if (agregarACola) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH,
                            null,
                            "ecoguia_navegacion"
                        )

                    } else {
                        mensajeVozPendiente = if (agregarACola) {
                            listOfNotNull(mensajeVozPendiente, mensaje)
                                .joinToString(" ")
                        } else {
                            mensaje
                        }
                    }
                }

                LaunchedEffect(vozPreparada, mensajeVozPendiente) {
                    val mensaje = mensajeVozPendiente
                    val motor = motorVoz
                    if (vozPreparada && motor != null && mensaje != null) {
                        mensajeVozPendiente = null
                        motor.language = Locale("es", "SV")
                        motor.speak(
                            mensaje,
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            "ecoguia_navegacion_pendiente"
                        )
                    }
                }

                LaunchedEffect(pantalla) {
                    val pantallaAnunciada = pantalla
                    val pantallasNoVidente = setOf(
                        "principal_ciego",
                        "seleccionar_ruta",
                        "agregar_ruta",
                        "ruta_en_curso",
                        "ajustes_no_vidente"
                    )
                    val anuncioPantalla = when (pantallaAnunciada) {
                        "principal_ciego" ->
                            "${rutaIniciadaNombre?.let { "Has iniciado la ruta $it. " }.orEmpty()}Inicio de persona no vidente. Puedes seleccionar una ruta, agregar una ruta, consultar la ruta actual o abrir ajustes. Toca el asistente de voz y di qué deseas hacer."
                        "seleccionar_ruta" ->
                            "Selección de ruta. Puedes escuchar las rutas disponibles, elegir una, revisar su mapa, distancia y tiempo estimado, y confirmar el inicio cuando estés en el primer punto. También puedes volver al inicio o abrir ajustes."
                        "agregar_ruta" ->
                            "Agregar ruta. Puedes escribir el nombre y la descripción, capturar puntos con el GPS, revisar los puntos agregados y guardar la ruta. También puedes volver al inicio o abrir ajustes."
                        "ruta_en_curso" ->
                            "Ruta en curso. Puedes consultar el avance, el último punto alcanzado, el siguiente punto y el estado del dispositivo EcoGuía. También puedes reintentar la guía, finalizar o cancelar el recorrido."
                        "ajustes_no_vidente" ->
                            "Ajustes de persona no vidente. Puedes escuchar la guía rápida, configurar los avisos sonoros, vincular a tu encargado y volver al inicio."
                        "principal_encargado" ->
                            "Inicio de encargado. Puedes rastrear a una persona vinculada, vincular a una persona no vidente o abrir ajustes."
                        "vincular_encargado" ->
                            "Vincular persona no vidente. Puedes generar o ingresar un código de vinculación, consultar el estado y volver al inicio."
                        "seguimiento" ->
                            "Rastreo. Puedes ver la ubicación y el recorrido observado de las personas vinculadas, consultar la ruta y su hora estimada de llegada, recargar la búsqueda si no aparece nadie y enviar ayuda."
                        "ajustes_encargado" ->
                            "Ajustes de encargado. Puedes revisar las opciones de cuenta y ubicación compartida, y volver al inicio."
                        else -> null
                    }

                    if (anuncioPantalla != null) {
                        hablar(anuncioPantalla)
                    }

                    if (pantallaAnunciada in pantallasNoVidente) {
                        val resultadoRutas = runCatching {
                            RutasRepository().listarNombresRutasDisponibles()
                        }

                        if (pantalla == pantallaAnunciada) {
                            rutasDisponiblesVoz = resultadoRutas.getOrDefault(emptyList())
                            val avisoRutas = resultadoRutas.fold(
                                onSuccess = { nombresRutas ->
                                    if (nombresRutas.isEmpty()) {
                                        "No tienes rutas guardadas disponibles."
                                    } else {
                                        "Rutas disponibles: ${nombresRutas.joinToString(", ")}."
                                    }
                                },
                                onFailure = {
                                    "No se pudieron consultar las rutas en este momento."
                                }
                            )
                            hablar(avisoRutas, agregarACola = true)
                        }
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
                            val esNoVidente = pantalla in setOf(
                                "principal_ciego",
                                "seleccionar_ruta",
                                "agregar_ruta",
                                "ruta_en_curso",
                                "ajustes_no_vidente"
                            )

                            when {
                                frase.contains("ayuda") ||
                                        frase.contains("que puedo decir") ||
                                        frase.contains("que puedes hacer") ||
                                        frase.contains("comandos") -> {
                                    hablar(
                                        if (esEncargado)
                                            "Puedes decir: rastrear, vincular con no vidente, abrir ajustes, o volver."
                                        else {
                                            val rutas = if (rutasDisponiblesVoz.isEmpty()) {
                                                "No tienes rutas guardadas disponibles."
                                            } else {
                                                "Rutas disponibles: ${rutasDisponiblesVoz.joinToString(", ")}."
                                            }
                                            "Puedes decir: elegir ruta, agregar ruta, ruta actual, abrir ajustes, ir al inicio o volver. $rutas"
                                        }
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
                                    rutaIniciadaNombre = nombreRuta
                                    // La guía arranca ya, para validar el rumbo con el MPU desde el
                                    // primer paso y no recién al abrir "Ruta en curso".
                                    SesionGuia.iniciar(contexto, seguimientoId)
                                    pantalla = "principal_ciego"
                                },
                                onAjustesClick = {
                                    pantalla = "ajustes_no_vidente"
                                },
                                onAsistenteVozClick = { iniciarAsistenteVoz() },
                                onMensajeVoz = { mensaje -> hablar(mensaje) }
                            )

                        // Visualizar ruta en curso
                        "ruta_en_curso" ->
                            RutaEnCursoScreen(
                                seguimientoIdInicial = seguimientoIdActual,
                                onMensajeVoz = { mensaje -> hablar(mensaje, agregarACola = true) },
                                onVolver = {
                                    pantalla = "principal_ciego"
                                },
                                onFinalizada = {
                                    seguimientoIdActual = null
                                    rutaIniciadaNombre = null
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
