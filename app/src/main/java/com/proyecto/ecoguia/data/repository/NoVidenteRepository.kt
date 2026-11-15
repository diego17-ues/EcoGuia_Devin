
package com.proyecto.ecoguia.data.repository

import com.proyecto.ecoguia.data.remote.SupabaseNoVidenteClient

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest

import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
private data class PerfilNoVidente(
    val id: String,
    val rol: String
)

class NoVidenteRepository {

    // Cliente EXCLUSIVO del rol no vidente
    private val supabase =
        SupabaseNoVidenteClient.client

    // ESPERAR RESTAURACIÓN DE SESIÓN

    private suspend fun esperarSesion() {

        supabase.auth.sessionStatus.first {
            it !is SessionStatus.Initializing
        }
    }


    // CONSULTAR PERFIL
    private suspend fun consultarPerfil(
        id: String
    ): PerfilNoVidente? {

        return supabase.postgrest["usuarios"]
            .select {
                filter {
                    eq("id", id)
                }
            }
            .decodeList<PerfilNoVidente>()
            .singleOrNull()
    }

    // REGISTRAR PERSONA NO VIDENTE
    suspend fun registrarNoVidente(
        nombre: String,
        sexo: String
    ) {

        esperarSesion()

        val nombreLimpio = nombre.trim()

        require(nombreLimpio.length in 2..150) {
            "El nombre debe tener entre 2 y 150 caracteres."
        }

        require(
            sexo == "masculino" ||
                    sexo == "femenino"
        ) {
            "Selecciona tu sexo."
        }

        // Recuperar únicamente la sesión
        // guardada para la persona no vidente.
        if (
            supabase.auth.currentSessionOrNull() == null
        ) {

            // Si no existe una sesión, crear
            // una nueva cuenta anónima.
            supabase.auth.signInAnonymously()
        }

        val persona =
            supabase.auth.retrieveUserForCurrentSession(
                updateSession = true
            )

        check(persona.isAnonymous == true) {
            "La sesión guardada no corresponde " +
                    "a una persona no vidente."
        }

        val perfilActual =
            consultarPerfil(persona.id)

        if (perfilActual != null) {

            check(perfilActual.rol == "no_vidente") {
                "El perfil registrado no corresponde " +
                        "a una persona no vidente."
            }

            throw IllegalStateException(
                "Ya existe una cuenta de persona " +
                        "no vidente en este teléfono. " +
                        "Utiliza Iniciar sesión."
            )
        }

        // Crear el perfil en la tabla usuarios
        // utilizando la función SQL existente.
        supabase.postgrest.rpc(
            function = "registrar_perfil",
            parameters = buildJsonObject {
                put(
                    "p_nombre_completo",
                    nombreLimpio
                )
                put("p_sexo", sexo)
            }
        )

        val perfilGuardado =
            consultarPerfil(persona.id)

        check(
            perfilGuardado?.rol == "no_vidente"
        ) {
            "No se pudo comprobar el perfil " +
                    "de la persona no vidente."
        }
    }

    // INICIAR SESIÓN PERSONA NO VIDENTE

    suspend fun validarSesionNoVidente() {

        esperarSesion()

        val sesion =
            supabase.auth.currentSessionOrNull()

        android.util.Log.d(
            "ECOGUIA_SESION",
            if (sesion == null) {
                "No existe sesión local de persona no vidente"
            } else {
                "Existe sesión local de persona no vidente"
            }
        )


        check(sesion != null) {
            "No existe una sesión de persona " +
                    "no vidente guardada en este teléfono. " +
                    "Primero debes crear la cuenta."
        }

        val persona =
            supabase.auth.retrieveUserForCurrentSession(
                updateSession = true
            )

        check(persona.isAnonymous == true) {
            "La sesión actual no corresponde " +
                    "a una persona no vidente."
        }

        val perfil =
            consultarPerfil(persona.id)

        check(perfil?.rol == "no_vidente") {
            "No se encontró un perfil válido " +
                    "de persona no vidente."
        }
    }

    suspend fun obtenerIdAutenticado(): String {
        validarSesionNoVidente()

        return supabase.auth
            .retrieveUserForCurrentSession(updateSession = true)
            .id
    }
}
