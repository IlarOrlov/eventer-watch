package watch

sealed interface Alert {
    val event: Event

    data class NewEvent(override val event: Event) : Alert
    data class SaleOpened(override val event: Event) : Alert
}

/** previous == null means first run: seed silently. */
fun diff(previous: Map<String, Event>?, current: List<Event>): List<Alert> {
    if (previous == null) return emptyList()
    return current.mapNotNull { e ->
        val old = previous[e.id]
        when {
            old == null -> Alert.NewEvent(e)
            !old.onSale && e.onSale -> Alert.SaleOpened(e)
            else -> null
        }
    }
}

/** Keeps events that vanished from the feed so their reappearance is not "new". */
fun snapshotOf(current: List<Event>, previous: Map<String, Event>?): Map<String, Event> {
    val result = LinkedHashMap<String, Event>(previous ?: emptyMap())
    current.forEach { result[it.id] = it }
    return result
}
