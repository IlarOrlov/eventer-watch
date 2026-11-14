package watch

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

data class Requirement(
    val id: String,
    val label: String,
    val instructions: String,
    val done: Boolean,
    val fix: Intent?,
)

object Onboarding {
    const val NOTIFICATIONS = "notifications"
    const val FULL_SCREEN = "fullscreen"
    const val OVERLAY = "overlay"
    const val BATTERY = "battery"
    const val SAMSUNG = "samsung"

    private const val PREFS = "watch"
    private const val KEY_SAMSUNG_ACK = "samsung_ack"

    fun acknowledgeSamsung(c: Context) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SAMSUNG_ACK, true).apply()
    }

    fun check(c: Context): List<Requirement> {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val pm = c.getSystemService(Context.POWER_SERVICE) as PowerManager
        val pkg = Uri.parse("package:${c.packageName}")

        val fullScreenOk = Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent()
        val fullScreenFix = if (Build.VERSION.SDK_INT >= 34)
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg) else null

        return listOf(
            Requirement(
                NOTIFICATIONS, "Notifications",
                "Allow notifications so alarms can be shown.",
                nm.areNotificationsEnabled(),
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName),
            ),
            Requirement(
                FULL_SCREEN, "Full-screen alarms",
                "Allow full-screen notifications so the screen wakes up for an alarm.",
                fullScreenOk, fullScreenFix,
            ),
            Requirement(
                OVERLAY, "Display over other apps",
                "Lets the alarm screen open by itself while you are using the phone.",
                Settings.canDrawOverlays(c),
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg),
            ),
            Requirement(
                BATTERY, "Battery optimization",
                "Exempt EventerWatch from battery optimization so it keeps running.",
                pm.isIgnoringBatteryOptimizations(c.packageName),
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg),
            ),
            Requirement(
                SAMSUNG, "Samsung battery settings",
                "Settings > Battery > Background usage limits: remove EventerWatch from \"Sleeping apps\" and " +
                    "\"Deep sleeping apps\", turn off \"Put unused apps to sleep\". In the app's Battery page " +
                    "choose \"Unrestricted\". Tap the button when done.",
                c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SAMSUNG_ACK, false),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg),
            ),
        )
    }
}
