
package com.proyecto.ecoguia.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class ResultadoVinculacion(
    val exito: Boolean,
    val mensaje: String
)

class VincularEncargadoRepository(
    private val supabase: SupabaseClient
) {

    suspend fun vincular(
        codigo: String
    ): ResultadoVinculacion {

        require(
            codigo.matches(Regex("^[0-9]{6}$"))
        ) {
            "Ingresa un código válido de 6 dígitos."
        }

        val parametros = buildJsonObject {
            put("p_codigo", codigo)
        }

        return supabase.postgrest
            .rpc(
                "ecoguia_vincular_encargado",
                parametros
            )
            .decodeAs<ResultadoVinculacion>()
    }
}


