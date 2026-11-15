package com.proyecto.ecoguia.guia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidadorInicioTest {

    private val entradaUes = Coordenada(13.7189, -89.2030)

    @Test
    fun sobreElNodoDeInicioPermiteIniciar() {
        val resultado = ValidadorInicio.validar(entradaUes, entradaUes)
        assertTrue(resultado is ResultadoInicio.EnInicio)
    }

    @Test
    fun aPocosMetrosDelNodoPermiteIniciar() {
        // ~10 m al norte del nodo.
        val cerca = Coordenada(13.7189 + 0.00009, -89.2030)
        assertTrue(ValidadorInicio.validar(cerca, entradaUes) is ResultadoInicio.EnInicio)
    }

    @Test
    fun fueraDelRadioNoPermiteIniciar() {
        // ~33 m al norte del nodo.
        val lejos = Coordenada(13.7189 + 0.0003, -89.2030)
        assertTrue(ValidadorInicio.validar(lejos, entradaUes) is ResultadoInicio.FueraDeInicio)
    }

    @Test
    fun desdeOtraCiudadNoPermiteIniciar() {
        val cojutepeque = Coordenada(13.7167, -88.9333)
        val resultado = ValidadorInicio.validar(cojutepeque, entradaUes)
        assertTrue(resultado is ResultadoInicio.FueraDeInicio)
        assertTrue((resultado as ResultadoInicio.FueraDeInicio).distanciaMetros > 25_000.0)
    }

    @Test
    fun sinUbicacionNoPermiteIniciar() {
        assertEquals(ResultadoInicio.SinUbicacion, ValidadorInicio.validar(null, entradaUes))
    }

    @Test
    fun rutaSinNodosNoPermiteIniciar() {
        assertEquals(ResultadoInicio.RutaSinNodos, ValidadorInicio.validar(entradaUes, null))
    }
}
