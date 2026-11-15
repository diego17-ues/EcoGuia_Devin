
package com.proyecto.ecoguia.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Colores de la barra de EcoGuía
private val fondoBarra = Color.White
private val textoOscuro = Color(0xFF19171F)
private val azulSeleccionado = Color(0xFF5269E9)
private val colorDivisor = Color(0xFFE4E4E8)

// ======================================
// BARRA INFERIOR REUTILIZABLE
// ======================================

@Composable
fun EcoGuiaNavigationBar(
    pantallaActual: BottomNavItem,
    onInicioClick: () -> Unit,
    onAjustesClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(fondoBarra)
    ) {

        HorizontalDivider(
            thickness = 1.dp,
            color = colorDivisor
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // INICIO
            ItemNavegacion(
                item = BottomNavItem.INICIO,
                seleccionado =
                    pantallaActual == BottomNavItem.INICIO,
                onClick = onInicioClick,
                modifier = Modifier.weight(1f)
            )

            // AJUSTES
            ItemNavegacion(
                item = BottomNavItem.AJUSTES,
                seleccionado =
                    pantallaActual == BottomNavItem.AJUSTES,
                onClick = onAjustesClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ======================================
// ELEMENTO DE NAVEGACIÓN
// ======================================

@Composable
private fun ItemNavegacion(
    item: BottomNavItem,
    seleccionado: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .semantics {
                selected = seleccionado
                contentDescription = item.titulo
            }
            .clickable(
                role = Role.Tab,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        // Solo cambia el color del icono
        Image(
            painter = painterResource(
                id = item.icono
            ),
            contentDescription = null,
            modifier = Modifier.size(25.dp),
            colorFilter = ColorFilter.tint(
                if (seleccionado)
                    azulSeleccionado
                else
                    textoOscuro
            )
        )

        Spacer(Modifier.height(3.dp))

        Text(
            text = item.titulo,
            fontSize = 12.sp,
            fontWeight =
                if (seleccionado)
                    FontWeight.Medium
                else
                    FontWeight.Normal,
            color = textoOscuro
        )
    }
}
