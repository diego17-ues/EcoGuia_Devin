
package com.proyecto.ecoguia.components

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

@Composable
fun recordarBiometria(
    onExito: () -> Unit,
    onError: (String) -> Unit,
    onFallo: () -> Unit = {},
    onCancel: () -> Unit = {}
): () -> Unit {

    val contexto = LocalContext.current
    val actividad = contexto as? FragmentActivity

    val exitoActual = rememberUpdatedState(onExito)
    val errorActual = rememberUpdatedState(onError)
    val falloActual = rememberUpdatedState(onFallo)
    val cancelActual = rememberUpdatedState(onCancel)

    val prompt = remember(actividad) {
        actividad?.let { act ->

            BiometricPrompt(
                act,
                ContextCompat.getMainExecutor(act),
                object : BiometricPrompt.AuthenticationCallback() {

                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult
                    ) {
                        super.onAuthenticationSucceeded(result)

                        exitoActual.value()
                    }

                    // La huella no fue reconocida.
                    // Android permite intentarlo nuevamente.
                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()

                        falloActual.value()
                    }

                    // La verificación terminó o fue cancelada.
                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence
                    ) {
                        super.onAuthenticationError(
                            errorCode,
                            errString
                        )

                        if (
                            errorCode == BiometricPrompt.ERROR_CANCELED ||
                            errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                        ) {
                            cancelActual.value()
                        } else {
                            errorActual.value(
                                errString.toString()
                            )
                        }
                    }
                }
            )
        }
    }

    return {
        if (actividad == null || prompt == null) {

            errorActual.value(
                "No se pudo iniciar la verificación biométrica."
            )

        } else {

            val autenticadores =
                BiometricManager.Authenticators.BIOMETRIC_STRONG

            val estado = BiometricManager.from(actividad)
                .canAuthenticate(autenticadores)

            if (estado != BiometricManager.BIOMETRIC_SUCCESS) {

                errorActual.value(
                    "No hay una biometría compatible configurada. " +
                            "Registra tu huella en los ajustes del teléfono " +
                            "e inténtalo nuevamente."
                )

            } else {

                val informacion =
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle("EcoGuía")
                        .setSubtitle("Ingrese su huella")
                        .setAllowedAuthenticators(
                            autenticadores
                        )
                        .setNegativeButtonText("Cancelar")
                        .build()

                prompt.authenticate(informacion)
            }
        }
    }
}
