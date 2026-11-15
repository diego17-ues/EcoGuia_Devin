package com.proyecto.ecoguia.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserMensajesEsp32Test {

    @Test
    fun rumboValido() {
        assertEquals(MensajeEsp32.Rumbo(123.4, -5.5), ParserMensajesEsp32.parsear("Y,123.4,-5.5"))
        assertEquals(MensajeEsp32.Rumbo(0.0, 0.0), ParserMensajesEsp32.parsear("Y,360,0\r\n"))
    }

    @Test
    fun posicionConYSinFix() {
        assertEquals(
            MensajeEsp32.Posicion(13.718912, -89.203045, true),
            ParserMensajesEsp32.parsear("P,13.718912,-89.203045,1")
        )
        assertEquals(
            MensajeEsp32.Posicion(0.0, 0.0, false),
            ParserMensajesEsp32.parsear("P,0.000000,0.000000,0")
        )
    }

    @Test
    fun lineasMalFormadasNoLanzanExcepcion() {
        listOf(
            "",
            "   ",
            "X,1,2",
            "Y,abc,1",
            "Y,10",
            "Y,400,1",
            "Y,10,NaN",
            "P,13.7,-89.2",
            "P,95,-89.2,1",
            "P,13.7,-189.2,1",
            "P,13.7,-89.2,2",
            "Y,1,2,3"
        ).forEach { linea ->
            assertTrue("\"$linea\" debió ser inválida", ParserMensajesEsp32.parsear(linea) is MensajeEsp32.Invalido)
        }
    }
}
