package com.proyecto.ecoguia.guia

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectorFuenteTest {

    private val ahora = 100_000L
    private val esp32 = Coordenada(13.70, -89.20)
    private val telefono = Coordenada(13.71, -89.21)

    private fun entradas(
        conectado: Boolean = true,
        fix: Boolean = true,
        edadEsp32Ms: Long = 500,
        edadRumboEsp32Ms: Long = 100,
        simulacion: Boolean = false
    ) = EntradasGuia(
        simulacion = simulacion,
        esp32Conectado = conectado,
        posicionEsp32 = Lectura(PosicionEsp32(esp32, fix), ahora - edadEsp32Ms),
        rumboEsp32 = Lectura(45.0, ahora - edadRumboEsp32Ms),
        posicionTelefono = Lectura(telefono, ahora - 1_000),
        rumboTelefono = Lectura(90.0, ahora - 100),
        posicionSimulada = Lectura(Coordenada(0.0, 0.0), ahora),
        rumboSimulado = Lectura(180.0, ahora)
    )

    @Test
    fun esp32ConFixEsLaFuentePrincipal() {
        val elegidas = SelectorFuente.elegir(entradas(), ahora)
        assertEquals(FuentePosicion.ESP32, elegidas.fuentePosicion)
        assertEquals(esp32, elegidas.posicion)
        assertEquals(FuenteRumbo.ESP32, elegidas.fuenteRumbo)
        assertEquals(45.0, elegidas.rumbo!!, 1e-9)
    }

    @Test
    fun sinFixSeUsaElGpsDelTelefonoPeroElRumboDelMpu() {
        val elegidas = SelectorFuente.elegir(entradas(fix = false), ahora)
        assertEquals(FuentePosicion.TELEFONO, elegidas.fuentePosicion)
        assertEquals(FuenteRumbo.ESP32, elegidas.fuenteRumbo)
    }

    @Test
    fun desconectadoSeUsaTodoDelTelefono() {
        val elegidas = SelectorFuente.elegir(entradas(conectado = false), ahora)
        assertEquals(FuentePosicion.TELEFONO, elegidas.fuentePosicion)
        assertEquals(FuenteRumbo.TELEFONO, elegidas.fuenteRumbo)
        assertEquals(90.0, elegidas.rumbo!!, 1e-9)
    }

    @Test
    fun lecturasViejasDelEsp32SeDescartan() {
        val elegidas = SelectorFuente.elegir(entradas(edadEsp32Ms = 5_000, edadRumboEsp32Ms = 2_000), ahora)
        assertEquals(FuentePosicion.TELEFONO, elegidas.fuentePosicion)
        assertEquals(FuenteRumbo.TELEFONO, elegidas.fuenteRumbo)
    }

    @Test
    fun simulacionTienePrioridad() {
        val elegidas = SelectorFuente.elegir(entradas(simulacion = true), ahora)
        assertEquals(FuentePosicion.SIMULACION, elegidas.fuentePosicion)
        assertEquals(FuenteRumbo.SIMULACION, elegidas.fuenteRumbo)
    }
}
