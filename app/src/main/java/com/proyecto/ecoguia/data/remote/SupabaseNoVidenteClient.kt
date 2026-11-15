package com.proyecto.ecoguia.data.remote

import com.proyecto.ecoguia.BuildConfig

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseNoVidenteClient {

    val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
    ) {

        install(Auth) {

            // Almacenar la sesión de la persona
            // no vidente por separado.
            sessionManager = SettingsSessionManager(
                key = "ecoguia_sesion_no_vidente"
            )

            autoLoadFromStorage = true
            autoSaveToStorage = true
        }

        install(Postgrest)
    }
}
