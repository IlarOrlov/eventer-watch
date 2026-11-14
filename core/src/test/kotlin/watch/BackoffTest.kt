package watch

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

class BackoffTest {
    private val b = Backoff()

    @Test
    fun zeroFailuresIsThirtySecondsWithJitter() {
        repeat(200) {
            val d = b.nextDelayMs(0, Random(it))
            assertTrue(d in 27_000..33_000, "was $d")
        }
    }

    @Test
    fun delaysDoubleWithFailures() {
        val noJitter = Backoff(jitterMs = 0, graceFailures = 0)
        assertEquals(30_000, noJitter.nextDelayMs(0))
        assertEquals(60_000, noJitter.nextDelayMs(1))
        assertEquals(120_000, noJitter.nextDelayMs(2))
        assertEquals(240_000, noJitter.nextDelayMs(3))
    }

    @Test
    fun delayCapsAtTenMinutes() {
        val noJitter = Backoff(jitterMs = 0, graceFailures = 0)
        assertEquals(600_000, noJitter.nextDelayMs(5))
        assertEquals(600_000, noJitter.nextDelayMs(500))
    }

    @Test
    fun firstFailuresKeepBaseDelay() {
        val graceful = Backoff(jitterMs = 0)
        repeat(5) { assertEquals(30_000, graceful.nextDelayMs(it)) }
        assertEquals(60_000, graceful.nextDelayMs(5))
        assertEquals(600_000, graceful.nextDelayMs(50))
    }
}
