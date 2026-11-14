package watch

import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Shown over the lock screen and wakes the display; the notification keeps ringing until dismissed. */
class AlarmActivity : Activity() {
    companion object {
        const val EXTRA_URLS = "urls"
        const val EXTRA_TITLES = "titles"
        const val ID_TITLES = 9101
        const val ID_OPEN = 9102
        const val ID_DISMISS = 9103
    }

    private var urls: Array<String> = emptyArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        show(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        show(intent)
    }

    private fun show(i: Intent) {
        urls = i.getStringArrayExtra(EXTRA_URLS) ?: emptyArray()
        val titles = i.getStringArrayExtra(EXTRA_TITLES) ?: emptyArray()
        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#B71C1C"))
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "New on Eventer!"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            id = ID_TITLES
            text = titles.joinToString("\n")
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            textDirection = View.TEXT_DIRECTION_ANY_RTL
            setPadding(0, pad, 0, pad)
        })
        root.addView(Button(this).apply {
            id = ID_OPEN
            text = "Open event page"
            textSize = 22f
            setOnClickListener { open() }
        })
        root.addView(Button(this).apply {
            id = ID_DISMISS
            text = "Dismiss"
            setOnClickListener { stopRinging(); finish() }
        })
        setContentView(root)
    }

    private fun open() {
        stopRinging()
        urls.firstOrNull()?.let { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        finish()
    }

    private fun stopRinging() {
        getSystemService(NotificationManager::class.java).cancel(Alarm.ID_ALERT)
    }
}
