package com.proyecto.ecoguia.bluetooth

import com.proyecto.ecoguia.guia.Indicacion
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
    fun rumboNegativoSeNormaliza() {
        assertEquals(MensajeEsp32.Rumbo(350.0, 2.0), ParserMensajesEsp32.parsear("Y,-10.0,2.0"))
    }

    @Test
    fun posicionSinFixOConCamposExtra() {
        assertEquals(
            MensajeEsp32.Posicion(13.7189, -89.2030, true),
            ParserMensajesEsp32.parsear("P,13.7189,-89.2030")
        )
        assertEquals(
            MensajeEsp32.Posicion(13.7189, -89.2030, true),
            ParserMensajesEsp32.parsear("P,13.7189,-89.2030,1,8,1.2")
        )
        assertEquals(MensajeEsp32.Posicion(0.0, 0.0, false), ParserMensajesEsp32.parsear("P,0.0,0.0"))
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
    fun elLatidoRepiteLaIndicacionVigente() {
        assertEquals("L", ParserMensajesEsp32.mensajeLatido(Indicacion.IZQUIERDA))
        assertEquals("R", ParserMensajesEsp32.mensajeLatido(Indicacion.DERECHA))
        assertEquals("A", ParserMensajesEsp32.mensajeLatido(Indicacion.RECTO))
        assertEquals("H", ParserMensajesEsp32.mensajeLatido(null))
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
            "P,13.7",
            "P,95,-89.2,1",
            "P,13.7,-189.2,1",
            "P,13.7,-89.2,2",
            "Y,1,2,3"
        ).forEach { linea ->
            assertTrue("\"$linea\" debió ser inválida", ParserMensajesEsp32.parsear(linea) is MensajeEsp32.Invalido)
        }
    }
}
