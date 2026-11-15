
package com.proyecto.ecoguia.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proyecto.ecoguia.R
import com.proyecto.ecoguia.components.AsistenteVozCard
import com.proyecto.ecoguia.components.EcoGuiaHeader
import com.proyecto.ecoguia.navigation.BottomNavItem
import com.proyecto.ecoguia.navigation.EcoGuiaNavigationBar

private val fondo = Color(0xFFFAF9F7)
private val negro = Color(0xFF19171F)

@Composable
fun HomeEncargadoScreen(
    onRastrearClick: () -> Unit = {},
    onVincularClick: () -> Unit = {},
    onAjustesClick: () -> Unit = {},
    onAsistenteVozClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(fondo)
    ) {

        // ENCABEZADO COMPARTIDO
        EcoGuiaHeader(rol = "encargado")

        // CONTENIDO PRINCIPAL
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

            // ASISTENTE DE VOZ COMPARTIDO
            AsistenteVozCard(onClick = onAsistenteVozClick)

            Spacer(Modifier.height(21.dp))

            // ACCIONES RAPIDAS
            Text(
                text = "Acciones rápidas",
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = negro
            )

            Spacer(Modifier.height(3.dp))

            Text(
                text = "Selecciona",
                fontSize = 13.sp,
                color = negro
            )

            Spacer(Modifier.height(20.dp))

            // RASTREAR
            TarjetaEncargado(
                titulo = "Rastrear",
                icono = R.drawable.ic_rastrear,
                colorIcono = Color(0xFFEAD5FC),
                onClick = onRastrearClick
            )

            Spacer(Modifier.height(26.dp))

            // VINCULAR CON CIEGO
            TarjetaEncargado(

                titulo = "Vincular con no vidente",
                icono = R.drawable.ic_vincular,
                colorIcono = Color(0xFFB1E5FF),
                onClick = onVincularClick
            )

            Spacer(Modifier.height(20.dp))
        }

        // BARRA INFERIOR COMPARTIDA
        EcoGuiaNavigationBar(
            pantallaActual = BottomNavItem.INICIO,
            onInicioClick = {},
            onAjustesClick = onAjustesClick
        )
    }
}


// TARJETAS DEL ENCARGADO
@Composable
private fun TarjetaEncargado(
    titulo: String,
    icono: Int,
    colorIcono: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(155.dp),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 5.dp
        )
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(17.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {

                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colorIcono),
                    contentAlignment = Alignment.Center
                ) {
                    ImagenEncargado(
                        icono = icono,
                        tamano = 34.dp
                    )
                }

                // FLECHA DE 30 DP, BAJADA 5 DP
                Box(
                    modifier = Modifier.offset(y = 5.dp)
                ) {
                    ImagenEncargado(
                        icono = R.drawable.ic_flecha_derecha,
                        tamano = 30.dp
                    )
                }
            }

            // TITULO ELEVADO 8 DP
            Text(
                text = titulo,
                modifier = Modifier.offset(y = (-8).dp),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = negro
            )
        }
    }
}


// ICONOS DESDE DRAWABLE
@Composable
private fun ImagenEncargado(
    icono: Int,
    tamano: Dp
) {
    Image(
        painter = painterResource(id = icono),
        contentDescription = null,
        modifier = Modifier.size(tamano),
        colorFilter = ColorFilter.tint(Color.Black)
    )
}
