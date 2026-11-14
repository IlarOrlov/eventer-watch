package watch

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** What the checkout assistant script reports about the page it is driving. */
data class CheckoutStatus(val phase: String, val detail: String = "", val missing: List<String> = emptyList()) {
    /** The assistant has stopped on purpose and the person has to act (payment, CAPTCHA, unanswerable question). */
    val needsHuman: Boolean get() = phase in HUMAN_PHASES

    /** Tickets are not on sale yet: keep the page fresh until they are. */
    val shouldReload: Boolean get() = phase == "waiting"

    companion object {
        private val HUMAN_PHASES = setOf("payment", "human", "needs-input")

        fun parse(json: String): CheckoutStatus? = try {
            val o = Json.parseToJsonElement(json) as? JsonObject
            val phase = (o?.get("phase") as? JsonPrimitive)?.contentOrNull
            if (o == null || phase == null) null
            else CheckoutStatus(
                phase,
                (o["detail"] as? JsonPrimitive)?.contentOrNull ?: "",
                (o["missing"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList(),
            )
        } catch (e: Exception) {
            null
        }

        /** `WebView.evaluateJavascript` hands back the script's string result JSON-quoted. */
        fun fromWebView(raw: String?): CheckoutStatus? {
            if (raw == null || raw == "null") return null
            val inner = try { (Json.parseToJsonElement(raw) as? JsonPrimitive)?.contentOrNull } catch (e: Exception) { null }
            return inner?.let(::parse)
        }
    }
}
