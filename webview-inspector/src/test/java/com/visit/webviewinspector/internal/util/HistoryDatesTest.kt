package com.visit.webviewinspector.internal.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class HistoryDatesTest {
    private val zone = TimeZone.getTimeZone("America/New_York")

    @Test fun localMidnightSeparatesTodayFromYesterday() {
        val now = time(2026, 10, 25, 0, 5)
        assertEquals(HistoryDates.RelativeDay.TODAY, HistoryDates.relative(time(2026, 10, 25, 0, 1), now, zone))
        assertEquals(HistoryDates.RelativeDay.YESTERDAY, HistoryDates.relative(time(2026, 10, 24, 23, 59), now, zone))
        assertEquals(HistoryDates.RelativeDay.EARLIER, HistoryDates.relative(time(2026, 10, 23, 23, 59), now, zone))
    }

    @Test fun daylightSavingYesterdayUsesCalendarDayInsteadOfTwentyFourHours() {
        val now = time(2026, 3, 9, 0, 15)
        assertEquals(HistoryDates.RelativeDay.YESTERDAY, HistoryDates.relative(time(2026, 3, 8, 0, 5), now, zone))
    }

    @Test fun yearBoundaryKeysAreDistinct() {
        assertNotEquals(HistoryDates.key(time(2025, 1, 1, 12, 0), zone), HistoryDates.key(time(2026, 1, 1, 12, 0), zone))
        assertEquals(HistoryDates.RelativeDay.YESTERDAY, HistoryDates.relative(time(2025, 12, 31, 23, 0), time(2026, 1, 1, 1, 0), zone))
    }

    @Test fun sameLocalDayKeepsOneGroupingKey() {
        assertEquals(HistoryDates.key(time(2026, 10, 25, 0, 1), zone), HistoryDates.key(time(2026, 10, 25, 23, 59), zone))
    }

    private fun time(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long = Calendar.getInstance(zone).apply {
        clear()
        set(year, month - 1, day, hour, minute)
    }.timeInMillis
}
