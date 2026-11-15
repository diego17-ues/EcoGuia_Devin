package com.proyecto.ecoguia.guia

import org.junit.Assert.assertEquals
import org.junit.Test

class EstimadorTiempoTest {

    private val a = Coordenada(13.7189, -89.2030)
    private val b = Haversine.destino(a, 0.0, 120.0)
    private val c = Haversine.destino(b, 90.0, 60.0)

    @Test
    fun velocidadPorDefectoEs1_2() {
        assertEquals(1.2, EstimadorTiempo().velocidadMetrosSegundo, 1e-9)
        assertEquals(100L, EstimadorTiempo().segundosPara(120.0))
    }

    @Test
    fun velocidadConfigurable() {
        assertEquals(60L, EstimadorTiempo(2.0).segundosPara(120.0))
        assertEquals(240L, EstimadorTiempo(0.5).segundosPara(120.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun velocidadCeroNoEsValida() {
        EstimadorTiempo(0.0)
    }

    @Test
    fun distanciaNegativaEsCero() {
        assertEquals(0L, EstimadorTiempo().segundosPara(-5.0))
    }

    @Test
    fun usaDistanciaDeAristaCuandoExiste() {
        val total = EstimadorTiempo.distanciaTotal(listOf(a, b, c), listOf(150.0, 80.0))
        assertEquals(230.0, total, 1e-9)
    }

    @Test
    fun calculaConHaversineCuandoFaltaLaArista() {
        val total = EstimadorTiempo.distanciaTotal(listOf(a, b, c), listOf(null))
        assertEquals(180.0, total, 0.01)
        val mixto = EstimadorTiempo.distanciaTotal(listOf(a, b, c), listOf(150.0, null))
        assertEquals(210.0, mixto, 0.01)
    }

    @Test
    fun distanciaRestanteDesdeMitadDelPrimerTramo() {
        val puntos = listOf(a, b, c)
        val tramos = EstimadorTiempo.longitudesTramos(puntos, emptyList())
        val mitad = Haversine.destino(a, 0.0, 60.0)
        val restante = EstimadorTiempo.distanciaRestante(mitad, puntos, tramos, indiceObjetivo = 1)
        assertEquals(120.0, restante, 0.01)
        assertEquals(100L, EstimadorTiempo().segundosPara(restante))
    }

    @Test
    fun distanciaRestanteSinObjetivoEsCero() {
        val puntos = listOf(a, b)
        val tramos = EstimadorTiempo.longitudesTramos(puntos, emptyList())
        assertEquals(0.0, EstimadorTiempo.distanciaRestante(b, puntos, tramos, indiceObjetivo = 2), 1e-9)
    }

    @Test
    fun formatoLegible() {
        assertEquals("45 s", EstimadorTiempo.formatear(45))
        assertEquals("2 min", EstimadorTiempo.formatear(120))
        assertEquals("2 min 5 s", EstimadorTiempo.formatear(125))
        assertEquals("1 h 1 min", EstimadorTiempo.formatear(3_660))
    }
}
