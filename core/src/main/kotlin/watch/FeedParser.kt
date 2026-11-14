package watch

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** Parses Eventer's `/sliders/list.js`. Event slides have slideType 5. */
object FeedParser {
    const val BASE_URL = "https://www.eventer.co.il"
    private const val EVENT_SLIDE = 5

    fun parse(body: String): ParseResult {
        val root = try {
            Json.parseToJsonElement(body)
        } catch (e: Exception) {
            return ParseResult.ShapeChanged("not json")
        }
        if (root !is JsonArray) return ParseResult.ShapeChanged("not an array")

        val events = LinkedHashMap<String, Event>()
        for (slider in root) {
            val slides = (slider as? JsonObject)?.get("slides") as? JsonArray ?: continue
            for (slide in slides) {
                val s = slide as? JsonObject ?: continue
                if (s.int("slideType") != EVENT_SLIDE) continue
                val id = s.str("objectRef") ?: continue
                val url = s.str("url") ?: continue
                val title = s.str("title") ?: continue
                events.putIfAbsent(id, Event(id, title, absolute(url), s.bool("isVisible") ?: false))
            }
        }
        if (events.isEmpty()) return ParseResult.ShapeChanged("empty")
        return ParseResult.Ok(events.values.toList())
    }

    private fun absolute(url: String) =
        if (url.startsWith("http")) url else BASE_URL + (if (url.startsWith("/")) url else "/$url")

    private fun JsonObject.prim(k: String): JsonPrimitive? = get(k) as? JsonPrimitive
    private fun JsonObject.str(k: String) = prim(k)?.takeIf { it.isString }?.contentOrNull
    private fun JsonObject.int(k: String) = prim(k)?.intOrNull
    private fun JsonObject.bool(k: String) = prim(k)?.booleanOrNull
}
