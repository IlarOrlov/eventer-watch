package watch

import android.content.Context
import android.content.Intent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

class FileSeriesStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun roundTripAndMissingAndCorrupt() {
        val f = java.io.File(tmp.root, "series.json")
        val s = FileSeriesStore(f)
        assertNull(s.load())
        s.save(setOf("mesibatapijama", "mesibatapijama2"))
        assertEquals(setOf("mesibatapijama", "mesibatapijama2"), FileSeriesStore(f).load())
        f.writeText("{bad")
        assertNull(s.load())
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainControlsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun main() = Robolectric.buildActivity(MainActivity::class.java).setup().get()

    @Test fun buyerButtonOpensProfileScreen() {
        val a = main()
        a.findViewById<Button>(MainActivity.ID_PROFILE).performClick()
        assertEquals(ProfileActivity::class.java.name, shadowOf(a).nextStartedActivity.component!!.className)
    }

    @Test fun buyerCardSaysWhenNoDetailsAreSaved() {
        val a = main()
        assertTrue(a.findViewById<TextView>(MainActivity.ID_PROFILE_SUMMARY).text.toString().contains("Add"))
    }

    @Test fun seriesOnlyIsOnByDefaultAndToggles() {
        assertTrue(AppPrefs.seriesOnly(context))
        val a = main()
        a.findViewById<Button>(MainActivity.ID_SERIES_ONLY).performClick()
        assertFalse(AppPrefs.seriesOnly(context))
    }

    @Test fun autoCheckoutToggles() {
        assertTrue(AppPrefs.autoCheckout(context))
        val a = main()
        a.findViewById<Button>(MainActivity.ID_AUTO_CHECKOUT).performClick()
        assertFalse(AppPrefs.autoCheckout(context))
    }

    @Test fun openLinkFieldStartsCheckoutForEventerLinks() {
        val a = main()
        a.findViewById<EditText>(MainActivity.ID_LINK).setText("https://www.eventer.co.il/mesibatapijama")
        a.findViewById<Button>(MainActivity.ID_LINK_GO).performClick()
        val i: Intent = shadowOf(a).nextStartedActivity
        assertEquals(CheckoutActivity::class.java.name, i.component!!.className)
        assertEquals("https://www.eventer.co.il/mesibatapijama", i.getStringExtra(CheckoutActivity.EXTRA_URL))
    }

    @Test fun openLinkFieldRejectsOtherSites() {
        val a = main()
        a.findViewById<EditText>(MainActivity.ID_LINK).setText("https://example.com/x")
        a.findViewById<Button>(MainActivity.ID_LINK_GO).performClick()
        assertNull(shadowOf(a).nextStartedActivity)
    }
}
