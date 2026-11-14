package watch

object TimeAgo {
    fun format(nowMs: Long, thenMs: Long): String {
        if (thenMs <= 0) return "never"
        val s = (nowMs - thenMs) / 1000
        return when {
            s < 1 -> "just now"
            s < 60 -> "$s s ago"
            s < 3600 -> "${s / 60} min ago"
            s < 86_400 -> "${s / 3600} h ago"
            else -> "${s / 86_400} d ago"
        }
    }
}
