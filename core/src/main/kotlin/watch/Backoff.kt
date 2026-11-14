package watch

import kotlin.random.Random

class Backoff(
    val baseMs: Long = 30_000,
    val capMs: Long = 600_000,
    val jitterMs: Long = 3_000,
    /** Failures that keep the base delay, so "blind" is noticed within ~2 minutes. */
    val graceFailures: Int = 4,
) {
    fun nextDelayMs(consecutiveFailures: Int, random: Random = Random.Default): Long {
        val shift = (consecutiveFailures - graceFailures).coerceIn(0, 30)
        val raw = (baseMs shl shift).coerceIn(0, capMs)
        val jitter = if (jitterMs > 0) random.nextLong(-jitterMs, jitterMs + 1) else 0L
        return (raw + jitter).coerceAtMost(capMs)
    }
}
