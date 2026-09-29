package ru.vinteno.finni

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.vinteno.finni.ui.components.jarFillFraction

class JarFillTest {
    @Test fun threeJarsUseOneScaleAndDoNotStopAtHundred() {
        assertEquals(0f, jarFillFraction(0, 25), 0.001f)
        assertEquals(0.2f, jarFillFraction(5, 25), 0.001f)
        assertEquals(0.4f, jarFillFraction(10, 25), 0.001f)
        assertEquals(1f, jarFillFraction(25, 25), 0.001f)
        assertEquals(0.5f, jarFillFraction(105, 210), 0.001f)
        assertEquals(1f, jarFillFraction(210, 210), 0.001f)
    }
}
