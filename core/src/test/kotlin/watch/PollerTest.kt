package watch

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PollerTest {
    private class FakeFetcher : FeedFetcher {
        var next: () -> String = { error("not set") }
        override suspend fun fetch(): String = next()
    }

    private class FakeStore(var data: Map<String, Event>? = null) : SnapshotStore {
        var saves = 0
        override fun load() = data
        override fun save(s: Map<String, Event>) { data = s; saves++ }
    }

    private fun feed(vararg ids: String, onSale: Boolean = true) =
        "[{\"slides\":[" + ids.joinToString(",") {
            """{"slideType":5,"objectRef":"$it","title":"t$it","url":"/$it","isVisible":$onSale}"""
        } + "]}]"

    private val fetcher = FakeFetcher()
    private val store = FakeStore()
    private val health = Health()
    private val poller = Poller(fetcher, store, health)

    private fun poll() = runBlocking { poller.pollOnce() }

    @Test
    fun firstPollSeedsAndSavesNoAlerts() {
        fetcher.next = { feed("a", "b") }
        val r = poll()
        assertTrue(r.alerts.isEmpty())
        assertEquals(Outcome.OK, r.outcome)
        assertEquals(setOf("a", "b"), store.data!!.keys)
    }

    @Test
    fun secondPollWithNewEventAlerts() {
        fetcher.next = { feed("a") }
        poll()
        fetcher.next = { feed("a", "b") }
        val r = poll()
        assertEquals(listOf("b"), r.alerts.map { it.event.id })
    }

    @Test
    fun fetchExceptionIsFailedAndSnapshotUntouched() {
        fetcher.next = { feed("a") }
        poll()
        val before = store.data
        val saves = store.saves
        fetcher.next = { throw java.io.IOException("boom") }
        val r = poll()
        assertEquals(Outcome.FAILED, r.outcome)
        assertTrue(r.alerts.isEmpty())
        assertEquals(before, store.data)
        assertEquals(saves, store.saves)
    }

    @Test
    fun emptyFeedDoesNotWipeSnapshot() {
        fetcher.next = { feed("a", "b") }
        poll()
        fetcher.next = { "[]" }
        val r = poll()
        assertEquals(Outcome.SHAPE_CHANGED, r.outcome)
        assertEquals(setOf("a", "b"), store.data!!.keys)
    }

    @Test
    fun afterEmptyFeedRealFeedProducesNoStorm() {
        fetcher.next = { feed("a", "b") }
        poll()
        fetcher.next = { "<html>Just a moment</html>" }
        poll()
        fetcher.next = { feed("a", "b", "c") }
        val r = poll()
        assertEquals(listOf("c"), r.alerts.map { it.event.id })
    }

    @Test
    fun fiveFailuresReportWentBlind() {
        fetcher.next = { throw java.io.IOException("down") }
        val events = (1..5).map { poll().health }
        assertEquals(listOf(null, null, null, null, HealthEvent.WENT_BLIND), events)
    }

    @Test
    fun recoveryReported() {
        fetcher.next = { throw java.io.IOException("down") }
        repeat(5) { poll() }
        fetcher.next = { feed("a") }
        assertEquals(HealthEvent.RECOVERED, poll().health)
    }
}
