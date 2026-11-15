package com.proyecto.ecoguia.guia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValidadorRumboTest {

    // Tramo de 100 m hacia el norte.
    private val inicio = Coordenada(13.7189, -89.2030)
    private val fin = Haversine.destino(inicio, 0.0, 100.0)
    private val sobreLaRuta = Haversine.destino(inicio, 0.0, 30.0)

    @Test
    fun sinRumboDelMpuNoValida() {
        val resultado = ValidadorRumbo().evaluar(sobreLaRuta, null, null, inicio, fin)
        assertEquals(EstadoValidacion.SIN_MPU, resultado.estado)
        assertNull(resultado.indicacion)
    }

    @Test
    fun sinPosicionOConLaRutaTerminada() {
        assertEquals(EstadoValidacion.SIN_POSICION, ValidadorRumbo().evaluar(null, 0.0, 0.0, inicio, fin).estado)
        val terminada = ValidadorRumbo().evaluar(sobreLaRuta, 0.0, 0.0, inicio, null)
        assertEquals(EstadoValidacion.RUTA_TERMINADA, terminada.estado)
        assertNull(terminada.indicacion)
    }

    @Test
    fun siguiendoLaRutaEnviaA() {
        val resultado = ValidadorRumbo().evaluar(sobreLaRuta, 5.0, 0.0, inicio, fin)
        assertEquals(EstadoValidacion.EN_RUTA, resultado.estado)
        assertEquals(Indicacion.RECTO, resultado.indicacion)
    }

    @Test
    fun desviadoHaciaUnLadoPideGirarAlOtro() {
        assertEquals(Indicacion.IZQUIERDA, ValidadorRumbo().evaluar(sobreLaRuta, 60.0, 0.0, inicio, fin).indicacion)
        assertEquals(Indicacion.DERECHA, ValidadorRumbo().evaluar(sobreLaRuta, 300.0, 0.0, inicio, fin).indicacion)
    }

    @Test
    fun histeresisYConfirmacionAntesDeVolverAA() {
        val validador = ValidadorRumbo()
        assertEquals(Indicacion.IZQUIERDA, validador.evaluar(sobreLaRuta, 60.0, 0.0, inicio, fin).indicacion)
        // 15° de desvío: no bastaría para empezar a corregir, pero sí para seguir corrigiendo.
        assertEquals(Indicacion.IZQUIERDA, validador.evaluar(sobreLaRuta, 15.0, 0.0, inicio, fin).indicacion)
        // Ya alineado: el cambio a A se confirma en el segundo cálculo.
        assertEquals(Indicacion.IZQUIERDA, validador.evaluar(sobreLaRuta, 3.0, 0.0, inicio, fin).indicacion)
        assertEquals(Indicacion.RECTO, validador.evaluar(sobreLaRuta, 3.0, 0.0, inicio, fin).indicacion)
    }

    @Test
    fun ignoraUnaLecturaAisladaDelMpu() {
        val validador = ValidadorRumbo()
        assertEquals(Indicacion.RECTO, validador.evaluar(sobreLaRuta, 0.0, 0.0, inicio, fin).indicacion)
        assertEquals(Indicacion.RECTO, validador.evaluar(sobreLaRuta, 40.0, 0.0, inicio, fin).indicacion)
        assertEquals(Indicacion.RECTO, validador.evaluar(sobreLaRuta, 0.0, 0.0, inicio, fin).indicacion)
    }

    @Test
    fun corrigeDeInmediatoSiVaEnSentidoContrario() {
        val validador = ValidadorRumbo()
        assertEquals(Indicacion.RECTO, validador.evaluar(sobreLaRuta, 0.0, 0.0, inicio, fin).indicacion)
        assertEquals(EstadoValidacion.DESVIADO, validador.evaluar(sobreLaRuta, 170.0, 0.0, inicio, fin).estado)
    }

    @Test
    fun siYaEstaGirandoHaciaLaRutaNoLaCorrige() {
        // Mira 60° a la derecha, pero gira a la izquierda a 120 °/s.
        assertEquals(Indicacion.RECTO, ValidadorRumbo().evaluar(sobreLaRuta, 60.0, -120.0, inicio, fin).indicacion)
    }

    @Test
    fun apartadoDeLaLineaLoDevuelveALaRuta() {
        val apartado = Haversine.destino(sobreLaRuta, 90.0, 10.0)
        val resultado = ValidadorRumbo().evaluar(apartado, 0.0, 0.0, inicio, fin)
        assertEquals(Indicacion.IZQUIERDA, resultado.indicacion)
        assertEquals(10.0, resultado.desvioLateralMetros!!, 0.5)
        // Sin tramo (aún no se alcanzó el primer nodo) solo se apunta al nodo.
        assertEquals(Indicacion.RECTO, ValidadorRumbo().evaluar(apartado, 0.0, 0.0, null, fin).indicacion)
    }
}
