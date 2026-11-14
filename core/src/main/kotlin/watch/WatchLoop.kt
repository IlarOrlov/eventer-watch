package watch

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class WatchLoop(
    private val poller: Poller,
    private val backoff: Backoff,
    /** Extra detectors (e.g. the series watcher); their failures never affect the main feed's health. */
    private val extra: (suspend () -> List<Alert>)? = null,
    /** Decides which main-feed alerts are wanted (extra alerts are always kept). */
    private val feedFilter: (Alert) -> Boolean = { true },
    private val onResult: (PollResult) -> Unit,
) {
    suspend fun run() {
        var failures = 0
        while (currentCoroutineContext().isActive) {
            val result = poller.pollOnce()
            failures = if (result.outcome == Outcome.OK) 0 else failures + 1
            val more = try { extra?.invoke() ?: emptyList() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { emptyList() }
            onResult(result.copy(alerts = result.alerts.filter(feedFilter) + more))
            delay(backoff.nextDelayMs(failures))
        }
    }
}
