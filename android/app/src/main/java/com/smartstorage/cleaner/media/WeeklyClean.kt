package com.smartstorage.cleaner.media

import com.smartstorage.cleaner.model.formattedBytes
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

// Mirrors ios/SmartStorage/Weekly/WeeklyClean.swift.

/** When the Weekly Smart Clean reminder fires. [weekday] is ISO: 1 = Monday … 7 = Sunday. Pure, for tests. */
data class WeeklyCleanSchedule(val weekday: Int = 7, val hour: Int = 10, val minute: Int = 0) {
    /** The next time this slot occurs strictly after [now] (epoch millis). */
    fun nextFire(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val current = java.time.Instant.ofEpochMilli(now).atZone(zone)
        var candidate: ZonedDateTime = current.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(weekday.coerceIn(1, 7))))
            .withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!candidate.isAfter(current)) candidate = candidate.plusWeeks(1)
        return candidate.toInstant().toEpochMilli()
    }
}

/** The notification text, from what the last scan found. Never mentions files or people. */
object WeeklyCleanMessage {
    const val TITLE = "Weekly Smart Clean"
    /** Below this the reminder doesn't promise savings. */
    const val MEANINGFUL_BYTES = 50_000_000L
    /** A scan older than this may no longer reflect the library. */
    const val STALE_AFTER_MS = 30L * 86_400_000

    fun body(potentialBytes: Long?, lastScan: Long?, now: Long = System.currentTimeMillis()): String {
        if (potentialBytes == null || lastScan == null || now - lastScan > STALE_AFTER_MS) {
            return "Open KeepSpace to look for space you can recover."
        }
        return if (potentialBytes >= MEANINGFUL_BYTES) {
            "About ${potentialBytes.formattedBytes()} can be cleaned up. Review the safest items first."
        } else {
            "Your library looks tidy. Tap to check again."
        }
    }
}
