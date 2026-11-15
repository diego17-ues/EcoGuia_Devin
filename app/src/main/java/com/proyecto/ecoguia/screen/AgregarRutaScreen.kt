

package com.proyecto.ecoguia.screen

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proyecto.ecoguia.R
import com.proyecto.ecoguia.data.repository.BluetoothRepository
import com.proyecto.ecoguia.data.repository.CoordenadaCapturada
import com.proyecto.ecoguia.data.repository.DispositivoBluetooth
import com.proyecto.ecoguia.data.repository.NodoParaGuardar
import com.proyecto.ecoguia.data.repository.RutasRepository
import com.proyecto.ecoguia.data.repository.UbicacionRepository
import com.proyecto.ecoguia.navigation.BottomNavItem
import com.proyecto.ecoguia.navigation.EcoGuiaNavigationBar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

private val fondoRuta = Color(0xFFEFF0F8)
private val oscuroRuta = Color(0xFF19171F)
private val azulRuta = Color(0xFFC6D1F4)
private val lilaRuta = Color(0xFFE7E4F9)
private val moradoRuta = Color(0xFF5B66C5)
private val grisRuta = Color(0xFF777783)
private val verdeEstado = Color(0xFFC4F4D1)

private data class PuntoTemporal(
    val nombre: String,
    val tipo: String,
    val descripcion: String,
    val coordenada: CoordenadaCapturada
)

