
package com.proyecto.ecoguia.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.proyecto.ecoguia.data.repository.EstadoVinculacion
import com.proyecto.ecoguia.data.repository.VinculacionRepository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val colorTexto = Color(0xFF19192D)
private val colorBoton = Color(0xFF5B66C5)
private val colorFondoCodigo = Color(0xFFE7E4F9)
private val colorGris = Color(0xFF777783)
private val colorVerde = Color(0xFF16803D)
private val colorRojo = Color(0xFFB42318)

@Composable
fun GenerarCodigoVinculacion() {

    val scope = rememberCoroutineScope()

    val repositorio = remember {
        VinculacionRepository()
    }

    // Estado de vinculación consultado en Supabase
    var estado by remember {
        mutableStateOf<EstadoVinculacion?>(null)
    }

    // Código generado
    var codigo by remember {
        mutableStateOf("")
    }

    // Estados de carga
    var consultando by remember {
        mutableStateOf(true)
    }

    var generando by remember {
        mutableStateOf(false)
    }

    var desvinculando by remember {
        mutableStateOf(false)
    }

    // Mensajes
    var error by remember {
        mutableStateOf("")
    }

    var aviso by remember {
        mutableStateOf("")
    }

    var mostrarConfirmacion by remember {
        mutableStateOf(false)
    }

    // Consultar automáticamente la vinculación
    LaunchedEffect(Unit) {

        consultando = true
        error = ""

        try {

            estado = repositorio.consultarEstado()

        } catch (e: CancellationException) {

            throw e

        } catch (e: Exception) {

            error =
                "No se pudo consultar tu vinculación. " +
                        "Comprueba tu conexión e inténtalo nuevamente."

        } finally {

            consultando = false
        }
    }

    // Ocultar el código después de cinco minutos
    LaunchedEffect(codigo) {

        if (codigo.isNotEmpty()) {

            val codigoGenerado = codigo

            delay(300000L)

            if (codigo == codigoGenerado) {

                codigo = ""

                aviso =
                    "El código anterior ha vencido. " +
                            "Puedes generar uno nuevo."
            }
        }
    }

    // Confirmación de desvinculación
    if (mostrarConfirmacion) {

        AlertDialog(
            onDismissRequest = {
                if (!desvinculando) {
                    mostrarConfirmacion = false
                }
            },
            title = {

                Text(
                    text = "¿Desvincular encargado?",
                    fontWeight = FontWeight.Bold,
                    color = colorTexto
                )
            },
            text = {

                Text(
                    text = "Al desvincular, el encargado " +
                            "dejará de tener acceso a la " +
                            "información asociada a tu cuenta. " +
                            "Podrás vincularte nuevamente " +
                            "cuando lo necesites.",
                    color = colorTexto
                )
            },
            confirmButton = {

                TextButton(
                    enabled = !desvinculando,
                    onClick = {

                        desvinculando = true
                        error = ""
                        aviso = ""

                        scope.launch {

                            try {

                                val resultado =
                                    repositorio.desvincular()

                                if (resultado.exito) {

                                    estado = EstadoVinculacion(
                                        vinculado = false,
                                        nombre_encargado = null
                                    )

                                    codigo = ""

                                    mostrarConfirmacion = false

                                    aviso = resultado.mensaje

                                } else {

                                    mostrarConfirmacion = false

                                    error = resultado.mensaje
                                }

                            } catch (
                                e: CancellationException
                            ) {

                                throw e

                            } catch (e: Exception) {

                                mostrarConfirmacion = false

                                error =
                                    "No se pudo confirmar la " +
                                            "desvinculación. Comprueba " +
                                            "tu conexión e inténtalo nuevamente."

                            } finally {

                                desvinculando = false
                            }
                        }
                    }
                ) {

                    Text(
                        text = if (desvinculando) {
                            "Desvinculando..."
                        } else {
                            "Sí, desvincular"
                        },
                        color = colorRojo,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {

                TextButton(
                    enabled = !desvinculando,
                    onClick = {
                        mostrarConfirmacion = false
                    }
                ) {

                    Text(
                        text = "Cancelar",
                        color = colorBoton
                    )
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        // Consultando el estado de vinculación
        if (consultando) {

            CircularProgressIndicator(
                color = colorBoton,
                strokeWidth = 3.dp
            )

            Text(
                text = "Comprobando vinculación...",
                fontSize = 12.sp,
                color = colorGris
            )

        } else if (estado == null) {

            // Falló la consulta
            Text(
                text = "No se pudo comprobar " +
                        "el estado de tu cuenta.",
                fontSize = 13.sp,
                color = colorGris,
                textAlign = TextAlign.Center
            )

            Button(
                onClick = {

                    consultando = true
                    error = ""

                    scope.launch {

                        try {

                            estado = repositorio.consultarEstado()

                        } catch (
                            e: CancellationException
                        ) {

                            throw e

                        } catch (e: Exception) {

                            error =
                                "No se pudo consultar tu cuenta. " +
                                        "Comprueba tu conexión."

                        } finally {

                            consultando = false
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorBoton
                ),
                modifier = Modifier.fillMaxWidth()
            ) {

                Text("Reintentar")
            }

        } else if (estado?.vinculado == true) {

            // Cuenta ya vinculada
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {

                Text(
                    text = "✓",
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorVerde
                )

                Text(
                    text = "Vinculación activa",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorVerde
                )
            }

            // Información del encargado
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color(0xFFF2F1FA),
                        RoundedCornerShape(13.dp)
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {

                Text(
                    text = "Encargado vinculado",
                    fontSize = 12.sp,
                    color = colorGris
                )

                Text(
                    text = estado?.nombre_encargado
                        ?: "Nombre no disponible",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorTexto
                )

                Text(
                    text = "Tu cuenta está vinculada correctamente.",
                    fontSize = 12.sp,
                    color = colorGris
                )
            }

            // Botón para desvincular
            OutlinedButton(
                onClick = {
                    mostrarConfirmacion = true
                },
                enabled = !desvinculando,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    colorRojo
                )
            ) {

                Text(
                    text = "Desvincular encargado",
                    color = colorRojo,
                    fontWeight = FontWeight.SemiBold
                )
            }

        } else {

            // Cuenta sin encargado
            Text(
                text = "Genera un código para vincular " +
                        "tu cuenta con un encargado.",
                fontSize = 13.sp,
                color = colorTexto,
                textAlign = TextAlign.Center
            )

            // Área donde aparece el código
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        colorFondoCodigo,
                        RoundedCornerShape(14.dp)
                    )
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {

                Text(
                    text = "Código de vinculación",
                    fontSize = 13.sp,
                    color = colorGris
                )

                if (codigo.isNotEmpty()) {

                    Text(
                        text = codigo,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp,
                        color = colorTexto
                    )

                    Text(
                        text = "Válido durante 5 minutos " +
                                "desde su generación.",
                        fontSize = 12.sp,
                        color = colorGris,
                        textAlign = TextAlign.Center
                    )

                } else {

                    Text(
                        text = "Sin generar",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorTexto
                    )
                }
            }

            // Generar código real en Supabase
            Button(
                onClick = {

                    generando = true
                    error = ""
                    aviso = ""
                    codigo = ""

                    scope.launch {

                        try {

                            val resultado =
                                repositorio.generarCodigo()

                            codigo = resultado.codigo

                        } catch (
                            e: CancellationException
                        ) {

                            throw e

                        } catch (e: Exception) {

                            error =
                                "No se pudo generar el código. " +
                                        "Comprueba tu conexión y " +
                                        "el estado de tu vinculación."

                            // Actualizar estado por si fue vinculada
                            try {

                                estado =
                                    repositorio.consultarEstado()

                            } catch (
                                e: CancellationException
                            ) {

                                throw e

                            } catch (_: Exception) {
                                // Mantener el error original
                            }

                        } finally {

                            generando = false
                        }
                    }
                },
                enabled = !generando,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorBoton
                ),
                shape = RoundedCornerShape(12.dp)
            ) {

                if (generando) {

                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.height(20.dp)
                    )

                } else {

                    Text(
                        text = if (codigo.isEmpty()) {
                            "Generar código"
                        } else {
                            "Generar otro código"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Mensajes de información
        if (aviso.isNotEmpty()) {

            Text(
                text = aviso,
                fontSize = 12.sp,
                color = colorVerde,
                textAlign = TextAlign.Center
            )
        }

        // Mensajes de error
        if (error.isNotEmpty()) {

            Text(
                text = error,
                fontSize = 12.sp,
                color = colorRojo,
                textAlign = TextAlign.Center
            )
        }
    }
}
