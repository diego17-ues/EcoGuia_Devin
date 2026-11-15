package com.proyecto.ecoguia.guia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotorGuiaTest {

    private val inicio = Coordenada(13.7189, -89.2030)
    private val obstaculo = Haversine.destino(inicio, 0.0, 50.0)
    private val giro = Haversine.destino(inicio, 0.0, 100.0)
    private val destino = Haversine.destino(giro, 90.0, 100.0)

    private fun ruta(longitudes: List<Double?> = emptyList()) = RutaGuia(
        id = 1,
        nombre = "Prueba",
        puntos = listOf(
            PuntoRuta(1, "Inicio", "Inicio", "", inicio),
            PuntoRuta(2, "Banca", "Obstáculo", "", obstaculo),
            PuntoRuta(3, "Esquina", "Giro a la derecha", "", giro),
            PuntoRuta(4, "Biblioteca", "Destino", "", destino)
        ),
        longitudesAristas = longitudes
    )

    @Test
    fun detectaObstaculoPorTipoSinImportarTildes() {
        assertTrue(PuntoRuta.esTipoObstaculo("Obstáculo"))
        assertTrue(PuntoRuta.esTipoObstaculo("obstaculo"))
        assertTrue(PuntoRuta.esTipoObstaculo("OBSTÁCULO: gradas"))
        assertFalse(PuntoRuta.esTipoObstaculo("Giro a la izquierda"))
    }

    @Test
    fun tiempoTotalAlInicio() {
        val motor = MotorGuia(ruta())
        val estado = motor.actualizar(null, null)
        assertEquals(200.0, estado.distanciaTotalMetros, 0.05)
        assertEquals(167L, estado.tiempoTotalSegundos)
        assertNull(estado.indicacion)
    }

    @Test
    fun tiempoTotalUsaDistanciaDeAristas() {
        val motor = MotorGuia(ruta(listOf(60.0, 60.0, 120.0)))
        assertEquals(240.0, motor.distanciaTotalMetros, 1e-9)
        assertEquals(200L, motor.tiempoTotalSegundos)
    }

    @Test
    fun avanzaNodosYRecalculaElTiempoRestante() {
        val motor = MotorGuia(ruta())
        val enInicio = motor.actualizar(inicio, 0.0)
        // El nodo de inicio queda alcanzado; el objetivo es el obstáculo.
        assertEquals(1, enInicio.indiceObjetivo)
        assertEquals(Indicacion.RECTO, enInicio.indicacion)
        assertEquals(200.0, enInicio.distanciaRestanteMetros, 0.1)

        val enMitad = motor.actualizar(Haversine.destino(inicio, 0.0, 75.0), 0.0)
        assertEquals(2, enMitad.indiceObjetivo)
        assertEquals(125.0, enMitad.distanciaRestanteMetros, 0.1)
        assertEquals(104L, enMitad.tiempoRestanteSegundos)
    }

    @Test
    fun indicaGiroEnLaEsquina() {
        val motor = MotorGuia(ruta())
        val estado = motor.actualizar(giro, 0.0)
        assertEquals(3, estado.indiceObjetivo)
        assertEquals(Indicacion.DERECHA, estado.indicacion)
        assertEquals(Indicacion.RECTO, motor.actualizar(giro, 90.0).indicacion)
    }

    @Test
    fun obstaculoSiguienteYPasoAutomaticoBajo5m() {
        val motor = MotorGuia(ruta())
        val lejos = motor.actualizar(Haversine.destino(inicio, 0.0, 20.0), 0.0)
        assertEquals("Banca", lejos.obstaculo?.punto?.nombre)
        assertEquals(30.0, lejos.obstaculo?.distanciaMetros ?: -1.0, 0.1)
        assertEquals(Indicacion.RECTO, lejos.obstaculo?.indicacion)
        assertEquals(1, lejos.obstaculosPendientes)

        val a6m = motor.actualizar(Haversine.destino(obstaculo, 180.0, 6.0), 0.0)
        assertEquals("Banca", a6m.obstaculo?.punto?.nombre)

        val a4m = motor.actualizar(Haversine.destino(obstaculo, 180.0, 4.0), 0.0)
        assertNull(a4m.obstaculo)
        assertEquals(0, a4m.obstaculosPendientes)
    }

    @Test
    fun cambiarDeFuenteNoReiniciaElAvance() {
        val motor = MotorGuia(ruta())
        motor.actualizar(giro, 90.0)
        // Una lectura sin posición (p. ej. mientras se cambia de ESP32 a teléfono) no retrocede.
        val sinPosicion = motor.actualizar(null, null)
        assertEquals(3, sinPosicion.indiceObjetivo)
        assertEquals(3, motor.actualizar(Haversine.destino(giro, 90.0, 10.0), 90.0).indiceObjetivo)
    }

    @Test
    fun finalizaAlLlegarAlDestino() {
        val motor = MotorGuia(ruta())
        motor.actualizar(giro, 90.0)
        val fin = motor.actualizar(destino, 90.0)
        assertTrue(fin.finalizada)
        assertEquals(0L, fin.tiempoRestanteSegundos)
        assertNull(fin.indicacion)
    }

    @Test
    fun cambiarVelocidadRecalculaTiempos() {
        val motor = MotorGuia(ruta(listOf(60.0, 60.0, 120.0)))
        motor.estimador = EstimadorTiempo(2.4)
        assertEquals(100L, motor.tiempoTotalSegundos)
    }

    @Test
    fun simuladorRecorreLaRutaCompleta() {
        val puntos = ruta().puntos.map { it.coordenada }
        val simulador = SimuladorRuta(puntos, velocidadMetrosSegundo = 5.0)
        val motor = MotorGuia(ruta())
        val indicaciones = mutableSetOf<Indicacion>()
        var pasos = 0
        while (!motor.actualizar(null, null).finalizada && pasos < 200) {
            val lectura = simulador.avanzar(1.0)
            motor.actualizar(lectura.posicion, lectura.rumbo).indicacion?.let(indicaciones::add)
            pasos++
        }
        assertTrue(motor.actualizar(null, null).finalizada)
        assertEquals(setOf(Indicacion.IZQUIERDA, Indicacion.DERECHA, Indicacion.RECTO), indicaciones)
    }

    @Test
    fun noSeSaltaLaEsquinaEnUnGiroDeIdaYVuelta() {
        val esquina = Haversine.destino(inicio, 0.0, 100.0)
        val fin = Haversine.destino(Haversine.destino(esquina, 90.0, 4.0), 180.0, 90.0)
        val motor = MotorGuia(
            RutaGuia(
                id = 2,
                nombre = "Ida y vuelta",
                puntos = listOf(
                    PuntoRuta(1, "Inicio", "Inicio", "", inicio),
                    PuntoRuta(2, "Esquina", "Giro", "", esquina),
                    PuntoRuta(3, "Fin", "Destino", "", fin)
                )
            )
        )
        val estado = motor.actualizar(Haversine.destino(inicio, 0.0, 50.0), 0.0)
        assertEquals(1, estado.indiceObjetivo)
        assertEquals(Indicacion.RECTO, estado.indicacion)
    }
}
