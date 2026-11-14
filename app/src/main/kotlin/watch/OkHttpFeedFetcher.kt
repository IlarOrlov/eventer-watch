package watch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class OkHttpFeedFetcher(
    private val client: OkHttpClient,
    private val feedUrl: String,
) : FeedFetcher {
    override suspend fun fetch(): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(feedUrl)
            .header("User-Agent", "EventerWatch/1.0")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            resp.body?.string() ?: throw IOException("empty body")
        }
    }
}
