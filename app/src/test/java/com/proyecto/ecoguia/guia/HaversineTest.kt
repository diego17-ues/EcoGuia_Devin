package com.proyecto.ecoguia.guia

import org.junit.Assert.assertEquals
import org.junit.Test

class HaversineTest {

    @Test
    fun mismoPuntoDaDistanciaCero() {
        val punto = Coordenada(13.7189, -89.2030)
        assertEquals(0.0, Haversine.distanciaMetros(punto, punto), 1e-9)
    }

    @Test
    fun unGradoDeLatitudSonUnos111Km() {
        val distancia = Haversine.distanciaMetros(0.0, 0.0, 1.0, 0.0)
        assertEquals(111_195.0, distancia, 5.0)
    }

    @Test
    fun distanciaConocidaSanSalvadorSantaAna() {
        // San Salvador (13.6929, -89.2182) a Santa Ana (13.9946, -89.5597): ~49.8 km
        val distancia = Haversine.distanciaMetros(13.6929, -89.2182, 13.9946, -89.5597)
        assertEquals(49_800.0, distancia, 500.0)
    }

    @Test
    fun distanciaCortaEnCampus() {
        // 0.0001° de latitud ≈ 11.1 m
        val distancia = Haversine.distanciaMetros(13.7189, -89.2030, 13.7190, -89.2030)
        assertEquals(11.12, distancia, 0.05)
    }

    @Test
    fun esSimetrica() {
        val a = Coordenada(13.7189, -89.2030)
        val b = Coordenada(13.7201, -89.2011)
        assertEquals(Haversine.distanciaMetros(a, b), Haversine.distanciaMetros(b, a), 1e-9)
    }

    @Test
    fun rumboHaciaLosPuntosCardinales() {
        val origen = Coordenada(13.7189, -89.2030)
        assertEquals(0.0, Haversine.rumboGrados(origen, Coordenada(13.7289, -89.2030)), 0.01)
        assertEquals(90.0, Haversine.rumboGrados(origen, Coordenada(13.7189, -89.1930)), 0.01)
        assertEquals(180.0, Haversine.rumboGrados(origen, Coordenada(13.7089, -89.2030)), 0.01)
        assertEquals(270.0, Haversine.rumboGrados(origen, Coordenada(13.7189, -89.2130)), 0.01)
    }

    @Test
    fun destinoRecorreLaDistanciaYElRumboPedidos() {
        val origen = Coordenada(13.7189, -89.2030)
        val destino = Haversine.destino(origen, 45.0, 100.0)
        assertEquals(100.0, Haversine.distanciaMetros(origen, destino), 0.01)
        assertEquals(45.0, Haversine.rumboGrados(origen, destino), 0.01)
    }

    @Test
    fun normalizaRumbos() {
        assertEquals(0.0, Haversine.normalizarRumbo(360.0), 1e-9)
        assertEquals(350.0, Haversine.normalizarRumbo(-10.0), 1e-9)
        assertEquals(10.0, Haversine.normalizarRumbo(730.0), 1e-9)
    }
}
