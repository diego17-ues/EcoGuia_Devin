
package com.proyecto.ecoguia.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proyecto.ecoguia.R
import com.proyecto.ecoguia.components.AsistenteVozCard
import com.proyecto.ecoguia.components.EcoGuiaHeader
import com.proyecto.ecoguia.data.repository.RutaActivaUsuario
import com.proyecto.ecoguia.data.repository.SeguimientoRepository
import com.proyecto.ecoguia.navigation.BottomNavItem
import com.proyecto.ecoguia.navigation.EcoGuiaNavigationBar
import kotlinx.coroutines.CancellationException
import java.util.Locale

private val fondo = Color(0xFFFAF9F7)
private val negro = Color(0xFF19171F)
private val gris = Color(0xFF777783)
private val verde = Color(0xFF12C98A)

@Composable
fun HomeNoVidenteScreen(
    onAjustesClick: () -> Unit = {},
    onElegirRutaClick: () -> Unit = {},
    onAgregarRutaClick: () -> Unit = {},
    onRutaActualClick: () -> Unit = {},
    onAsistenteVozClick: () -> Unit = {}
) {

    val seguimientoRepository = remember {
        SeguimientoRepository()
    }

    var rutaActual by remember {
        mutableStateOf<RutaActivaUsuario?>(null)
    }

    var cargandoRuta by remember {
        mutableStateOf(true)
    }

    var errorRuta by remember {
        mutableStateOf(false)
    }

    // Consultar la ruta activa al entrar a esta pantalla
    LaunchedEffect(Unit) {
        cargandoRuta = true
        errorRuta = false

        try {
            rutaActual =
                seguimientoRepository.obtenerRutaActivaUsuario()

        } catch (e: CancellationException) {
            throw e

        } catch (e: Exception) {
            android.util.Log.e(
                "ECOGUIA_RUTA_ACTUAL",
                "No se pudo consultar la ruta activa",
                e
            )

            errorRuta = true

        } finally {
            cargandoRuta = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(fondo)
    ) {

        // Encabezado compartido
        EcoGuiaHeader(rol = "no_vidente")

        // Contenido principal
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = 15.dp,
                    vertical = 14.dp
                )
        ) {

            // Asistente de voz
            AsistenteVozCard(
                onClick = onAsistenteVozClick
            )

            Spacer(Modifier.height(18.dp))

            // Acciones rápidas
            Text(
                text = "Acciones rápidas",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = negro
            )

            Text(
                text = "Elige una acción",
                fontSize = 12.sp,
                color = negro
            )

            Spacer(Modifier.height(13.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                TarjetaAccionHome(
                    titulo = "Elegir ruta",
                    icono = R.drawable.ic_mapa,
                    colorFondo = Color(0xFFFFE7B8),
                    onClick = onElegirRutaClick,
                    modifier = Modifier.weight(1f)
                )

                TarjetaAccionHome(
                    titulo = "Agregar ruta",
                    icono = R.drawable.ic_agregar,
                    colorFondo = Color(0xFFB8E8FF),
                    onClick = onAgregarRutaClick,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(13.dp))

            // Ruta actual
            Card(

                onClick = {
                    onRutaActualClick()
                },

                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 3.dp
                )
            ) {

                Column(
                    modifier = Modifier.padding(13.dp)
                ) {

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    Color(0xFFDDF6E9),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            ImagenHome(
                                id = R.drawable.ic_ruta_actual,
                                tamano = 23,
                                color = Color(0xFF228A4B)
                            )
                        }

                        Spacer(Modifier.width(10.dp))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {

                            Text(
                                text = "Ruta actual",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = negro
                            )

                            Text(
                                text = when {
                                    cargandoRuta ->
                                        "Consultando ruta..."

                                    errorRuta ->
                                        "No se pudo consultar la ruta"

                                    rutaActual == null ->
                                        "Aún no hay una ruta en curso"

                                    else ->
                                        "Hacia: ${rutaActual!!.nombreRuta}"
                                },
                                fontSize = 11.sp,
                                color = gris
                            )
                        }

                        if (
                            rutaActual != null &&
                            !cargandoRuta &&
                            !errorRuta
                        ) {
                            ImagenHome(
                                id = R.drawable.ic_flecha_derecha,
                                tamano = 23,
                                color = negro
                            )
                        }
                    }

                    // Mostrar detalles solo si existe una ruta activa
                    val ruta = rutaActual

                    if (
                        ruta != null &&
                        !cargandoRuta &&
                        !errorRuta
                    ) {

                        Spacer(Modifier.height(13.dp))

                        // Progreso real
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            val progreso =
                                ruta.progresoPorcentaje.coerceIn(0, 100)

                            LinearProgressIndicator(
                                progress = { progreso / 100f },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .clip(CircleShape),
                                color = verde,
                                trackColor = Color(0xFFE3E5E9)
                            )

                            Spacer(Modifier.width(9.dp))

                            Text(
                                text = "$progreso%",
                                fontSize = 11.sp,
                                color = gris
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.SpaceEvenly,
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            EstadoRutaHome(
                                icono = R.drawable.ic_bluetooth,
                                colorFondo = Color(0xFFFFEDCB),
                                titulo = "Conexión Bluetooth",
                                valor = "No verificada",
                                modifier = Modifier.weight(1f)
                            )

                            VerticalDivider(
                                modifier = Modifier.height(57.dp),
                                thickness = 1.dp,
                                color = Color(0xFFE7E7E7)
                            )

                            val distancia =
                                ruta.distanciaSiguienteNodoMetros

                            val textoDistancia =
                                if (distancia != null) {
                                    String.format(
                                        Locale.US,
                                        "%.0f metros",
                                        distancia
                                    )
                                } else {
                                    "No disponible"
                                }

                            EstadoRutaHome(
                                icono = R.drawable.ic_ubicacion,
                                colorFondo = Color(0xFFFCE0E1),
                                titulo = "Siguiente punto",
                                valor = textoDistancia,
                                modifier = Modifier.weight(1f)
                            )
                        }

                    } else if (
                        !cargandoRuta &&
                        !errorRuta
                    ) {

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = "Selecciona una ruta para comenzar tu recorrido.",
                            color = gris,
                            fontSize = 12.sp
                        )

                    } else if (errorRuta) {

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = "Comprueba tu conexión e ingresa nuevamente a esta pantalla.",
                            color = gris,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(15.dp))
        }

        // Barra inferior
        EcoGuiaNavigationBar(
            pantallaActual = BottomNavItem.INICIO,
            onInicioClick = {},
            onAjustesClick = onAjustesClick
        )
    }
}

