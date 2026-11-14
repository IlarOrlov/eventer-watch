package watch

import kotlinx.serialization.Serializable

@Serializable
data class Event(val id: String, val title: String, val url: String, val onSale: Boolean)

sealed interface ParseResult {
    data class Ok(val events: List<Event>) : ParseResult
    data class ShapeChanged(val reason: String) : ParseResult
}