@Composable
fun AgregarRutaScreen(
    onInicioClick: () -> Unit,
    onAjustesClick: () -> Unit
) {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()

    val bluetooth = remember {
        BluetoothRepository(contexto.applicationContext)
    }

    val ubicacion = remember {
        UbicacionRepository(contexto.applicationContext)
    }

    val rutasRepository = remember {
        RutasRepository()
    }

    // Información de la ruta
    var nombreRuta by remember { mutableStateOf("") }
    var descripcionRuta by remember { mutableStateOf("") }

    // Lista de puntos
    val puntos = remember {
        mutableStateListOf<PuntoTemporal>()
    }

    // Bluetooth
    var conectado by remember { mutableStateOf(false) }
    var conectando by remember { mutableStateOf(false) }
    var dispositivoSeleccionado by remember {
        mutableStateOf("")
    }
    var dispositivos by remember {
        mutableStateOf<List<DispositivoBluetooth>>(emptyList())
    }
    var mostrarDispositivos by remember {
        mutableStateOf(false)
    }

    // GPS
    var capturando by remember { mutableStateOf(false) }
    var gpsVerificado by remember {
        mutableStateOf(false)
    }
    var coordenadaActual by remember {
        mutableStateOf<CoordenadaCapturada?>(null)
    }

    // Datos del punto
    var nombre by remember { mutableStateOf("") }
    var tipo by remember { mutableStateOf("Inicio") }
    var descripcion by remember { mutableStateOf("") }
    var mostrarTipos by remember {
        mutableStateOf(false)
    }

    // Confirmaciones
    var finalizando by remember {
        mutableStateOf(false)
    }
    var confirmarSalida by remember {
        mutableStateOf(false)
    }
    var destinoSalida by remember {
        mutableStateOf("inicio")
    }
    var confirmarFinalizar by remember {
        mutableStateOf(false)
    }
    var resultadoGuardado by remember {
        mutableStateOf("")
    }
    var avisoTitulo by remember {
        mutableStateOf("")
    }
    var avisoMensaje by remember {
        mutableStateOf("")
    }

    fun avisar(titulo: String, mensaje: String) {
        avisoTitulo = titulo
        avisoMensaje = mensaje
    }

    fun limpiarRuta() {
        puntos.clear()
        nombreRuta = ""
        descripcionRuta = ""
        coordenadaActual = null
        nombre = ""
        tipo = "Inicio"
        descripcion = ""
    }

    fun solicitarSalida(destino: String) {
        if (finalizando) return

        destinoSalida = destino

        val hayCambios =
            puntos.isNotEmpty() ||
                    nombreRuta.isNotBlank() ||
                    descripcionRuta.isNotBlank() ||
                    coordenadaActual != null ||
                    nombre.isNotBlank() ||
                    descripcion.isNotBlank()

        if (hayCambios) {
            confirmarSalida = true
        } else {
            if (destino == "ajustes") {
                onAjustesClick()
            } else {
                onInicioClick()
            }
        }
    }

    // Dispositivos Bluetooth emparejados
    fun cargarEmparejados() {
        try {
            dispositivos = bluetooth.obtenerEmparejados()
            mostrarDispositivos = true
        } catch (e: Exception) {
            avisar(
                "Bluetooth",
                e.localizedMessage
                    ?: "No se pudo consultar Bluetooth."
            )
        }
    }

    // Permiso Bluetooth
    val permisoBluetooth =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { autorizado ->
            if (autorizado) {
                cargarEmparejados()
            } else {
                avisar(
                    "Permiso requerido",
                    "Autoriza Bluetooth para conectar el dispositivo."
                )
            }
        }

    // Permiso GPS
    val permisoUbicacion =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permisos ->
            if (
                permisos[Manifest.permission.ACCESS_FINE_LOCATION]
                != true
            ) {
                gpsVerificado = false

                avisar(
                    "Ubicación precisa",
                    "Autoriza la ubicación precisa para capturar los puntos."
                )
            }
        }

    // Desconectar al salir de la pantalla
    DisposableEffect(bluetooth) {
        onDispose {
            bluetooth.desconectar()
        }
    }

    BackHandler(enabled = !finalizando) {
        solicitarSalida("inicio")
    }

    // Avisos generales
    if (avisoMensaje.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = {
                avisoMensaje = ""
            },
            title = {
                Text(
                    avisoTitulo,
                    fontWeight = FontWeight.Bold,
                    color = oscuroRuta
                )
            },
            text = {
                Text(
                    avisoMensaje,
                    color = oscuroRuta
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { avisoMensaje = "" }
                ) {
                    Text(
                        "Aceptar",
                        color = moradoRuta
                    )
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Confirmar salida
    if (confirmarSalida) {
        AlertDialog(
            onDismissRequest = {
                confirmarSalida = false
            },
            title = {
                Text("¿Descartar ruta?")
            },
            text = {
                Text(
                    "Si cancelas, perderás todos los puntos " +
                            "agregados a esta ruta. " +
                            "¿Deseas continuar?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmarSalida = false
                        limpiarRuta()

                        if (destinoSalida == "ajustes") {
                            onAjustesClick()
                        } else {
                            onInicioClick()
                        }
                    }
                ) {
                    Text(
                        "Sí, descartar",
                        color = Color(0xFFB42318)
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmarSalida = false
                    }
                ) {
                    Text(
                        "Continuar editando",
                        color = moradoRuta
                    )
                }
            },
            containerColor = Color.White
        )
    }

    // Confirmar guardado de ruta
    if (confirmarFinalizar) {
        AlertDialog(
            onDismissRequest = {
                confirmarFinalizar = false
            },
            title = {
                Text("¿Finalizar ruta?")
            },
            text = {
                Text(
                    "Se guardará la ruta \"$nombreRuta\" " +
                            "con ${puntos.size} puntos. " +
                            "¿Deseas finalizarla?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmarFinalizar = false
                        finalizando = true

                        val nodosParaEnviar =
                            puntos.map { punto ->
                                NodoParaGuardar(
                                    nombre = punto.nombre,
                                    tipo = punto.tipo,
                                    descripcion = punto.descripcion,
                                    latitud =
                                        punto.coordenada.latitud,
                                    longitud =
                                        punto.coordenada.longitud
                                )
                            }

                        val nombreParaEnviar =
                            nombreRuta.trim()

                        val descripcionParaEnviar =
                            descripcionRuta.trim()

                        scope.launch {
                            try {
                                val resultado =
                                    rutasRepository.finalizarRuta(
                                        nombreRuta =
                                            nombreParaEnviar,
                                        descripcionRuta =
                                            descripcionParaEnviar,
                                        nodos = nodosParaEnviar
                                    )

                                resultadoGuardado =
                                    "La ruta se guardó correctamente " +
                                            "con ${resultado.total_nodos} puntos."

                            } catch (
                                e: CancellationException
                            ) {
                                throw e
                            } catch (e: Exception) {
                                android.util.Log.e(
                                    "ECOGUIA_RUTAS",
                                    "Error al guardar la ruta",
                                    e
                                )
                                avisar(
                                    "No se pudo confirmar el guardado",
                                    "Ocurrió un error al guardar la ruta. " +
                                            "Los puntos permanecerán en la lista. " +
                                            "Revisa Logcat para conocer el motivo."
                                )
                            } finally {
                                finalizando = false
                            }
                        }
                    }
                ) {
                    Text(
                        "Sí, guardar",
                        color = moradoRuta
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmarFinalizar = false
                    }
                ) {
                    Text("Volver")
                }
            },
            containerColor = Color.White
        )
    }

    // Ruta guardada correctamente
    if (resultadoGuardado.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text("Ruta guardada")
            },
            text = {
                Text(resultadoGuardado)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        resultadoGuardado = ""
                        limpiarRuta()
                        onInicioClick()
                    }
                ) {
                    Text(
                        "Aceptar",
                        color = moradoRuta
                    )
                }
            },
            containerColor = Color.White
        )
    }

    // Seleccionar ESP32
    if (mostrarDispositivos) {
        AlertDialog(
            onDismissRequest = {
                mostrarDispositivos = false
            },
            title = {
                Text("Seleccionar ESP32")
            },
            text = {
                Column {
                    if (dispositivos.isEmpty()) {
                        Text(
                            "No hay dispositivos emparejados. " +
                                    "Primero empareja el ESP32 desde " +
                                    "los ajustes Bluetooth de tu teléfono."
                        )
                    } else {
                        dispositivos.forEach { dispositivo ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        mostrarDispositivos = false
                                        conectando = true

                                        scope.launch {
                                            try {
                                                bluetooth.conectar(
                                                    dispositivo.direccion
                                                )

                                                conectado = true
                                                dispositivoSeleccionado =
                                                    dispositivo.nombre

                                            } catch (
                                                e: CancellationException
                                            ) {
                                                throw e
                                            } catch (e: Exception) {
                                                conectado = false
                                                avisar(
                                                    "Conexión fallida",
                                                    e.localizedMessage
                                                        ?: "No se pudo conectar."
                                                )
                                            } finally {
                                                conectando = false
                                            }
                                        }
                                    }
                                    .padding(vertical = 12.dp)
                            ) {
                                Text(
                                    dispositivo.nombre,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    dispositivo.direccion,
                                    fontSize = 11.sp,
                                    color = grisRuta
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        mostrarDispositivos = false
                    }
                ) {
                    Text("Cerrar")
                }
            },
            containerColor = Color.White
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(fondoRuta)
    ) {

        // Encabezado
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(69.dp)
                .background(oscuroRuta)
        ) {
            Text(
                text = "Agregar ruta",
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
                        enabled = !finalizando
                    ) {
                        solicitarSalida("inicio")
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(
                        R.drawable.ic_volver
                    ),
                    contentDescription = "Volver al inicio",
                    modifier = Modifier.size(26.dp),
                    colorFilter = ColorFilter.tint(Color.White)
                )
            }
        }

        // Contenido desplazable
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement =
                Arrangement.spacedBy(15.dp)
        ) {

            Text(
                text = "Crea una nueva ruta",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = oscuroRuta
            )

            Text(
                text = "Registra los puntos importantes de tu recorrido.",
                fontSize = 13.sp,
                color = grisRuta
            )

            // Estado del ESP32 y GPS
            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {

                TarjetaEstadoRuta(
                    titulo = "Dispositivo",
                    subtitulo = when {
                        conectando -> "Conectando..."
                        conectado -> "Conectado"
                        else -> "Desconectado"
                    },
                    icono = R.drawable.ic_bluetooth,
                    activo = conectado,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (conectando) {
                            return@TarjetaEstadoRuta
                        }

                        if (conectado) {
                            bluetooth.desconectar()
                            conectado = false
                            dispositivoSeleccionado = ""

                        } else if (
                            Build.VERSION.SDK_INT >=
                            Build.VERSION_CODES.S &&
                            !bluetooth.tienePermiso()
                        ) {
                            permisoBluetooth.launch(
                                Manifest.permission.BLUETOOTH_CONNECT
                            )
                        } else {
                            cargarEmparejados()
                        }
                    }
                )

                TarjetaEstadoRuta(
                    titulo = "GPS",
                    subtitulo = when {
                        capturando -> "Verificando..."
                        gpsVerificado -> "Señal válida"
                        else -> "Sin verificar"
                    },
                    icono = R.drawable.ic_ubicacion,
                    activo = gpsVerificado && !capturando,
                    centrarContenido = true,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (!ubicacion.tienePermisoPreciso()) {
                            permisoUbicacion.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        } else {
                            avisar(
                                "GPS",
                                if (gpsVerificado) {
                                    "Se obtuvo una ubicación correctamente."
                                } else {
                                    "Presiona Capturar punto para comprobar la ubicación."
                                }
                            )
                        }
                    }
                )
            }

            if (conectado) {
                Text(
                    text = "Dispositivo: $dispositivoSeleccionado",
                    fontSize = 12.sp,
                    color = moradoRuta
                )
            }

            // Información general de la ruta
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(17.dp))
                    .background(Color.White)
                    .padding(15.dp),
                verticalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {

                Text(
                    text = "Información de la ruta",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = oscuroRuta
                )

                OutlinedTextField(
                    value = nombreRuta,
                    onValueChange = {
                        nombreRuta = it
                    },
                    enabled = !finalizando,
                    label = {
                        Text("Nombre de la ruta")
                    },
                    placeholder = {
                        Text("Ej. Entrada UES a Biblioteca")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = descripcionRuta,
                    onValueChange = {
                        descripcionRuta = it
                    },
                    enabled = !finalizando,
                    label = {
                        Text("Descripción de la ruta")
                    },
                    placeholder = {
                        Text("Opcional")
                    },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            // Captura del punto
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(17.dp))
                    .background(Color.White)
                    .border(
                        width = 1.dp,
                        color = Color(0xFFB8A6E7),
                        shape = RoundedCornerShape(17.dp)
                    )
                    .padding(15.dp),
                verticalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {

                Text(
                    text = "Captura del punto",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = oscuroRuta
                )

                // Capturar punto:
                // primero requiere ESP32 conectado.
                // La ubicación viene del GPS del teléfono.
                Button(
                    onClick = {


                        if (
                            !ubicacion.tienePermisoPreciso()
                        ) {

                            permisoUbicacion.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )

                        } else {
                            capturando = true
                            gpsVerificado = false
                            coordenadaActual = null

                            scope.launch {
                                try {
                                    coordenadaActual =
                                        ubicacion.capturarCoordenada()

                                    gpsVerificado = true

                                } catch (
                                    e: CancellationException
                                ) {
                                    throw e
                                } catch (e: Exception) {
                                    gpsVerificado = false

                                    avisar(
                                        "No se pudo capturar",
                                        e.localizedMessage
                                            ?: "Comprueba la ubicación."
                                    )
                                } finally {
                                    capturando = false
                                }
                            }
                        }
                    },
                    enabled = !capturando && !finalizando,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = azulRuta
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {

                    if (capturando) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = oscuroRuta
                        )
                    } else {
                        Text(
                            text = "Capturar punto",
                            color = oscuroRuta,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Coordenadas GPS
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFDFDFF))
                        .border(
                            width = 1.dp,
                            color = Color(0xFFB8A6E7),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(13.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(12.dp)
                ) {

                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically,
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {
                        Image(
                            painter = painterResource(
                                R.drawable.ic_ubicacion
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(21.dp),
                            colorFilter =
                                ColorFilter.tint(moradoRuta)
                        )

                        Text(
                            text = "Datos capturados por GPS",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = oscuroRuta
                        )
                    }

                    val punto = coordenadaActual

                    CampoDatoGPS(
                        titulo = "Latitud",
                        valor = punto?.let {
                            String.format(
                                Locale.US,
                                "%.6f",
                                it.latitud
                            )
                        } ?: "Sin capturar"
                    )

                    CampoDatoGPS(
                        titulo = "Longitud",
                        valor = punto?.let {
                            String.format(
                                Locale.US,
                                "%.6f",
                                it.longitud
                            )
                        } ?: "Sin capturar"
                    )

                    // Advertencia de precisión GPS
                    val precision = punto?.precisionMetros

                    if (
                        precision != null &&
                        precision > 5f
                    ) {
                        Text(
                            text = if (precision <= 10f) {
                                "Se recomienda comprobar el punto."
                            } else {
                                "Precisión baja. Se recomienda volver a capturar el punto."
                            },
                            fontSize = 11.sp,
                            color = Color(0xFFB45309)
                        )
                    }
                }

                // Nombre del punto
                OutlinedTextField(
                    value = nombre,
                    onValueChange = {
                        nombre = it
                    },
                    enabled = !finalizando,
                    label = {
                        Text("Nombre del punto")
                    },
                    placeholder = {
                        Text("Ej. Entrada principal")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Tipo de punto
                ComboBoxTipoPunto(
                    tipoSeleccionado = tipo,
                    expandido = mostrarTipos,
                    habilitado = !finalizando,
                    onExpandidoChange = {
                        mostrarTipos = it
                    },
                    onTipoSeleccionado = {
                        tipo = it
                        mostrarTipos = false
                    }
                )

                // Descripción
                OutlinedTextField(
                    value = descripcion,
                    onValueChange = {
                        descripcion = it
                    },
                    enabled = !finalizando,
                    label = {
                        Text("Descripción")
                    },
                    placeholder = {
                        Text("Describe el lugar")
                    },
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(
                        capitalization =
                            KeyboardCapitalization.Sentences
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Guardar punto en lista temporal:
                // requiere Bluetooth conectado.
                Button(
                    onClick = {

                        val punto = coordenadaActual


                        when {
                            punto == null -> {

                                avisar(
                                    "Ubicación pendiente",
                                    "Primero captura una coordenada."
                                )
                            }

                            nombre.trim().length !in 2..150 -> {
                                avisar(
                                    "Nombre del punto",
                                    "El nombre debe tener entre 2 y 150 caracteres."
                                )
                            }

                            descripcion.length > 1000 -> {
                                avisar(
                                    "Descripción",
                                    "La descripción es demasiado larga."
                                )
                            }

                            puntos.size >= 100 -> {
                                avisar(
                                    "Límite de puntos",
                                    "Esta ruta puede tener hasta 100 puntos."
                                )
                            }

                            else -> {
                                puntos.add(
                                    PuntoTemporal(
                                        nombre = nombre.trim(),
                                        tipo = tipo,
                                        descripcion =
                                            descripcion.trim(),
                                        coordenada = punto
                                    )
                                )

                                coordenadaActual = null
                                nombre = ""
                                tipo = "Inicio"
                                descripcion = ""

                                avisar(
                                    "Punto agregado",
                                    "El punto se agregó a la lista."
                                )
                            }
                        }
                    },
                    enabled = !capturando && !finalizando,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = moradoRuta
                    )
                ) {
                    Text(
                        text = "Guardar punto",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Lista de puntos de la ruta
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(17.dp))
                    .background(Color.White)
                    .padding(15.dp),
                verticalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {

                Text(
                    text = "Puntos de la ruta (${puntos.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = oscuroRuta
                )

                if (puntos.isEmpty()) {
                    Text(
                        text = "Todavía no has agregado puntos.",
                        fontSize = 13.sp,
                        color = grisRuta
                    )
                }

                puntos.forEachIndexed { indice, punto ->

                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement =
                                Arrangement.spacedBy(4.dp)
                        ) {

                            Text(
                                text = "${indice + 1}. ${punto.nombre}",
                                fontWeight =
                                    FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = oscuroRuta
                            )

                            Text(
                                text = punto.tipo,
                                fontSize = 11.sp,
                                color = moradoRuta
                            )

                            Text(
                                text = String.format(
                                    Locale.US,
                                    "%.6f, %.6f",
                                    punto.coordenada.latitud,
                                    punto.coordenada.longitud
                                ),
                                fontSize = 11.sp,
                                color = grisRuta
                            )

                            if (punto.descripcion.isNotBlank()) {
                                Text(
                                    text = punto.descripcion,
                                    fontSize = 11.sp,
                                    color = grisRuta
                                )
                            }
                        }

                        TextButton(
                            enabled = !finalizando,
                            onClick = {
                                puntos.removeAt(indice)
                            }
                        ) {
                            Text(
                                "Quitar",
                                color = moradoRuta
                            )
                        }
                    }

                    HorizontalDivider(
                        color = Color(0xFFE8E5EF)
                    )
                }

                if (puntos.isNotEmpty()) {
                    Text(
                        text = "Puedes quitar puntos de la lista " +
                                "antes de finalizar la ruta.",
                        color = grisRuta,
                        fontSize = 11.sp
                    )
                }
            }

            // Finalizar ruta
            Button(
                onClick = {
                    when {
                        nombreRuta.trim().length !in 2..150 -> {
                            avisar(
                                "Nombre de la ruta",
                                "Ingresa un nombre de ruta " +
                                        "entre 2 y 150 caracteres."
                            )
                        }

                        descripcionRuta.length > 1000 -> {
                            avisar(
                                "Descripción de la ruta",
                                "La descripción es demasiado larga."
                            )
                        }

                        puntos.size < 2 -> {
                            avisar(
                                "Puntos insuficientes",
                                "Debes agregar al menos dos puntos " +
                                        "para finalizar una ruta."
                            )
                        }

                        else -> {
                            confirmarFinalizar = true
                        }
                    }
                },
                enabled = !finalizando && !capturando,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFC21C)
                )
            ) {

                if (finalizando) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = oscuroRuta
                    )

                    Spacer(Modifier.width(10.dp))

                    Text(
                        text = "Guardando ruta...",
                        color = oscuroRuta
                    )
                } else {
                    Text(
                        text = "Finalizar ruta",
                        fontWeight = FontWeight.Bold,
                        color = oscuroRuta
                    )
                }
            }

            // Cancelar
            OutlinedButton(
                onClick = {
                    solicitarSalida("inicio")
                },
                enabled = !finalizando,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Cancelar",
                    color = Color(0xFFB42318),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Barra inferior
        EcoGuiaNavigationBar(
            pantallaActual = BottomNavItem.INICIO,
            onInicioClick = {
                solicitarSalida("inicio")
            },
            onAjustesClick = {
                solicitarSalida("ajustes")
            }
        )
    }
}

// Tarjetas de estado Bluetooth y GPS
@Composable
private fun TarjetaEstadoRuta(
    titulo: String,
    subtitulo: String,
    icono: Int,
    activo: Boolean,
    modifier: Modifier = Modifier,
    centrarContenido: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(lilaRuta)
            .clickable(onClick = onClick)
            .padding(
                horizontal = 10.dp,
                vertical = 13.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Image(
            painter = painterResource(icono),
            contentDescription = null,
            modifier = Modifier.size(27.dp),
            colorFilter = ColorFilter.tint(oscuroRuta)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement =
                Arrangement.spacedBy(7.dp),
            horizontalAlignment =
                if (centrarContenido) {
                    Alignment.CenterHorizontally
                } else {
                    Alignment.Start
                }
        ) {
            Text(
                text = titulo,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = oscuroRuta,
                maxLines = 1
            )

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (activo) {
                            verdeEstado
                        } else {
                            Color(0xFFE0E0EB)
                        }
                    )
                    .padding(
                        horizontal = 8.dp,
                        vertical = 4.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = subtitulo,
                    fontSize = 10.sp,
                    color = oscuroRuta,
                    maxLines = 1
                )
            }
        }
    }
}

