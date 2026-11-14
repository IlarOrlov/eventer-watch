package watch

/** The one event line the customer cares about: "מסיבתה" (links start with `mesibata`). */
object SeriesFilter {
    private const val PREFIX = "mesibata"
    private const val TITLE_WORD = "מסיבתה"

    fun codeOf(url: String): String = url.substringBefore('?').trimEnd('/').substringAfterLast('/')

    fun isSeriesCode(code: String) = code.lowercase().startsWith(PREFIX)

    fun matches(e: Event) = isSeriesCode(codeOf(e.url)) || e.title.contains(TITLE_WORD)
}

object Sitemap {
    fun codes(xml: String): List<String> =
        Regex("<loc>([^<]+)</loc>").findAll(xml).map { SeriesFilter.codeOf(it.groupValues[1]) }.toList()
}

object Candidates {
    /** For each link family (`mesibatapijama`, `mesibatapijama2`, ...) the next number nobody has used yet. */
    fun next(known: Set<String>): List<String> {
        val maxByFamily = sortedMapOf<String, Int>()
        for (code in known.filter(SeriesFilter::isSeriesCode)) {
            val family = code.trimEnd { it.isDigit() }
            val n = code.removePrefix(family).toIntOrNull() ?: 1
            maxByFamily[family] = maxOf(maxByFamily[family] ?: 0, n)
        }
        return maxByFamily.map { (family, max) -> family + (max + 1) }.filter { it !in known }
    }
}

sealed interface ProbeResult {
    data class Exists(val event: Event) : ProbeResult
    object Missing : ProbeResult
}

interface SitemapSource {
    /** Link codes from the newest sitemap file. Throws on network errors. */
    suspend fun latestCodes(): List<String>
}

interface EventProbe {
    /** Exists when Eventer knows the link (HTTP 200), Missing on 403/404. Throws on other errors. */
    suspend fun probe(code: String): ProbeResult
}

interface SeriesStore {
    fun load(): Set<String>?
    fun save(codes: Set<String>)
}

class SeriesWatcher(
    private val sitemap: SitemapSource,
    private val probe: EventProbe,
    private val store: SeriesStore,
    private val probesPerCheck: Int = 4,
    private val sitemapEvery: Int = 2,
) {
    private var checks = 0
    private var cursor = 0

    suspend fun check(): List<Alert> {
        val loaded = store.load()
        val known = (loaded ?: emptySet()).toMutableSet()
        val alerts = mutableListOf<Alert>()
        val readSitemap = checks % sitemapEvery == 0
        checks++

        if (readSitemap) {
            val codes = try { sitemap.latestCodes().filter(SeriesFilter::isSeriesCode) } catch (e: Exception) { null }
            if (codes != null) {
                if (loaded == null) { // first run: remember what exists, alert on nothing
                    store.save(known + codes)
                    return emptyList()
                }
                for (c in codes) if (known.add(c)) alerts += alertFor(c)
            }
        }
        if (loaded == null && alerts.isEmpty() && known.isEmpty()) return emptyList()

        val candidates = Candidates.next(known)
        if (candidates.isNotEmpty() && probesPerCheck > 0) {
            repeat(minOf(probesPerCheck, candidates.size)) {
                val code = candidates[cursor % candidates.size]
                cursor++
                when (val r = try { probe.probe(code) } catch (e: Exception) { null }) {
                    is ProbeResult.Exists -> if (known.add(code)) alerts += Alert.NewEvent(r.event)
                    else -> Unit
                }
            }
        }
        if (known.size != (loaded?.size ?: 0)) store.save(known)
        return alerts
    }

    private suspend fun alertFor(code: String): Alert {
        val r = try { probe.probe(code) } catch (e: Exception) { null }
        val event = (r as? ProbeResult.Exists)?.event
            ?: Event(code, code, "https://www.eventer.co.il/$code", false)
        return Alert.NewEvent(event)
    }
}
