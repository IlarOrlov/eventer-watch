package watch

import kotlin.coroutines.cancellation.CancellationException

data class PollResult(val alerts: List<Alert>, val health: HealthEvent?, val outcome: Outcome)

class Poller(
    private val fetcher: FeedFetcher,
    private val store: SnapshotStore,
    private val health: Health,
) {
    suspend fun pollOnce(): PollResult {
        val body = try {
            fetcher.fetch()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return failed(Outcome.FAILED)
        }
        return when (val parsed = FeedParser.parse(body)) {
            is ParseResult.ShapeChanged -> failed(Outcome.SHAPE_CHANGED)
            is ParseResult.Ok -> {
                try {
                    val previous = store.load()
                    val alerts = diff(previous, parsed.events)
                    store.save(snapshotOf(parsed.events, previous))
                    PollResult(alerts, health.record(Outcome.OK), Outcome.OK)
                } catch (e: Exception) {
                    failed(Outcome.FAILED)
                }
            }
        }
    }

    private fun failed(o: Outcome) = PollResult(emptyList(), health.record(o), o)
}
