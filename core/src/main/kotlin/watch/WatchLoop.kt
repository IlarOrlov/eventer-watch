package watch

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class WatchLoop(
    private val poller: Poller,
    private val backoff: Backoff,
    private val onResult: (PollResult) -> Unit,
) {
    suspend fun run() {
        var failures = 0
        while (currentCoroutineContext().isActive) {
            val result = poller.pollOnce()
            failures = if (result.outcome == Outcome.OK) 0 else failures + 1
            onResult(result)
            delay(backoff.nextDelayMs(failures))
        }
    }
}
