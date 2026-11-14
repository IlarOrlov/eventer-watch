package watch

import android.app.NotificationManager
import android.content.Context
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
class MainActivityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val nm get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Test
    fun testAlarmButtonRaisesAlarm() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        activity.findViewById<Button>(MainActivity.ID_TEST_ALARM).performClick()
        val posted = shadowOf(nm).allNotifications.filter { it.channelId == Alarm.CHANNEL_ALARM }
        assertEquals(1, posted.size)
    }

    @Test
    fun delayedTestAlarmFiresOnlyAfterTenSeconds() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        activity.findViewById<Button>(MainActivity.ID_TEST_ALARM_DELAYED).performClick()
        val looper = shadowOf(android.os.Looper.getMainLooper())
        looper.idleFor(java.time.Duration.ofSeconds(9))
        assertTrue(shadowOf(nm).allNotifications.isEmpty())
        looper.idleFor(java.time.Duration.ofSeconds(2))
        assertEquals(1, shadowOf(nm).allNotifications.count { it.channelId == Alarm.CHANNEL_ALARM })
    }

    @Test
    fun checklistShowsMissingPermissions() {
        shadowOf(nm).setNotificationsEnabled(false)
        val items = Onboarding.check(context)
        val notif = items.single { it.id == Onboarding.NOTIFICATIONS }
        assertFalse(notif.done)
        assertNotNull(notif.fix)
        assertFalse(items.single { it.id == Onboarding.BATTERY }.done)
        val overlay = items.single { it.id == Onboarding.OVERLAY }
        assertFalse(overlay.done)
        assertNotNull(overlay.fix)
        assertFalse(items.single { it.id == Onboarding.SAMSUNG }.done)
    }

    @Test
    fun checklistRendersEachItemWithFixButtonWhenNotDone() {
        shadowOf(nm).setNotificationsEnabled(false)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val texts = activity.findViewById<android.view.ViewGroup>(MainActivity.ID_CHECKLIST)
        val labels = (0 until texts.childCount).map { (texts.getChildAt(it).findViewWithTag<TextView>("label")).text.toString() }
        assertEquals(Onboarding.check(context).size, labels.size)
        assertTrue(labels.any { it.contains("Notifications") })
    }

    @Test
    fun startStopButtonReflectsRunningState() {
        WatchState.update { WatchStatus(running = false) }
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val btn = activity.findViewById<Button>(MainActivity.ID_START_STOP)
        assertEquals("Start watching", btn.text.toString())
        WatchState.update { WatchStatus(running = true) }
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals("Stop watching", btn.text.toString())
    }
}
