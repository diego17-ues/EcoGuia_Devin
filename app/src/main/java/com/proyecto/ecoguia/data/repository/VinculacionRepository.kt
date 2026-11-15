
package com.proyecto.ecoguia.data.repository

import com.proyecto.ecoguia.data.remote.SupabaseNoVidenteClient
import io.github.jan.supabase.postgrest.postgrest

import kotlinx.serialization.Serializable

@Serializable
data class CodigoVinculacion(
    val codigo: String,
    val expira_utc: String
)

@Serializable
data class EstadoVinculacion(
    val vinculado: Boolean,
    val nombre_encargado: String? = null
)

@Serializable
data class ResultadoDesvinculacion(
    val exito: Boolean,
    val mensaje: String
)

class VinculacionRepository {

    private val supabase =
        SupabaseNoVidenteClient.client

    private val usuariosRepository =
        NoVidenteRepository()

    // Generar un código temporal
    suspend fun generarCodigo(): CodigoVinculacion {

        usuariosRepository.validarSesionNoVidente()

        return supabase.postgrest
            .rpc("ecoguia_generar_codigo_vinculacion")
            .decodeAs<CodigoVinculacion>()
    }

    // Consultar si existe un encargado vinculado
    suspend fun consultarEstado(): EstadoVinculacion {

        usuariosRepository.validarSesionNoVidente()

        return supabase.postgrest
            .rpc("ecoguia_estado_vinculacion")
            .decodeAs<EstadoVinculacion>()
    }

    // Desvincular al encargado
    suspend fun desvincular(): ResultadoDesvinculacion {

        usuariosRepository.validarSesionNoVidente()

        return supabase.postgrest
            .rpc("ecoguia_desvincular_encargado")
            .decodeAs<ResultadoDesvinculacion>()
    }
}
