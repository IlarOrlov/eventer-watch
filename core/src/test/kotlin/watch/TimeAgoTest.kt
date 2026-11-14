package watch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TimeAgoTest {
    private val now = 1_000_000_000L

    @Test fun neverWhenNoTimestamp() = assertEquals("never", TimeAgo.format(now, 0))
    @Test fun secondsAreShownUnderAMinute() = assertEquals("12 s ago", TimeAgo.format(now, now - 12_000))
    @Test fun zeroSecondsIsJustNow() = assertEquals("just now", TimeAgo.format(now, now - 800))
    @Test fun minutes() = assertEquals("3 min ago", TimeAgo.format(now, now - 3 * 60_000 - 5_000))
    @Test fun hours() = assertEquals("2 h ago", TimeAgo.format(now, now - 2 * 3_600_000 - 60_000))
    @Test fun days() = assertEquals("3 d ago", TimeAgo.format(now, now - 3 * 86_400_000L))
    @Test fun clockSkewIntoTheFutureIsJustNow() = assertEquals("just now", TimeAgo.format(now, now + 5_000))
}
