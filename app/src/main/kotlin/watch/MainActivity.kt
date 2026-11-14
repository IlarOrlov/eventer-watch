package watch

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    companion object {
        const val ID_START_STOP = 9001
        const val ID_TEST_ALARM = 9002
        const val ID_CHECKLIST = 9003
        const val ID_STATUS = 9004
        const val ID_RECENT = 9005
    }

    private val scope = CoroutineScope(Dispatchers.Main)
    private var collector: Job? = null
    private lateinit var startStop: Button
    private lateinit var status: TextView
    private lateinit var checklist: LinearLayout
    private lateinit var recent: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        status = text("", 18f).apply { id = ID_STATUS }
        startStop = Button(this).apply {
            id = ID_START_STOP
            setOnClickListener { toggle() }
        }
        val test = Button(this).apply {
            id = ID_TEST_ALARM
            text = "Test alarm"
            setOnClickListener { Alarm(this@MainActivity).apply { ensureChannels() }.raise(listOf(testAlert())) }
        }
        checklist = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; id = ID_CHECKLIST }
        recent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; id = ID_RECENT }
        root.addView(status)
        root.addView(startStop)
        root.addView(test)
        root.addView(text("Setup", 20f))
        root.addView(checklist)
        root.addView(text("Recent detections", 20f))
        root.addView(recent)
        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onStart() {
        super.onStart()
        collector = scope.launch { WatchState.status.collect { render(it) } }
    }

    override fun onResume() {
        super.onResume()
        renderChecklist()
    }

    override fun onStop() {
        collector?.cancel()
        super.onStop()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun toggle() {
        if (WatchState.status.value.running) {
            WatchService.stop(this)
        } else {
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
            WatchService.start(this)
        }
    }

    private fun render(s: WatchStatus) {
        startStop.text = if (s.running) "Stop watching" else "Start watching"
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
        val last = if (s.lastCheckEpochMs == 0L) "never" else fmt.format(Date(s.lastCheckEpochMs))
        val health = when {
            !s.running -> "Stopped"
            s.blind -> "BLIND – cannot read Eventer"
            else -> "Healthy"
        }
        status.text = "$health\nLast check: $last"
        recent.removeAllViews()
        s.recent.forEach { a ->
            recent.addView(text((if (a is Alert.SaleOpened) "Sale opened: " else "New: ") + a.event.title, 16f).apply {
                setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.event.url))) }
            })
        }
    }

    private fun renderChecklist() {
        checklist.removeAllViews()
        Onboarding.check(this).forEach { r ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            row.addView(text((if (r.done) "✓ " else "✗ ") + r.label, 16f).apply { tag = "label" })
            if (!r.done) {
                row.addView(text(r.instructions, 14f))
                r.fix?.let { fix ->
                    row.addView(Button(this).apply {
                        text = "Fix"
                        tag = "fix"
                        setOnClickListener {
                            if (r.id == Onboarding.SAMSUNG) Onboarding.acknowledgeSamsung(this@MainActivity)
                            try { startActivity(fix) } catch (e: Exception) { /* settings page unavailable */ }
                            renderChecklist()
                        }
                    })
                }
            }
            checklist.addView(row)
        }
    }

    private fun text(s: String, size: Float) = TextView(this).apply {
        text = s
        textSize = size
        textDirection = View.TEXT_DIRECTION_ANY_RTL
    }

    private fun testAlert() = Alert.NewEvent(Event("test", "Test alarm", "https://www.eventer.co.il", false))
}
