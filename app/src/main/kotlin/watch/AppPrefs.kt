package watch

import android.content.Context

object AppPrefs {
    private const val PREFS = "watch"
    private const val SERIES_ONLY = "series_only"
    private const val AUTO_CHECKOUT = "auto_checkout"

    private fun p(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Only alert for the "מסיבתה" line. */
    fun seriesOnly(c: Context) = p(c).getBoolean(SERIES_ONLY, true)
    fun setSeriesOnly(c: Context, v: Boolean) = p(c).edit().putBoolean(SERIES_ONLY, v).apply()

    /** Open the auto-filling checkout straight away when a series event appears. */
    fun autoCheckout(c: Context) = p(c).getBoolean(AUTO_CHECKOUT, true)
    fun setAutoCheckout(c: Context, v: Boolean) = p(c).edit().putBoolean(AUTO_CHECKOUT, v).apply()
}
