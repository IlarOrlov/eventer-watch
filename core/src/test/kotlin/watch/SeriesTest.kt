package watch

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SeriesTest {
    @Test fun filterMatchesCodeOrHebrewTitle() {
        assertTrue(SeriesFilter.matches(Event("1", "x", "https://www.eventer.co.il/mesibatapijama", false)))
        assertTrue(SeriesFilter.matches(Event("1", "מסיבתה פיג׳מות הגדולה", "https://www.eventer.co.il/abc12", false)))
        assertTrue(SeriesFilter.matches(Event("1", "מסיבתה נוסטלגיה", "https://www.eventer.co.il/abc12", false)))
        assertFalse(SeriesFilter.matches(Event("1", "נועם צוריאלי", "https://www.eventer.co.il/vsh2f", false)))
    }

    @Test fun codeIsLastPathSegment() {
        assertEquals("mesibatapijama", SeriesFilter.codeOf("https://www.eventer.co.il/mesibatapijama"))
        assertEquals("abc", SeriesFilter.codeOf("https://www.eventer.co.il/abc?x=1"))
    }

    @Test fun sitemapCodesAreExtracted() {
        val xml = """<urlset><url><loc>https://www.eventer.co.il/mesibatapijama2</loc></url>
            <url><loc>https://www.eventer.co.il/vsh2f</loc></url></urlset>"""
        assertEquals(listOf("mesibatapijama2", "vsh2f"), Sitemap.codes(xml))
    }

    @Test fun candidatesAreTheNextNumberPerFamily() {
        val c = Candidates.next(setOf("mesibatapijama", "mesibatapijama2", "mesibatapijama3", "mesibatanostalgia", "mesibataexpotlv1", "mesibataexpotlv2"))
        assertTrue("mesibatapijama4" in c)
        assertTrue("mesibatanostalgia2" in c)
        assertTrue("mesibataexpotlv3" in c)
        assertFalse("mesibatapijama2" in c)
    }

    // ---- watcher ----

    private class Fakes {
        var sitemap: List<String> = emptyList()
        var sitemapFails = false
        val existing = mutableMapOf<String, Event>()
        var probes = mutableListOf<String>()
        var known: Set<String>? = null
    }

    private fun ev(code: String) = Event(code, "title $code", "https://www.eventer.co.il/$code", false)

    private fun watcher(f: Fakes, probesPerCheck: Int = 2, sitemapEvery: Int = 1) = SeriesWatcher(
        sitemap = object : SitemapSource {
            override suspend fun latestCodes(): List<String> =
                if (f.sitemapFails) throw java.io.IOException("x") else f.sitemap
        },
        probe = object : EventProbe {
            override suspend fun probe(code: String): ProbeResult {
                f.probes += code
                return f.existing[code]?.let { ProbeResult.Exists(it) } ?: ProbeResult.Missing
            }
        },
        store = object : SeriesStore {
            override fun load() = f.known
            override fun save(codes: Set<String>) { f.known = codes }
        },
        probesPerCheck = probesPerCheck,
        sitemapEvery = sitemapEvery,
    )

    private fun check(w: SeriesWatcher) = runBlocking { w.check() }

    @Test fun firstRunSeedsSilently() {
        val f = Fakes().apply { sitemap = listOf("mesibatapijama", "vsh2f") }
        assertTrue(check(watcher(f)).isEmpty())
        assertEquals(setOf("mesibatapijama"), f.known)
    }

    @Test fun newSeriesCodeInSitemapAlertsOnce() {
        val f = Fakes().apply { sitemap = listOf("mesibatapijama"); existing["mesibatapijama5"] = ev("mesibatapijama5") }
        val w = watcher(f)
        check(w)
        f.sitemap = listOf("mesibatapijama", "mesibatapijama5", "zzz")
        val alerts = check(w)
        assertEquals(listOf("mesibatapijama5"), alerts.map { SeriesFilter.codeOf(it.event.url) })
        assertTrue(check(w).isEmpty())
    }

    @Test fun probeHitAlertsEvenWhenSitemapLags() {
        val f = Fakes().apply { sitemap = listOf("mesibatapijama3") }
        val w = watcher(f, probesPerCheck = 5)
        check(w)
        f.existing["mesibatapijama4"] = ev("mesibatapijama4")
        val alerts = check(w)
        assertEquals(listOf("mesibatapijama4"), alerts.map { SeriesFilter.codeOf(it.event.url) })
        assertTrue(check(w).isEmpty())
    }

    @Test fun sitemapFailureDoesNotStopProbing() {
        val f = Fakes().apply { sitemap = listOf("mesibatapijama3") }
        val w = watcher(f, probesPerCheck = 5)
        check(w)
        f.sitemapFails = true
        f.existing["mesibatapijama4"] = ev("mesibatapijama4")
        assertEquals(1, check(w).size)
    }

    @Test fun probesRotateThroughEveryCandidate() {
        val f = Fakes().apply { sitemap = listOf("mesibatapijama", "mesibatanostalgia", "mesibatalehet") }
        val w = watcher(f, probesPerCheck = 1)
        check(w)
        repeat(3) { check(w) }
        assertEquals(setOf("mesibatapijama2", "mesibatanostalgia2", "mesibatalehet2"), f.probes.toSet())
    }

    @Test fun sitemapIsOnlyReadEveryNthCheck() {
        var reads = 0
        val f = Fakes()
        val w = SeriesWatcher(
            sitemap = object : SitemapSource { override suspend fun latestCodes(): List<String> { reads++; return listOf("mesibatapijama") } },
            probe = object : EventProbe { override suspend fun probe(code: String) = ProbeResult.Missing },
            store = object : SeriesStore { override fun load() = f.known; override fun save(codes: Set<String>) { f.known = codes } },
            probesPerCheck = 0, sitemapEvery = 2,
        )
        repeat(4) { check(w) }
        assertEquals(2, reads)
    }
}
