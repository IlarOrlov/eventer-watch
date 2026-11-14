package watch

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HealthTest {
    @Test
    fun blindAfterFiveConsecutiveFailures() {
        val h = Health()
        repeat(4) { assertNull(h.record(Outcome.FAILED)) }
        assertFalse(h.isBlind)
        assertEquals(HealthEvent.WENT_BLIND, h.record(Outcome.FAILED))
        assertTrue(h.isBlind)
        assertNull(h.record(Outcome.FAILED))
        assertNull(h.record(Outcome.FAILED))
    }

    @Test
    fun shapeChangeCountsAsFailure() {
        val h = Health()
        repeat(4) { h.record(Outcome.SHAPE_CHANGED) }
        assertEquals(HealthEvent.WENT_BLIND, h.record(Outcome.FAILED))
    }

    @Test
    fun okAfterBlindEmitsRecovered() {
        val h = Health()
        repeat(5) { h.record(Outcome.FAILED) }
        assertEquals(HealthEvent.RECOVERED, h.record(Outcome.OK))
        assertFalse(h.isBlind)
        assertNull(h.record(Outcome.OK))
    }

    @Test
    fun okResetsCounter() {
        val h = Health()
        repeat(4) { h.record(Outcome.FAILED) }
        assertNull(h.record(Outcome.OK))
        repeat(4) { assertNull(h.record(Outcome.FAILED)) }
        assertFalse(h.isBlind)
    }
}
