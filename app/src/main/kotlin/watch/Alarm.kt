package watch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat

class Alarm(private val context: Context) {
    companion object {
        // Channel settings are immutable once created, so the ids carry a version.
        const val CHANNEL_ALARM = "events_v2"
        const val CHANNEL_HEALTH = "health_v2"
        const val CHANNEL_SERVICE = "service"
        const val ID_ALERT = 1001
        const val ID_HEALTH = 1002
        const val ID_SERVICE = 1003
    }

    private val nm get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun ensureChannels() {
        val audio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val alarm = NotificationChannel(CHANNEL_ALARM, "New events", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Rings when a new event or a sale is detected"
            setSound(rawUri(R.raw.event_alert), audio)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val health = NotificationChannel(CHANNEL_HEALTH, "Watcher health", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Tells you when the watcher cannot read Eventer"
            setSound(rawUri(R.raw.health_alert), audio)
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val service = NotificationChannel(CHANNEL_SERVICE, "Watcher status", NotificationManager.IMPORTANCE_LOW)
        nm.deleteNotificationChannel("alarm")
        nm.createNotificationChannel(alarm)
        nm.createNotificationChannel(health)
        nm.createNotificationChannel(service)
    }

    fun raise(alerts: List<Alert>) {
        val first = alerts.firstOrNull() ?: return
        val title = when (first) {
            is Alert.NewEvent -> "New event: ${first.event.title}"
            is Alert.SaleOpened -> "Sale opened: ${first.event.title}"
        }
        val body = alerts.joinToString("\n") {
            (if (it is Alert.SaleOpened) "Sale opened: " else "New: ") + it.event.title
        }
        val screen = Intent(context, AlarmActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(AlarmActivity.EXTRA_URLS, alerts.map { it.event.url }.toTypedArray())
            .putExtra(AlarmActivity.EXTRA_TITLES, alerts.map { it.event.title }.toTypedArray())
        val pi = PendingIntent.getActivity(
            context, ID_ALERT, screen,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_bell)
            .setColor(Ui.ACCENT)
            .setContentTitle(title)
            .setContentText(if (alerts.size > 1) "+${alerts.size - 1} more" else first.event.url)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        n.flags = n.flags or Notification.FLAG_INSISTENT // keep ringing until dismissed
        nm.notify(ID_ALERT, n)
        wakeScreen()
        // With "display over other apps" granted Android lets a background service start the
        // alarm screen even while the phone is unlocked and in use.
        if (Settings.canDrawOverlays(context)) {
            try { context.startActivity(screen) } catch (e: Exception) { /* notification still shown */ }
        }
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "eventerwatch:wake",
        ).acquire(8_000)
    }

    fun raiseHealth(e: HealthEvent) {
        val (title, text) = when (e) {
            HealthEvent.WENT_BLIND -> "Watcher is BLIND" to "Eventer could not be read for 5 checks. You are not covered."
            HealthEvent.RECOVERED -> "Watcher recovered" to "Eventer is readable again."
        }
        val n = NotificationCompat.Builder(context, CHANNEL_HEALTH)
            .setSmallIcon(R.drawable.ic_bell)
            .setColor(Ui.AMBER)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .build()
        nm.notify(ID_HEALTH, n)
    }

    fun serviceNotification(text: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_bell)
            .setColor(Ui.ACCENT)
            .setContentTitle("EventerWatch")
            .setContentText(text)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun rawUri(res: Int): Uri = Uri.parse("android.resource://${context.packageName}/$res")

}
