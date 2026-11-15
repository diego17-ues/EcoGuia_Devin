package com.proyecto.ecoguia.guia

import org.junit.Assert.assertEquals
import org.junit.Test

class DireccionRelativaTest {

    @Test
    fun dentroDelUmbralEsRecto() {
        assertEquals(Indicacion.RECTO, DireccionRelativa.calcular(rumboActual = 90.0, rumboObjetivo = 90.0))
        assertEquals(Indicacion.RECTO, DireccionRelativa.calcular(90.0, 110.0))
        assertEquals(Indicacion.RECTO, DireccionRelativa.calcular(90.0, 70.0))
    }

    @Test
    fun justoFueraDelUmbralGira() {
        assertEquals(Indicacion.DERECHA, DireccionRelativa.calcular(90.0, 110.5))
        assertEquals(Indicacion.IZQUIERDA, DireccionRelativa.calcular(90.0, 69.5))
    }

    @Test
    fun objetivoALaDerechaEsR() {
        assertEquals(Indicacion.DERECHA, DireccionRelativa.calcular(0.0, 90.0))
        assertEquals(Indicacion.DERECHA, DireccionRelativa.calcular(0.0, 179.0))
    }

    @Test
    fun objetivoALaIzquierdaEsL() {
        assertEquals(Indicacion.IZQUIERDA, DireccionRelativa.calcular(0.0, 270.0))
        assertEquals(Indicacion.IZQUIERDA, DireccionRelativa.calcular(90.0, 0.0))
    }

    @Test
    fun cruceDelNorteSeTomaPorElLadoCorto() {
        // Mirando a 350°, un objetivo a 10° está 20° a la derecha: recto.
        assertEquals(Indicacion.RECTO, DireccionRelativa.calcular(350.0, 10.0))
        // Mirando a 350°, un objetivo a 40° está 50° a la derecha.
        assertEquals(Indicacion.DERECHA, DireccionRelativa.calcular(350.0, 40.0))
        // Mirando a 10°, un objetivo a 300° está 70° a la izquierda.
        assertEquals(Indicacion.IZQUIERDA, DireccionRelativa.calcular(10.0, 300.0))
    }

    @Test
    fun diferenciaFirmada() {
        assertEquals(20.0, DireccionRelativa.diferenciaGrados(350.0, 10.0), 1e-9)
        assertEquals(-20.0, DireccionRelativa.diferenciaGrados(10.0, 350.0), 1e-9)
        assertEquals(180.0, DireccionRelativa.diferenciaGrados(0.0, 180.0), 1e-9)
    }

    @Test
    fun umbralConfigurable() {
        assertEquals(Indicacion.DERECHA, DireccionRelativa.calcular(0.0, 15.0, umbralRectoGrados = 10.0))
        assertEquals(Indicacion.RECTO, DireccionRelativa.calcular(0.0, 15.0, umbralRectoGrados = 20.0))
    }

    @Test
    fun letrasDelProtocolo() {
        assertEquals('L', Indicacion.IZQUIERDA.letra)
        assertEquals('R', Indicacion.DERECHA.letra)
        assertEquals('A', Indicacion.RECTO.letra)
        assertEquals('N', AvisoRuta.NODO_ALCANZADO.letra)
        assertEquals('F', AvisoRuta.DESTINO_FINAL.letra)
    }

    @Test
    fun haciaUnPuntoAlEsteMirandoAlNorte() {
        val posicion = Coordenada(13.7189, -89.2030)
        val alEste = Coordenada(13.7189, -89.2020)
        assertEquals(Indicacion.DERECHA, DireccionRelativa.hacia(posicion, 0.0, alEste))
        assertEquals(Indicacion.RECTO, DireccionRelativa.hacia(posicion, 90.0, alEste))
        assertEquals(Indicacion.IZQUIERDA, DireccionRelativa.hacia(posicion, 180.0, alEste))
    }
}
