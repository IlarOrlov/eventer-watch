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
    fun tapAndFullScreenOpenTheAlarmScreenCarryingTheFirstUrl() {
        val alarm = Alarm(context).also { it.ensureChannels() }
        alarm.raise(listOf(Alert.NewEvent(ev("שלום", "https://www.eventer.co.il/אירוע")), Alert.NewEvent(ev("b"))))
        val n = shadowOf(nm).allNotifications.single { it.channelId == Alarm.CHANNEL_ALARM }
        for (pi in listOf(n.contentIntent, n.fullScreenIntent)) {
            val intent: Intent = shadowOf(pi).savedIntent
            assertEquals(AlarmActivity::class.java.name, intent.component!!.className)
            assertEquals("https://www.eventer.co.il/אירוע", intent.getStringArrayExtra(AlarmActivity.EXTRA_URLS)!![0])
            assertEquals(2, intent.getStringArrayExtra(AlarmActivity.EXTRA_TITLES)!!.size)
        }
    }

    @Test
    fun raiseShowsAlarmScreenDirectlyWhenOverlayPermitted() {
        org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(true)
        Alarm(context).also { it.ensureChannels() }.raise(listOf(Alert.NewEvent(ev("a"))))
        val started = shadowOf(context).nextStartedActivity
        assertEquals(AlarmActivity::class.java.name, started.component!!.className)
    }

    @Test
    fun raiseDoesNotForceActivityWithoutOverlayPermission() {
        org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(false)
        Alarm(context).also { it.ensureChannels() }.raise(listOf(Alert.NewEvent(ev("a"))))
        assertNull(shadowOf(context).nextStartedActivity)
    }

    @Test
    fun raiseWithNoAlertsDoesNothing() {
        val alarm = Alarm(context).also { it.ensureChannels() }
        alarm.raise(emptyList())
        assertTrue(shadowOf(nm).allNotifications.isEmpty())
    }

    @Test
    fun healthEventsPostOnTheirOwnChannel() {
        val alarm = Alarm(context).also { it.ensureChannels() }
        alarm.raiseHealth(HealthEvent.WENT_BLIND)
        val n = shadowOf(nm).allNotifications.single()
        assertEquals(Alarm.CHANNEL_HEALTH, n.channelId)
    }

    @Test
    fun eventChannelUsesBundledSoundNotSystemAlarm() {
        Alarm(context).ensureChannels()
        val ch = nm.getNotificationChannel(Alarm.CHANNEL_ALARM)
        val sound = ch.sound.toString()
        assertTrue(sound, sound.startsWith("android.resource://${context.packageName}/"))
        assertNotEquals(sound, nm.getNotificationChannel(Alarm.CHANNEL_HEALTH).sound.toString())
        assertEquals(android.media.AudioAttributes.USAGE_ALARM, ch.audioAttributes.usage)
    }

    @Test
    fun eventAlertKeepsRingingUntilDismissed() {
        val alarm = Alarm(context).also { it.ensureChannels() }
        alarm.raise(listOf(Alert.NewEvent(ev("a"))))
        val n = shadowOf(nm).allNotifications.single { it.channelId == Alarm.CHANNEL_ALARM }
        assertTrue(n.flags and android.app.Notification.FLAG_INSISTENT != 0)
    }
}
