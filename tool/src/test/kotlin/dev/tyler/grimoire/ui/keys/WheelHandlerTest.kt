package dev.tyler.grimoire.ui.keys

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WheelHandlerTest {

    @Test
    fun wheelUpIs317() {
        assertEquals(WheelEvent.UP, WheelHandler.of(317), "317 is a wheel turn toward the top of the phone")
    }

    @Test
    fun wheelDownIs318() {
        assertEquals(WheelEvent.DOWN, WheelHandler.of(318), "318 is a wheel turn toward the bottom of the phone")
    }

    @Test
    fun wheelPressIs319() {
        assertEquals(WheelEvent.PRESS, WheelHandler.of(319), "319 is the wheel press")
    }

    @Test
    fun everyOtherKeyIsNull() {
        for (keyCode in intArrayOf(24, 25, 27, 80, 0, -1, 316, 320)) {
            assertNull(WheelHandler.of(keyCode), "key $keyCode is not a wheel event and must stay unconsumed")
        }
    }
}
