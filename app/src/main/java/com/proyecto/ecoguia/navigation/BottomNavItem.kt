
package com.proyecto.ecoguia.navigation

import androidx.annotation.DrawableRes
import com.proyecto.ecoguia.R

// Opciones de la barra inferior
enum class BottomNavItem(
    val titulo: String,
    @DrawableRes val icono: Int
) {
    INICIO(
        titulo = "Inicio",
        icono = R.drawable.ic_inicio
    ),

    AJUSTES(
        titulo = "Ajustes",
        icono = R.drawable.ic_ajustes
    )
}
