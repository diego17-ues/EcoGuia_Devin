package com.proyecto.ecoguia.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

// Colores de EcoGuía
private val fondo = Color(0xFFAFBCE9)
private val amarillo = Color(0xFFFFC21C)
private val texto = Color(0xFF19192D)
private val borde = Color(0xFF5269E9)
private val gris = Color(0xFF777783)

@Composable
fun SeleccionRolScreen(
    onNoVidenteClick: () -> Unit,
    onEncargadoClick: () -> Unit,
    onCrearCuentaClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(fondo),
        contentAlignment = Alignment.Center
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 30.dp, vertical = 35.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // LOGO ECOGUÍA
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(amarillo, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "EcoGuía",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.Black
                )
            }

            Spacer(Modifier.height(22.dp))

            // TÍTULO
            Text(
                text = "¿Cómo deseas\ningresar?",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 33.sp,
                textAlign = TextAlign.Center,
                color = texto
            )

            Spacer(Modifier.height(38.dp))

            // PERSONA NO VIDENTE
            TarjetaRol(
                titulo = "Persona no vidente",
                subtitulo = "Acceder con mi huella",
                imagen = R.drawable.ic_huella,
                onClick = onNoVidenteClick
            )

            Spacer(Modifier.height(26.dp))

            // ENCARGADO
            TarjetaRol(
                titulo = "Encargado del vidente",
                subtitulo = "Acceder con credenciales",
                imagen = R.drawable.ic_persona,
                onClick = onEncargadoClick
            )

            Spacer(Modifier.height(58.dp))

            // BOTÓN CREAR NUEVA CUENTA
            Row(
                modifier = Modifier
                    .height(54.dp)
                    .border(
                        width = 2.dp,
                        color = borde,
                        shape = RoundedCornerShape(15.dp)
                    )
                    .clip(RoundedCornerShape(15.dp))
                    .background(Color.White)
                    .clickable { onCrearCuentaClick() }
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(
                    text = "Crear nueva cuenta",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = texto
                )

                Image(
                    painter = painterResource(
                        id = R.drawable.ic_flecha_derecha
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    colorFilter = ColorFilter.tint(texto)
                )
            }
        }
    }
}


// =====================================
// TARJETAS DE SELECCIÓN
// =====================================

@Composable
private fun TarjetaRol(
    titulo: String,
    subtitulo: String,
    imagen: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .clickable { onClick() }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        // ÍCONO DESDE DRAWABLE
        Image(
            painter = painterResource(id = imagen),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            colorFilter = ColorFilter.tint(Color.Black)
        )

        Spacer(Modifier.width(16.dp))

        // TEXTOS
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = titulo,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = texto,
                lineHeight = 19.sp
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = subtitulo,
                fontSize = 11.sp,
                color = gris,
                lineHeight = 15.sp
            )
        }

        // FLECHA
        Image(
            painter = painterResource(
                id = R.drawable.ic_flecha_derecha
            ),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(texto)
        )
    }
}
