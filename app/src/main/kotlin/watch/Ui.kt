package watch

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.TextView

/** Small design system: colours, rounded surfaces, text and pill buttons. */
object Ui {
    val BG = Color.parseColor("#0E1020")
    val CARD = Color.parseColor("#1A1E36")
    val CARD_HI = Color.parseColor("#252A4A")
    val TEXT = Color.parseColor("#F4F5FA")
    val MUTED = Color.parseColor("#9AA0BC")
    val ACCENT = Color.parseColor("#7C5CFF")
    val GREEN = Color.parseColor("#2DD4A0")
    val RED = Color.parseColor("#FF5A6E")
    val AMBER = Color.parseColor("#FFB84D")

    fun dp(c: Context, v: Number): Int = (v.toFloat() * c.resources.displayMetrics.density).toInt()

    fun round(c: Context, color: Int, radiusDp: Number = 20): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(c, radiusDp).toFloat()
        }

    fun gradient(c: Context, from: Int, to: Int, radiusDp: Number = 24): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(from, to)).apply {
            cornerRadius = dp(c, radiusDp).toFloat()
        }

    fun text(c: Context, s: String, sp: Float, color: Int = TEXT, bold: Boolean = false): TextView =
        TextView(c).apply {
            text = s
            textSize = sp
            setTextColor(color)
            textDirection = View.TEXT_DIRECTION_ANY_RTL
            textAlignment = View.TEXT_ALIGNMENT_TEXT_START
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }

    fun pill(c: Context, s: String, bg: Int, fg: Int, sp: Float = 16f): Button =
        Button(c).apply {
            text = s
            textSize = sp
            isAllCaps = false
            setTextColor(fg)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            background = round(c, bg, 28)
            stateListAnimator = null
            minHeight = dp(c, 52)
            setPadding(dp(c, 20), 0, dp(c, 20), 0)
        }
}
