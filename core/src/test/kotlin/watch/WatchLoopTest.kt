package watch

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WatchLoopTest {
    private class CountingFetcher(var fail: Boolean = false) : FeedFetcher {
        var calls = 0
        override suspend fun fetch(): String {
            calls++
            if (fail) throw java.io.IOException("down")
            return """[{"slides":[{"slideType":5,"objectRef":"a","title":"t","url":"/a","isVisible":true}]}]"""
        }
    }

    private class MemStore : SnapshotStore {
        var d: Map<String, Event>? = null
        override fun load() = d
        override fun save(s: Map<String, Event>) { d = s }
    }

    private fun loop(f: FeedFetcher, results: MutableList<PollResult> = mutableListOf()) =
        WatchLoop(Poller(f, MemStore(), Health()), Backoff(jitterMs = 0)) { results.add(it) }

    @Test
    fun loopWaitsBackoffDelayBetweenPolls() = runTest {
        val f = CountingFetcher()
        backgroundScope.launch { loop(f).run() }
        runCurrent()
        assertEquals(1, f.calls)
        advanceTimeBy(29_999); runCurrent()
        assertEquals(1, f.calls)
        advanceTimeBy(2); runCurrent()
        assertEquals(2, f.calls)
    }

    @Test
    fun failuresBackOffExponentially() = runTest {
        val f = CountingFetcher(fail = true)
        backgroundScope.launch { loop(f).run() }
        runCurrent()
        assertEquals(1, f.calls)
        advanceTimeBy(60_001); runCurrent()   // 1 failure -> 60s wait
        assertEquals(2, f.calls)
        advanceTimeBy(119_000); runCurrent()  // 2 failures -> 120s wait, not yet
        assertEquals(2, f.calls)
        advanceTimeBy(2_000); runCurrent()
        assertEquals(3, f.calls)
    }

    @Test
    fun storeFailureDoesNotKillLoop() = runTest {
        val f = CountingFetcher()
        val brokenStore = object : SnapshotStore {
            override fun load(): Map<String, Event>? = null
            override fun save(s: Map<String, Event>) = throw java.io.IOException("disk full")
        }
        val results = mutableListOf<PollResult>()
        backgroundScope.launch { WatchLoop(Poller(f, brokenStore, Health()), Backoff(jitterMs = 0)) { results.add(it) }.run() }
        runCurrent()
        advanceTimeBy(60_001); runCurrent()
        assertEquals(2, f.calls)
        assertEquals(Outcome.FAILED, results.first().outcome)
    }

    @Test
    fun successResetsBackoffAndReportsEveryResult() = runTest {
        val f = CountingFetcher(fail = true)
        val results = mutableListOf<PollResult>()
        backgroundScope.launch { loop(f, results).run() }
        runCurrent()
        f.fail = false
        advanceTimeBy(60_001); runCurrent()   // succeeds
        advanceTimeBy(30_001); runCurrent()   // back to 30s cadence
        assertEquals(3, f.calls)
        assertEquals(listOf(Outcome.FAILED, Outcome.OK, Outcome.OK), results.map { it.outcome })
    }
}
