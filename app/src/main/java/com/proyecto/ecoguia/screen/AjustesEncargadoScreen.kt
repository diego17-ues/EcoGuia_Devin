
package com.proyecto.ecoguia.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.proyecto.ecoguia.R
import com.proyecto.ecoguia.navigation.BottomNavItem
import com.proyecto.ecoguia.navigation.EcoGuiaNavigationBar

private val fondoAjustes = Color(0xFFEFF0F8)
private val fondoEncabezado = Color(0xFF19171F)
private val azulGuia = Color(0xFFC6D1F4)
private val textoAjustes = Color(0xFF19171F)
private val grisAjustes = Color(0xFF777783)

@Composable
fun AjustesEncargadoScreen(
    onInicioClick: () -> Unit
) {
    var guiaAbierta by remember {
        mutableStateOf(true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(fondoAjustes)
    ) {

        // Encabezado superior
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(69.dp)
                .background(fondoEncabezado),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Ajustes",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }

        // Contenido de la pantalla
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = 20.dp,
                    vertical = 20.dp
                )
        ) {

            // Tarjeta desplegable de Guía rápida
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(17.dp))
                    .background(Color.White)
            ) {

                // Encabezado azul de la guía
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(azulGuia)
                        .clickable {
                            guiaAbierta = !guiaAbierta
                        }
                        .padding(
                            horizontal = 14.dp,
                            vertical = 11.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    // Ícono de Guía rápida
                    Image(
                        painter = painterResource(
                            id = R.drawable.ic_guia_rapida
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        colorFilter = ColorFilter.tint(
                            textoAjustes
                        )
                    )

                    Spacer(Modifier.width(11.dp))

                    Text(
                        text = "Guía rápida",
                        modifier = Modifier.weight(1f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = textoAjustes
                    )

                    // Flecha para desplegar o contraer
                    Image(
                        painter = painterResource(
                            id = R.drawable.ic_expandir
                        ),
                        contentDescription =
                            if (guiaAbierta)
                                "Contraer guía"
                            else
                                "Desplegar guía",
                        modifier = Modifier
                            .size(26.dp)
                            .graphicsLayer {
                                rotationZ =
                                    if (guiaAbierta) 180f else 0f
                            },
                        colorFilter = ColorFilter.tint(
                            textoAjustes
                        )
                    )
                }

                // Contenido de la Guía rápida
                if (guiaAbierta) {

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 14.dp,
                                vertical = 13.dp
                            )
                    ) {

                        // Texto introductorio
                        Text(
                            text = "EcoGuía permite al encargado consultar " +
                                    "la ubicación de una persona no vidente " +
                                    "y establecer una vinculación entre ambas " +
                                    "cuentas. Estas funciones pueden utilizarse " +
                                    "desde la pantalla principal o mediante " +
                                    "el asistente de voz.",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = grisAjustes
                        )

                        Spacer(Modifier.height(17.dp))

                        // Función Rastrear
                        Text(
                            text = "1. Rastrear",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = textoAjustes
                        )

                        Spacer(Modifier.height(5.dp))

                        Text(
                            text = "Permite consultar la ubicación de " +
                                    "la persona no vidente vinculada, " +
                                    "para conocer dónde se encuentra " +
                                    "durante su recorrido.",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = grisAjustes
                        )

                        Spacer(Modifier.height(15.dp))

                        // Función Vincular con ciego
                        Text(
                            text = "2. Vincular con ciego",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = textoAjustes
                        )

                        Spacer(Modifier.height(5.dp))

                        Text(
                            text = "Permite ingresar el código " +
                                    "generado por la persona no vidente " +
                                    "para establecer la vinculación " +
                                    "entre ambas cuentas.",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = grisAjustes
                        )
                    }
                }
            }
        }

        // Barra inferior de navegación
        EcoGuiaNavigationBar(
            pantallaActual = BottomNavItem.AJUSTES,
            onInicioClick = onInicioClick,
            onAjustesClick = {}
        )
    }
}
