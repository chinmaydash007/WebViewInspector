package io.github.chinmaydash007.webviewinspector.internal.util

import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/** Display-only local calendar grouping, compatible with API 23 and daylight-saving transitions. */
internal object HistoryDates {
    enum class RelativeDay { TODAY, YESTERDAY, EARLIER }

    fun key(atMillis: Long, zone: TimeZone = TimeZone.getDefault()): String {
        val day = Calendar.getInstance(zone).apply { timeInMillis = atMillis }
        return "${day.get(Calendar.ERA)}-${day.get(Calendar.YEAR)}-${day.get(Calendar.DAY_OF_YEAR)}"
    }

    fun relative(atMillis: Long, nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): RelativeDay {
        if (key(atMillis, zone) == key(nowMillis, zone)) return RelativeDay.TODAY
        val yesterday = Calendar.getInstance(zone).apply {
            timeInMillis = nowMillis
            add(Calendar.DATE, -1)
        }
        return if (key(atMillis, zone) == key(yesterday.timeInMillis, zone)) RelativeDay.YESTERDAY else RelativeDay.EARLIER
    }

    fun formatted(atMillis: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(atMillis))
}
