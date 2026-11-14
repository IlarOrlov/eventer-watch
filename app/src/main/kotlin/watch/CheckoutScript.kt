package watch

import kotlinx.serialization.json.Json

object CheckoutScript {
    private val json = Json { encodeDefaults = true }

    /** The engine source followed by a call that starts it with the buyer profile (JSON is valid JS). */
    fun build(engine: String, profile: Profile): String =
        engine + "\n;window.__ew && window.__ew.start(" + json.encodeToString(Profile.serializer(), profile) + ", {});"
}
