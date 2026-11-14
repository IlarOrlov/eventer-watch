package watch

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CheckoutStatusTest {
    @Test fun parsesPhaseDetailAndMissing() {
        val s = CheckoutStatus.parse("""{"phase":"needs-input","detail":"Needs you","missing":["a","b"],"selected":2}""")!!
        assertEquals("needs-input", s.phase)
        assertEquals(listOf("a", "b"), s.missing)
        assertTrue(s.needsHuman)
    }

    @Test fun paymentAndCaptchaNeedAHuman() {
        assertTrue(CheckoutStatus.parse("""{"phase":"payment"}""")!!.needsHuman)
        assertTrue(CheckoutStatus.parse("""{"phase":"human"}""")!!.needsHuman)
    }

    @Test fun progressPhasesDoNot() {
        for (p in listOf("waiting", "selecting", "continuing", "filling", "submitting"))
            assertFalse(CheckoutStatus.parse("""{"phase":"$p"}""")!!.needsHuman, p)
    }

    @Test fun waitingIsTheOnlyPhaseThatReloads() {
        assertTrue(CheckoutStatus.parse("""{"phase":"waiting"}""")!!.shouldReload)
        assertFalse(CheckoutStatus.parse("""{"phase":"filling"}""")!!.shouldReload)
    }

    @Test fun garbageOrNullIsNull() {
        assertNull(CheckoutStatus.parse("null"))
        assertNull(CheckoutStatus.parse(""))
        assertNull(CheckoutStatus.parse("{not json"))
    }

    @Test fun webViewQuotedResultIsUnwrapped() {
        // evaluateJavascript returns the JSON string wrapped in quotes with escapes
        val raw = "\"{\\\"phase\\\":\\\"payment\\\",\\\"detail\\\":\\\"x\\\",\\\"missing\\\":[]}\""
        assertEquals("payment", CheckoutStatus.fromWebView(raw)!!.phase)
        assertNull(CheckoutStatus.fromWebView("null"))
    }
}
