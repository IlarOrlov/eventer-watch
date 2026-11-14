package watch

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlarmTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val nm get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun ev(id: String, url: String = "https://www.eventer.co.il/$id") =
        Event(id, "title $id", url, true)

    @Test
    fun raiseWithThreeAlertsPostsOneNotification() {
        val alarm = Alarm(context).also { it.ensureChannels() }
        alarm.raise(listOf(Alert.NewEvent(ev("a")), Alert.NewEvent(ev("b")), Alert.SaleOpened(ev("c"))))
        val posted = shadowOf(nm).allNotifications.filter { it.channelId == Alarm.CHANNEL_ALARM }
        assertEquals(1, posted.size)
        assertTrue(posted.single().extras.getCharSequence("android.bigText").toString().contains("title b"))
    }

    @Test
    fun raiseOpensFirstEventUrl() {
        val alarm = Alarm(context).also { it.ensureChannels() }
        alarm.raise(listOf(Alert.NewEvent(ev("שלום", "https://www.eventer.co.il/אירוע")), Alert.NewEvent(ev("b"))))
        val n = shadowOf(nm).allNotifications.single { it.channelId == Alarm.CHANNEL_ALARM }
        val intent: Intent = shadowOf(n.contentIntent).savedIntent
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://www.eventer.co.il/אירוע", intent.data.toString().let { android.net.Uri.decode(it) })
        assertNotNull(n.fullScreenIntent)
    }

    @Test
    fun raiseWithNoAlertsDoesNothing() {
        val alarm = Alarm(context).also { it.ensureChannels() }
        alarm.raise(emptyList())
        assertTrue(shadowOf(nm).allNotifications.isEmpty())
    }

    @Test
    fun healthEventsPostOnAlarmChannel() {
        val alarm = Alarm(context).also { it.ensureChannels() }
        alarm.raiseHealth(HealthEvent.WENT_BLIND)
        val n = shadowOf(nm).allNotifications.single()
        assertEquals(Alarm.CHANNEL_ALARM, n.channelId)
    }
}
