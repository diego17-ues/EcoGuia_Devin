
package com.proyecto.ecoguia.data.repository

import com.proyecto.ecoguia.BuildConfig
import com.proyecto.ecoguia.data.remote.SupabaseClient

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.postgrest.postgrest

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

import kotlin.time.ExperimentalTime

@Serializable
private data class PerfilEncargado(
    val id: String,
    val rol: String
)

@OptIn(ExperimentalTime::class)
class AuthRepository {

    private val supabase = SupabaseClient.client

    // =====================================
    // CREAR CUENTA DEL ENCARGADO
    // =====================================

    suspend fun registrarEncargado(
        nombre: String,
        sexo: String,
        usuario: String,
        correo: String,
        contrasena: String
    ) {

        if (
            supabase.auth.currentSessionOrNull() != null
        ) {
            throw IllegalStateException(
                "Cierra la sesión actual antes de registrar otra cuenta"
            )
        }

        supabase.auth.signUpWith(Email) {

            email = correo.trim().lowercase()
            password = contrasena

            data = buildJsonObject {

                put(
                    "nombre_completo",
                    nombre.trim()
                )

                put("sexo", sexo)

                put(
                    "nombre_usuario",
                    usuario.trim().lowercase()
                )
            }
        }

        // El usuario debe confirmar su correo.
    }

    // =====================================
    // INICIAR SESIÓN DEL ENCARGADO
    // =====================================

    suspend fun iniciarSesionEncargado(
        usuario: String,
        contrasena: String
    ) {

        // No reutilizar una sesión anterior.
        if (
            supabase.auth.currentSessionOrNull() != null
        ) {
            supabase.auth.signOut()
        }

        // Consultar función de inicio de sesión
        val sesion = HttpClient(Android).use { http ->

            val response = http.post(
                "${BuildConfig.SUPABASE_URL}/functions/v1/login-encargado"
            ) {

                header(
                    "apikey",
                    BuildConfig.SUPABASE_PUBLISHABLE_KEY
                )

                contentType(ContentType.Application.Json)

                setBody(
                    buildJsonObject {
                        put(
                            "usuario",
                            usuario.trim().lowercase()
                        )
                        put("password", contrasena)
                    }.toString()
                )
            }

            if (response.status != HttpStatusCode.OK) {

                throw IllegalArgumentException(
                    if (
                        response.status ==
                        HttpStatusCode.Unauthorized
                    ) {
                        "Usuario o contraseña incorrectos, o correo sin confirmar"
                    } else {
                        "No se pudo iniciar sesión (HTTP ${response.status.value})"
                    }
                )
            }

            Json.parseToJsonElement(
                response.bodyAsText()
            ).jsonObject
        }

        // Guardar la sesión de Supabase Auth
        supabase.auth.importSession(
            UserSession(
                accessToken = sesion
                    .getValue("access_token")
                    .jsonPrimitive.content,

                refreshToken = sesion
                    .getValue("refresh_token")
                    .jsonPrimitive.content,

                expiresIn = sesion
                    .getValue("expires_in")
                    .jsonPrimitive.long,

                tokenType = sesion
                    .getValue("token_type")
                    .jsonPrimitive.content,

                user = null
            )
        )

        try {

            // Obtener el usuario autenticado
            val persona =
                supabase.auth.retrieveUserForCurrentSession(
                    updateSession = true
                )

            if (persona.isAnonymous == true) {
                error(
                    "La cuenta no corresponde a un encargado"
                )
            }

            // Comprobar si existe el perfil
            val existentes =
                supabase.postgrest["usuarios"].select {
                    filter {
                        eq("id", persona.id)
                    }
                }.decodeList<PerfilEncargado>()

            if (existentes.isEmpty()) {

                // Primer inicio después de confirmar correo
                val metadatos =
                    persona.userMetadata
                        ?: error(
                            "Faltan los datos del registro"
                        )

                val nombre = metadatos[
                    "nombre_completo"
                ]?.jsonPrimitive?.content
                    ?: error(
                        "Falta nombre del registro"
                    )

                val sexo = metadatos[
                    "sexo"
                ]?.jsonPrimitive?.content
                    ?: error(
                        "Falta sexo del registro"
                    )

                val nombreUsuario = metadatos[
                    "nombre_usuario"
                ]?.jsonPrimitive?.content
                    ?: error(
                        "Falta nombre de usuario del registro"
                    )

                // Guardar perfil en PostgreSQL
                supabase.postgrest.rpc(
                    "registrar_perfil",
                    buildJsonObject {

                        put(
                            "p_nombre_completo",
                            nombre
                        )

                        put(
                            "p_sexo",
                            sexo
                        )

                        put(
                            "p_nombre_usuario",
                            nombreUsuario
                        )
                    }
                )

            } else if (
                existentes.first().rol != "encargado"
            ) {
                error(
                    "La cuenta no tiene el rol de encargado"
                )
            }

            // Comprobación final del perfil
            val comprobacion =
                supabase.postgrest["usuarios"].select {
                    filter {
                        eq("id", persona.id)
                    }
                }.decodeList<PerfilEncargado>()

            check(
                comprobacion.singleOrNull()?.rol ==
                        "encargado"
            ) {
                "No se pudo validar el perfil de encargado"
            }

        } catch (e: Exception) {

            supabase.auth.signOut()
            throw e
        }
    }

    // =====================================
    // CERRAR SESIÓN
    // =====================================

    suspend fun cerrarSesion() {

        if (
            supabase.auth.currentSessionOrNull() != null
        ) {
            supabase.auth.signOut()
        }
    }
}
