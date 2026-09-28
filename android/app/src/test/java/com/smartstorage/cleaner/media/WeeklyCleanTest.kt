package com.smartstorage.cleaner.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

// Mirrors WeeklyCleanTests in ios/SmartStorageTests/WeeklyCleanTests.swift.
class WeeklyCleanTest {
    private val zone = ZoneId.of("Asia/Bangkok")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    @Test fun nextFireIsTheNextMatchingSlot() {
        // 28 Sep 2026 is a Monday.
        val sunday = WeeklyCleanSchedule(weekday = 7, hour = 10, minute = 30)
        assertEquals(at(2026, 10, 4, 10, 30), sunday.nextFire(at(2026, 9, 28, 9), zone))
        // Exactly on the slot → the following week.
        assertEquals(at(2026, 10, 11, 10, 30), sunday.nextFire(at(2026, 10, 4, 10, 30), zone))
        // Same day, earlier than the slot → today.
        assertEquals(at(2026, 10, 4, 10, 30), sunday.nextFire(at(2026, 10, 4, 8), zone))
        // Monday slot on a Monday morning → today; in the afternoon → next Monday.
        val monday = WeeklyCleanSchedule(weekday = 1, hour = 18)
        assertEquals(at(2026, 9, 28, 18), monday.nextFire(at(2026, 9, 28, 9), zone))
        assertEquals(at(2026, 10, 5, 18), monday.nextFire(at(2026, 9, 28, 19), zone))
    }

    @Test fun messageDependsOnWhatTheLastScanFound() {
        Locale.setDefault(Locale.US)
        val now = at(2026, 9, 28, 12)
        val day = 86_400_000L
        assertTrue(WeeklyCleanMessage.body(2_300_000_000, now - day, now).contains("2.3 GB"))
        assertTrue(WeeklyCleanMessage.body(1_000_000, now - day, now).contains("tidy"))
        // Never scanned, or a scan too old to trust: no numbers.
        assertFalse(WeeklyCleanMessage.body(null, null, now).contains("GB"))
        assertFalse(WeeklyCleanMessage.body(5_000_000_000, now - 40 * day, now).contains("GB"))
    }
}
