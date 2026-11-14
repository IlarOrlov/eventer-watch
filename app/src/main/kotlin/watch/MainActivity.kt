package watch

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : Activity() {
    companion object {
        const val ID_START_STOP = 9001
        const val ID_TEST_ALARM = 9002
        const val ID_TEST_ALARM_DELAYED = 9006
        const val ID_CHECKLIST = 9003
        const val ID_STATUS = 9004
        const val ID_RECENT = 9005
    }

    private val scope = CoroutineScope(Dispatchers.Main)
    private var collector: Job? = null

    private lateinit var hero: LinearLayout
    private lateinit var heroIcon: ImageView
    private lateinit var heroTitle: TextView
    private lateinit var heroSub: TextView
    private lateinit var startStop: Button
    private lateinit var recent: LinearLayout
    private lateinit var setupSummary: TextView
    private lateinit var checklist: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = column().apply { setPadding(dp(20), dp(24), dp(20), dp(32)) }

        page.addView(Ui.text(this, "EventerWatch", 28f, bold = true))
        page.addView(Ui.text(this, "The moment something drops, you know.", 14f, Ui.MUTED).apply {
            setPadding(0, dp(2), 0, dp(20))
        })

        // Hero
        hero = column().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(28), dp(20), dp(24))
        }
        heroIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_bell)
            setColorFilter(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
        }
        heroTitle = Ui.text(this, "", 24f, bold = true).apply { gravity = Gravity.CENTER; id = ID_STATUS }
        heroSub = Ui.text(this, "", 14f, 0xCCFFFFFF.toInt()).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(20))
        }
        startStop = Ui.pill(this, "Start watching", 0xFFFFFFFF.toInt(), Ui.BG, 17f).apply {
            id = ID_START_STOP
            setOnClickListener { toggle() }
        }
        hero.addView(heroIcon)
        hero.addView(Ui.text(this, "", 4f)) // spacer
        hero.addView(heroTitle)
        hero.addView(heroSub)
        hero.addView(startStop, LinearLayout.LayoutParams(-1, -2))
        page.addView(hero, LinearLayout.LayoutParams(-1, -2))

        // Recent
        page.addView(sectionTitle("Recent detections"))
        recent = column().apply { id = ID_RECENT }
        page.addView(recent)

        // Setup
        page.addView(sectionTitle("Setup"))
        setupSummary = Ui.text(this, "", 13f, Ui.MUTED).apply { setPadding(0, 0, 0, dp(8)) }
        page.addView(setupSummary)
        checklist = column().apply { id = ID_CHECKLIST }
        page.addView(checklist)

        // Tools
        page.addView(sectionTitle("Tools"))
        val tools = column().apply {
            background = Ui.round(this@MainActivity, Ui.CARD)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        tools.addView(Ui.text(this, "Hear and see exactly what an alert looks like.", 13f, Ui.MUTED))
        tools.addView(Ui.pill(this, "Preview alarm", Ui.CARD_HI, Ui.TEXT, 15f).apply {
            id = ID_TEST_ALARM
            setOnClickListener { Alarm(this@MainActivity).apply { ensureChannels() }.raise(listOf(sample())) }
        }, spaced())
        tools.addView(Ui.pill(this, "Preview in 10 s (then lock the screen)", Ui.CARD_HI, Ui.TEXT, 15f).apply {
            id = ID_TEST_ALARM_DELAYED
            setOnClickListener { scheduleSample() }
        }, spaced())
        page.addView(tools)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Ui.BG)
            isFillViewport = true
            addView(page)
        })
        render(WatchState.status.value)
    }

    override fun onStart() {
        super.onStart()
        collector = scope.launch {
            launch { WatchState.status.collect { render(it) } }
            launch { while (isActive) { delay(1000); render(WatchState.status.value) } } // keeps "x s ago" fresh
        }
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

    private fun scheduleSample() {
        val app = applicationContext
        val lock = (getSystemService(POWER_SERVICE) as android.os.PowerManager)
            .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "eventerwatch:preview")
        lock.acquire(20_000)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            Alarm(app).apply { ensureChannels() }.raise(listOf(sample()))
            if (lock.isHeld) lock.release()
        }, 10_000)
        Toast.makeText(this, "Alarm in 10 seconds – lock the screen now", Toast.LENGTH_LONG).show()
    }

    // ---- rendering ----

    private fun render(s: WatchStatus) {
        val now = System.currentTimeMillis()
        val (title, sub, from, to) = when {
            !s.running -> Quad("Not watching", "Tap start to be alerted about new events.", 0xFF3A3F63.toInt(), 0xFF262A47.toInt())
            s.blind -> Quad("Can't reach Eventer", "You are NOT covered. Last good check ${TimeAgo.format(now, s.lastCheckEpochMs)}.", 0xFFD64560.toInt(), 0xFF8E2A55.toInt())
            else -> Quad("Watching Eventer", "Last check ${TimeAgo.format(now, s.lastCheckEpochMs)}", 0xFF6A4CFF.toInt(), 0xFF2DB6A3.toInt())
        }
        hero.background = Ui.gradient(this, from, to)
        heroTitle.text = title
        heroSub.text = sub
        startStop.text = if (s.running) "Stop watching" else "Start watching"

        recent.removeAllViews()
        if (s.recent.isEmpty()) {
            recent.addView(card().apply {
                addView(Ui.text(context, "Nothing yet. You'll be alerted the moment something appears.", 14f, Ui.MUTED))
            })
        }
        s.recent.forEach { a ->
            val sale = a is Alert.SaleOpened
            recent.addView(card().apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(Ui.text(context, if (sale) "SALE" else "NEW", 11f, Ui.BG, bold = true).apply {
                    background = Ui.round(context, if (sale) Ui.GREEN else Ui.ACCENT, 8)
                    setPadding(dp(8), dp(3), dp(8), dp(3))
                })
                addView(Ui.text(context, a.event.title, 16f).apply { setPadding(dp(12), 0, 0, 0) },
                    LinearLayout.LayoutParams(0, -2, 1f))
                setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.event.url))) }
            })
        }
    }

    private fun renderChecklist() {
        val items = Onboarding.check(this)
        setupSummary.text = if (items.all { it.done }) "Everything is set. You're ready." else "${items.count { it.done }} of ${items.size} ready"
        checklist.removeAllViews()
        items.forEach { r ->
            val row = card().apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(Ui.text(this, if (r.done) "✓" else "!", 14f, Ui.BG, bold = true).apply {
                gravity = Gravity.CENTER
                background = Ui.round(this@MainActivity, if (r.done) Ui.GREEN else Ui.AMBER, 14)
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            })
            val body = column().apply { setPadding(dp(12), 0, dp(8), 0) }
            body.addView(Ui.text(this, r.label, 16f, bold = true).apply { tag = "label" })
            if (!r.done) body.addView(Ui.text(this, r.instructions, 13f, Ui.MUTED))
            row.addView(body, LinearLayout.LayoutParams(0, -2, 1f))
            if (!r.done) r.fix?.let { fix ->
                row.addView(Ui.pill(this, "Fix", Ui.ACCENT, 0xFFFFFFFF.toInt(), 14f).apply {
                    tag = "fix"
                    minHeight = dp(40)
                    setOnClickListener {
                        if (r.id == Onboarding.SAMSUNG) Onboarding.acknowledgeSamsung(this@MainActivity)
                        try { startActivity(fix) } catch (e: Exception) { /* settings page unavailable */ }
                        renderChecklist()
                    }
                })
            }
            checklist.addView(row)
        }
    }

    // ---- small helpers ----

    private data class Quad(val a: String, val b: String, val c: Int, val d: Int)

    private fun dp(v: Number) = Ui.dp(this, v)

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = Ui.round(context, Ui.CARD, 16)
        setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = spaced()
    }

    private fun spaced() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }

    private fun sectionTitle(s: String): View =
        Ui.text(this, s, 18f, bold = true).apply { setPadding(0, dp(24), 0, dp(8)) }

    private fun sample() = Alert.NewEvent(Event("preview", "Sample event – נועם צוריאלי", "https://www.eventer.co.il", false))
}