// Opciones del tipo de punto
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComboBoxTipoPunto(
    tipoSeleccionado: String,
    expandido: Boolean,
    habilitado: Boolean,
    onExpandidoChange: (Boolean) -> Unit,
    onTipoSeleccionado: (String) -> Unit
) {
    val opciones = listOf(
        "Inicio",
        "Destino",
        "Giro a la izquierda",
        "Giro a la derecha"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement =
            Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = "Tipo de punto",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = oscuroRuta
        )

        ExposedDropdownMenuBox(
            expanded = expandido,
            onExpandedChange = {
                if (habilitado) {
                    onExpandidoChange(it)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = tipoSeleccionado,
                onValueChange = {},
                readOnly = true,
                enabled = habilitado,
                singleLine = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(
                        expanded = expandido
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                shape = RoundedCornerShape(10.dp),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = moradoRuta,
                        unfocusedBorderColor =
                            Color(0xFFD1D5E4),
                        focusedContainerColor =
                            Color.White,
                        unfocusedContainerColor =
                            Color.White
                    )
            )

            ExposedDropdownMenu(
                expanded = expandido,
                onDismissRequest = {
                    onExpandidoChange(false)
                }
            ) {
                opciones.forEach { opcion ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = opcion,
                                fontSize = 14.sp,
                                color = oscuroRuta
                            )
                        },
                        onClick = {
                            onTipoSeleccionado(opcion)
                        }
                    )
                }
            }
        }
    }
}

// Mostrar latitud y longitud
@Composable
private fun CampoDatoGPS(
    titulo: String,
    valor: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement =
            Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = titulo,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = grisRuta
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF3F3F8))
                .padding(
                    horizontal = 12.dp,
                    vertical = 11.dp
                )
        ) {
            Text(
                text = valor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = oscuroRuta
            )
        }
    }
}