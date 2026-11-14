package watch

/** Helpers for the dd-mm-yyyy birth date the checkout forms expect. */
object BirthDate {
    /** Formats what has been typed so far: digits only, dashes inserted after day and month. */
    fun typing(raw: String): String {
        val d = raw.filter { it.isDigit() }.take(8)
        val sb = StringBuilder(d.take(2))
        if (d.length > 2) sb.append('-').append(d.substring(2, minOf(4, d.length)))
        if (d.length > 4) sb.append('-').append(d.substring(4))
        return sb.toString()
    }

    /** Turns `5/4/1986`, `5.4.1986` or `05041986` into `05-04-1986`; anything else is returned unchanged. */
    fun normalize(raw: String): String {
        val s = raw.trim()
        if (s.isEmpty()) return ""
        val parts = s.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }
        if (parts.size == 3 && parts[0].length <= 2 && parts[1].length <= 2 && parts[2].length == 4 && s.any { !it.isDigit() })
            return "%02d-%02d-%s".format(parts[0].toInt(), parts[1].toInt(), parts[2])
        if (s.length == 8 && s.all { it.isDigit() }) return typing(s)
        return raw
    }
}
