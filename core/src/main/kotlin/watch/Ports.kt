package watch

/** Throws on network error, non-2xx, or 429. */
interface FeedFetcher {
    suspend fun fetch(): String
}

interface SnapshotStore {
    fun load(): Map<String, Event>?
    fun save(s: Map<String, Event>)
}
