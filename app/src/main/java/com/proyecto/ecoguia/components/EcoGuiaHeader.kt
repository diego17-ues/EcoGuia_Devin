
package com.proyecto.ecoguia.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape

import androidx.compose.material3.Text

import androidx.compose.runtime.*

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.proyecto.ecoguia.data.repository.PerfilSaludoRepository

import kotlinx.coroutines.CancellationException

private val negro = Color(0xFF19171F)
private val amarillo = Color(0xFFFFC21C)

// Conserva los saludos mientras la aplicación esté abierta.
private val saludosGuardados = mutableStateMapOf<String, String>()

@Composable
fun EcoGuiaHeader(
    rol: String
) {

    val repositorio = remember {
        PerfilSaludoRepository()
    }

    // Recuperar inmediatamente el saludo guardado.
    val saludo = saludosGuardados[rol]

    LaunchedEffect(rol) {

        // Si ya tenemos el saludo, no consultar nuevamente.
        if (saludosGuardados.containsKey(rol)) {
            return@LaunchedEffect
        }

        try {

            val perfil = repositorio.obtenerPerfil(rol)

            val primerNombre = perfil.nombre_completo
                .trim()
                .split(Regex("\\s+"))
                .firstOrNull()
                .orEmpty()

            val bienvenida = when (
                perfil.sexo.trim().lowercase()
            ) {

                "masculino" -> "Bienvenido"

                "femenino" -> "Bienvenida"

                else -> "Bienvenido/a"
            }

            saludosGuardados[rol] =
                if (primerNombre.isNotEmpty()) {
                    "$bienvenida $primerNombre"
                } else {
                    bienvenida
                }

        } catch (e: CancellationException) {

            throw e

        } catch (_: Exception) {

            // Mantener el encabezado sin saludo
            // hasta que pueda consultarse correctamente.
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(94.dp)
            .background(negro)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {

        Column(
            modifier = Modifier.weight(1f)
        ) {

            // Reservar el mismo espacio mientras se carga.
            Box(
                modifier = Modifier.height(18.dp),
                contentAlignment = Alignment.CenterStart
            ) {

                if (saludo != null) {

                    Text(
                        text = saludo,
                        fontSize = 12.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(5.dp))

            Text(
                text = "¿A dónde vamos?",
                fontSize = 20.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }

        Spacer(Modifier.width(10.dp))

        Box(
            modifier = Modifier
                .size(65.dp)
                .background(
                    amarillo,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {

            Text(
                text = "EcoGuía",
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.Black
            )
        }
    }
}
