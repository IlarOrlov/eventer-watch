package watch

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlarmActivityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val nm get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val url = "https://www.eventer.co.il/אירוע"

    private fun launch() = Robolectric.buildActivity(
        AlarmActivity::class.java,
        Intent(context, AlarmActivity::class.java)
            .putExtra(AlarmActivity.EXTRA_URLS, arrayOf(url, "https://www.eventer.co.il/b"))
            .putExtra(AlarmActivity.EXTRA_TITLES, arrayOf("נועם צוריאלי", "Other")),
    ).setup().get()

    private fun raiseFirst() {
        Alarm(context).also { it.ensureChannels() }
            .raise(listOf(Alert.NewEvent(Event("a", "נועם צוריאלי", url, false))))
    }

    @Test
    fun showsEveryTitle() {
        val a = launch()
        val shown = a.findViewById<TextView>(AlarmActivity.ID_TITLES).text.toString()
        assertTrue(shown.contains("נועם צוריאלי") && shown.contains("Other"))
    }

    @Test
    fun openButtonOpensFirstUrlStopsRingingAndCloses() {
        raiseFirst()
        val a = launch()
        a.findViewById<Button>(AlarmActivity.ID_OPEN).performClick()
        val view = shadowOf(a).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, view.action)
        assertEquals(url, android.net.Uri.decode(view.data.toString()))
        assertTrue(shadowOf(nm).allNotifications.none { it.channelId == Alarm.CHANNEL_ALARM })
        assertTrue(a.isFinishing)
    }

    @Test
    fun dismissButtonStopsRingingWithoutOpeningAnything() {
        raiseFirst()
        val a = launch()
        a.findViewById<Button>(AlarmActivity.ID_DISMISS).performClick()
        assertNull(shadowOf(a).nextStartedActivity)
        assertTrue(shadowOf(nm).allNotifications.none { it.channelId == Alarm.CHANNEL_ALARM })
        assertTrue(a.isFinishing)
    }
}
