package com.pantry.app.importer

/**
 * schema.org durations are ISO-8601 ("PT1H30M"), but plenty of sites emit
 * "1 hr 30 mins" or "90" instead, so both shapes are accepted.
 */
object Iso8601Duration {

    private val iso = Regex(
        "^P(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+(?:\\.\\d+)?)S)?)?$",
        RegexOption.IGNORE_CASE
    )
    private val hoursText = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:h|hr|hrs|hour|hours)", RegexOption.IGNORE_CASE)
    private val minsText = Regex("(\\d+)\\s*(?:m|min|mins|minute|minutes)", RegexOption.IGNORE_CASE)

    fun toMinutes(value: String?): Int? {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) return null

        iso.find(raw)?.let { m ->
            val days = m.groupValues[1].toIntOrNull() ?: 0
            val hours = m.groupValues[2].toIntOrNull() ?: 0
            val mins = m.groupValues[3].toIntOrNull() ?: 0
            val total = days * 1440 + hours * 60 + mins
            if (total > 0) return total
        }

        val h = hoursText.find(raw)?.groupValues?.get(1)?.toDoubleOrNull()
        val mm = minsText.find(raw)?.groupValues?.get(1)?.toIntOrNull()
        if (h != null || mm != null) {
            val total = ((h ?: 0.0) * 60).toInt() + (mm ?: 0)
            if (total > 0) return total
        }

        return raw.toIntOrNull()?.takeIf { it in 1..6000 }
    }

    fun format(minutes: Int?): String = when {
        minutes == null || minutes <= 0 -> "--"
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60} hr"
        else -> "${minutes / 60} hr ${minutes % 60} min"
    }
}
