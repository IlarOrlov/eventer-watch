package watch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URI

private const val UA = "EventerWatch/1.0"

private fun OkHttpClient.get(url: String) =
    newCall(Request.Builder().url(url).header("User-Agent", UA).build())

/**
 * Asks Eventer whether a link exists. A real link answers 200 and an unknown one 403/404.
 * Only the event id and name are read from the response; nothing else is kept.
 */
class OkHttpEventProbe(private val client: OkHttpClient, private val base: String = "https://www.eventer.co.il") : EventProbe {
    override suspend fun probe(code: String): ProbeResult = withContext(Dispatchers.IO) {
        require(Regex("[A-Za-z0-9_-]{1,60}").matches(code)) { "bad link code" }
        client.get("$base/events/explainNames/$code.js").execute().use { resp ->
            when {
                resp.code == 403 || resp.code == 404 -> ProbeResult.Missing
                !resp.isSuccessful -> throw IOException("HTTP ${resp.code}")
                else -> {
                    val event = (Json.parseToJsonElement(resp.body?.string() ?: "") as? JsonObject)?.get("event") as? JsonObject
                    val id = (event?.get("_id") as? JsonPrimitive)?.contentOrNull ?: code
                    val name = (event?.get("name") as? JsonPrimitive)?.contentOrNull ?: code
                    ProbeResult.Exists(Event(id, name, "$base/$code", false))
                }
            }
        }
    }
}

/** Reads the newest sitemap file, where freshly created links appear. The index is re-read every 10 minutes. */
class OkHttpSitemapSource(
    private val client: OkHttpClient,
    private val base: String = "https://www.eventer.co.il",
    private val clock: () -> Long = System::currentTimeMillis,
) : SitemapSource {
    private var lastPath: String? = null
    private var indexReadAt = 0L

    override suspend fun latestCodes(): List<String> = withContext(Dispatchers.IO) {
        if (lastPath == null || clock() - indexReadAt > 600_000) {
            val index = client.get("$base/sitemap.xml").execute().use { r ->
                if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                r.body?.string() ?: ""
            }
            val last = Regex("<loc>([^<]+)</loc>").findAll(index).map { it.groupValues[1] }.lastOrNull()
                ?: throw IOException("empty sitemap index")
            lastPath = URI(last).path
            indexReadAt = clock()
        }
        client.get("$base$lastPath").execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            Sitemap.codes(r.body?.string() ?: "")
        }
    }
}
