
package com.proyecto.ecoguia.data.repository

import com.proyecto.ecoguia.data.remote.SupabaseClient
import com.proyecto.ecoguia.data.remote.SupabaseNoVidenteClient

import io.github.jan.supabase.postgrest.postgrest

import kotlinx.serialization.Serializable

@Serializable
data class PerfilSaludo(
    val nombre_completo: String,
    val sexo: String
)

class PerfilSaludoRepository {

    suspend fun obtenerPerfil(
        rol: String
    ): PerfilSaludo {

        // Utilizar la sesión correspondiente al rol
        val cliente = when (rol) {

            "encargado" ->
                SupabaseClient.client

            "no_vidente" ->
                SupabaseNoVidenteClient.client

            else ->
                throw IllegalArgumentException(
                    "Rol no válido."
                )
        }

        // Consultar los datos del usuario autenticado
        return cliente.postgrest
            .rpc("ecoguia_obtener_saludo")
            .decodeAs<PerfilSaludo>()
    }
}
