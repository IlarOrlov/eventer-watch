package watch

import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
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
        val pad = Ui.dp(this, 28)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            background = Ui.gradient(this@AlarmActivity, Ui.ACCENT, Ui.RED, 0)
        }
        root.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_bell)
            setColorFilter(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(Ui.dp(context, 72), Ui.dp(context, 72))
        })
        root.addView(Ui.text(this, if (urls.size > 1) "${urls.size} new on Eventer" else "New on Eventer", 16f, 0xCCFFFFFF.toInt()).apply {
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(context, 12), 0, Ui.dp(context, 8))
        })
        root.addView(Ui.text(this, titles.joinToString("\n"), 26f, bold = true).apply {
            id = ID_TITLES
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, Ui.dp(context, 32))
        })
        root.addView(Ui.pill(this, "Open event page", 0xFFFFFFFF.toInt(), Ui.BG, 20f).apply {
            id = ID_OPEN
            minHeight = Ui.dp(context, 64)
            setOnClickListener { open() }
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(Ui.pill(this, "Dismiss", 0x33FFFFFF, 0xFFFFFFFF.toInt(), 16f).apply {
            id = ID_DISMISS
            setOnClickListener { stopRinging(); finish() }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(this@AlarmActivity, 12) })
        setContentView(root)
    }

    private fun open() {
        stopRinging()
        urls.firstOrNull()?.let { url ->
            val series = SeriesFilter.isSeriesCode(SeriesFilter.codeOf(url)) && ProfileStore.forContext(this).load() != null
            startActivity(
                if (series) CheckoutActivity.intent(this, url)
                else Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        finish()
    }

    private fun stopRinging() {
        getSystemService(NotificationManager::class.java).cancel(Alarm.ID_ALERT)
    }
}
