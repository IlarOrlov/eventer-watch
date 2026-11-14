package watch

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class WatchService : Service() {
    companion object {
        const val ACTION_STOP = "watch.STOP"
        const val FEED_URL = "https://www.eventer.co.il/sliders/list.js"

        fun start(c: Context) {
            c.startForegroundService(Intent(c, WatchService::class.java))
        }

        fun stop(c: Context) {
            WatchState.setEnabled(c, false)
            c.startService(Intent(c, WatchService::class.java).setAction(ACTION_STOP))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private lateinit var alarm: Alarm

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        alarm = Alarm(this).also { it.ensureChannels() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            WatchState.update { it.copy(running = false) }
            job?.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this, Alarm.ID_SERVICE, alarm.serviceNotification("Starting…"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        WatchState.setEnabled(this, true)
        WatchState.update { it.copy(running = true) }
        if (job?.isActive != true) job = scope.launch { runLoop() }
        return START_STICKY
    }

    private suspend fun runLoop() {
        val client = OkHttpClient.Builder()
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "eventerwatch:poll")
        val fetcher = WakeLockFetcher(OkHttpFeedFetcher(client, FEED_URL), lock)
        val poller = Poller(fetcher, FileSnapshotStore(File(filesDir, "snapshot.json")), Health())
        val clock = SimpleDateFormat("HH:mm:ss", Locale.US)
        WatchLoop(poller, Backoff()) { r ->
            if (r.alerts.isNotEmpty()) alarm.raise(r.alerts)
            r.health?.let { alarm.raiseHealth(it) }
            val now = System.currentTimeMillis()
            WatchState.update { s ->
                s.copy(
                    lastCheckEpochMs = if (r.outcome == Outcome.OK) now else s.lastCheckEpochMs,
                    blind = when (r.health) {
                        HealthEvent.WENT_BLIND -> true
                        HealthEvent.RECOVERED -> false
                        null -> s.blind
                    },
                    recent = (r.alerts + s.recent).take(30),
                )
            }
            val last = WatchState.status.value.lastCheckEpochMs
            val text = (if (WatchState.status.value.blind) "BLIND – " else "") +
                if (last == 0L) "No successful check yet" else "Last check ${clock.format(Date(last))}"
            getSystemService(android.app.NotificationManager::class.java)
                .notify(Alarm.ID_SERVICE, alarm.serviceNotification(text))
        }.run()
    }

    override fun onDestroy() {
        WatchState.update { it.copy(running = false) }
        scope.cancel()
        super.onDestroy()
    }
}

private class WakeLockFetcher(private val inner: FeedFetcher, private val lock: PowerManager.WakeLock) : FeedFetcher {
    override suspend fun fetch(): String {
        lock.acquire(30_000)
        try {
            return inner.fetch()
        } finally {
            if (lock.isHeld) lock.release()
        }
    }
}
