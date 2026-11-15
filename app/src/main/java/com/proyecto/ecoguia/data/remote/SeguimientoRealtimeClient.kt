package com.proyecto.ecoguia.data.remote

import com.proyecto.ecoguia.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.ktor.client.engine.okhttp.OkHttp

/** Cliente aislado para el encargado: el motor Android actual no soporta WebSockets. */
object SeguimientoRealtimeClient {
    val client by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        ) {
            httpEngine = OkHttp.create()
            install(Auth)
            install(Postgrest)
            install(Realtime)
        }
    }
}
