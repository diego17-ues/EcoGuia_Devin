//Establecer conexión

package com.proyecto.ecoguia.data.remote

import com.proyecto.ecoguia.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClient {

    val client by lazy {

        require(BuildConfig.SUPABASE_URL.isNotBlank()) {
            "Falta configurar SUPABASE_URL"
        }

        require(
            BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()
        ) {
            "Falta configurar SUPABASE_PUBLISHABLE_KEY"
        }

        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        ) {

            // AUTENTICACIÓN
            install(Auth)

            // CONSULTAS A POSTGRESQL
            install(Postgrest)
        }
    }
}


