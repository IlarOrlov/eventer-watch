package watch

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class WatchStatus(
    val running: Boolean = false,
    val lastCheckEpochMs: Long = 0,
    val blind: Boolean = false,
    val recent: List<Alert> = emptyList(),
)

object WatchState {
    private val _status = MutableStateFlow(WatchStatus())
    val status: StateFlow<WatchStatus> = _status.asStateFlow()

    fun update(f: (WatchStatus) -> WatchStatus) = _status.update(f)

    private const val PREFS = "watch"
    private const val KEY_ENABLED = "enabled"

    fun isEnabled(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(c: Context, on: Boolean) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply()
    }
}