// Tarjeta de acción
@Composable
private fun TarjetaAccionHome(
    titulo: String,
    icono: Int,
    colorFondo: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    Card(
        onClick = onClick,
        modifier = modifier.height(86.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 3.dp
        )
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colorFondo),
                    contentAlignment = Alignment.Center
                ) {
                    ImagenHome(
                        id = icono,
                        tamano = 23,
                        color = Color.Black
                    )
                }

                ImagenHome(
                    id = R.drawable.ic_flecha_derecha,
                    tamano = 20,
                    color = negro
                )
            }

            Text(
                text = titulo,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = negro
            )
        }
    }
}

// Estado de la ruta
@Composable
private fun EstadoRutaHome(
    icono: Int,
    colorFondo: Color,
    titulo: String,
    valor: String,
    modifier: Modifier = Modifier
) {

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colorFondo),
            contentAlignment = Alignment.Center
        ) {
            ImagenHome(
                id = icono,
                tamano = 19,
                color = Color.Black
            )
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = titulo,
            fontSize = 9.sp,
            color = gris,
            textAlign = TextAlign.Center
        )

        Text(
            text = valor,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = negro,
            textAlign = TextAlign.Center
        )
    }
}

// Iconos desde drawable
@Composable
private fun ImagenHome(
    id: Int,
    tamano: Int,
    color: Color
) {

    Image(
        painter = painterResource(id = id),
        contentDescription = null,
        modifier = Modifier.size(tamano.dp),
        colorFilter = ColorFilter.tint(color)
    )
}
