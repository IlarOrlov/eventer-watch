package watch

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DiffTest {
    private fun ev(id: String, onSale: Boolean = false) = Event(id, "t$id", "https://x/$id", onSale)
    private fun snap(vararg e: Event) = e.associateBy { it.id }

    @Test
    fun firstRunSeedsSilently() {
        assertTrue(diff(null, listOf(ev("a"), ev("b", true))).isEmpty())
    }

    @Test
    fun newEventAlerts() {
        val alerts = diff(snap(ev("a")), listOf(ev("a"), ev("b")))
        assertEquals(listOf<Alert>(Alert.NewEvent(ev("b"))), alerts)
    }

    @Test
    fun newEventAlreadyOnSaleIsOneAlert() {
        val alerts = diff(snap(ev("a")), listOf(ev("a"), ev("b", true)))
        assertEquals(listOf<Alert>(Alert.NewEvent(ev("b", true))), alerts)
    }

    @Test
    fun notOnSaleToOnSaleAlerts() {
        val alerts = diff(snap(ev("a", false)), listOf(ev("a", true)))
        assertEquals(listOf<Alert>(Alert.SaleOpened(ev("a", true))), alerts)
    }

    @Test
    fun unchangedProducesNothing() {
        assertTrue(diff(snap(ev("a", true), ev("b")), listOf(ev("a", true), ev("b"))).isEmpty())
    }

    @Test
    fun vanishedThenReappearedIsNotNew() {
        val s1 = snap(ev("a"), ev("b"))
        val s2 = snapshotOf(listOf(ev("a")), s1)
        assertTrue(s2.containsKey("b"))
        assertTrue(diff(s2, listOf(ev("a"), ev("b"))).isEmpty())
    }

    @Test
    fun reappearedAndNowOnSaleAlertsSaleOpened() {
        val s2 = snapshotOf(listOf(ev("a")), snap(ev("a"), ev("b", false)))
        val alerts = diff(s2, listOf(ev("a"), ev("b", true)))
        assertEquals(listOf<Alert>(Alert.SaleOpened(ev("b", true))), alerts)
    }

    @Test
    fun manyNewEventsReturnedInFeedOrder() {
        val alerts = diff(snap(ev("a")), listOf(ev("z"), ev("a"), ev("m")))
        assertEquals(listOf("z", "m"), alerts.map { it.event.id })
    }
}
