package com.smartstorage.cleaner.media

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Mirrors ios/SmartStorage/MediaEngine/EventGrouper.swift.

/** One photo or video as the event grouper sees it. [date] is epoch millis. */
data class EventCandidate(
    val id: String,
    val date: Long,
    val isVideo: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Top scene label from an on-device classifier (iOS only for now). */
    val sceneLabel: String? = null,
)

/**
 * A trip or event found in the library. Titles come from dates, scene labels and distance — all
 * computed on the device; places are never looked up online.
 */
data class DetectedEvent(
    val id: String,
    val kind: Kind,
    val title: String,
    val start: Long,
    val end: Long,
    /** Capture order. */
    val itemIds: List<String>,
    val photoCount: Int,
    val videoCount: Int,
    /** Rounded distance from home for trips, when locations allow it. */
    val distanceKm: Int? = null,
) {
    enum class Kind { Trip, Event }
}

/**
 * Groups photos into events (a burst of photos on one occasion) and trips (days spent away from
 * home). Pure and deterministic, for tests.
 */
data class EventGrouper(
    /** A pause longer than this starts a new session. */
    val sessionGapMs: Long = 6 * HOUR,
    /** Away sessions this close together belong to the same trip. */
    val tripGapMs: Long = 48 * HOUR,
    /** "Away" means at least this far from home. */
    val awayKm: Double = 80.0,
    val minTripItems: Int = 20,
    /** A themed session (birthday, concert…) needs fewer shots to count than an ordinary busy day. */
    val minThemedEventItems: Int = 12,
    val minEventItems: Int = 30,
    /** Located items needed before a home can be inferred. */
    val minLocatedForHome: Int = 10,
) {
    data class Coordinate(val latitude: Double, val longitude: Double)

    fun events(items: List<EventCandidate>, zone: ZoneId = ZoneId.systemDefault()): List<DetectedEvent> {
        val sorted = items.sortedWith(compareBy<EventCandidate> { it.date }.thenBy { it.id })
        if (sorted.isEmpty()) return emptyList()
        val home = home(sorted, minLocatedForHome)

        // 1. Sessions: runs of shots without a long pause.
        val sessions = mutableListOf<MutableList<EventCandidate>>()
        for (item in sorted) {
            val last = sessions.lastOrNull()?.last()
            if (last != null && item.date - last.date <= sessionGapMs) sessions.last() += item else sessions += mutableListOf(item)
        }

        // 2. Trips: consecutive sessions spent away from home.
        val result = mutableListOf<DetectedEvent>()
        var index = 0
        while (index < sessions.size) {
            if (home == null || !isAway(sessions[index], home)) {
                event(sessions[index], zone)?.let { result += it }
                index++
                continue
            }
            val trip = sessions[index].toMutableList()
            index++
            while (index < sessions.size && sessions[index].first().date - trip.last().date <= tripGapMs && isAway(sessions[index], home)) {
                trip += sessions[index]
                index++
            }
            if (trip.size >= minTripItems) result += trip(trip, home, zone) else event(trip, zone)?.let { result += it }
        }
        return result.sortedByDescending { it.start }
    }

    private fun distances(items: List<EventCandidate>, home: Coordinate): List<Double> = items.mapNotNull { item ->
        val lat = item.latitude ?: return@mapNotNull null
        val lon = item.longitude ?: return@mapNotNull null
        distanceKm(home, Coordinate(lat, lon))
    }

    /** Most located shots in the session are far from home. */
    private fun isAway(session: List<EventCandidate>, home: Coordinate): Boolean {
        val far = distances(session, home)
        if (far.isEmpty()) return false
        return far.count { it >= awayKm } >= far.size / 2.0
    }

    private fun trip(items: List<EventCandidate>, home: Coordinate, zone: ZoneId): DetectedEvent {
        val start = items.first().date
        val end = items.last().date
        val firstDay = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(firstDay, Instant.ofEpochMilli(end).atZone(zone).toLocalDate()).toInt() + 1
        val median = distances(items, home).sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
        val theme = EventTheme.dominant(items)
        val touchesWeekend = (0 until days).any { firstDay.plusDays(it.toLong()).dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }
        val title = when {
            theme != null -> theme.tripTitle
            days == 1 -> "Day Trip"
            days <= 3 && touchesWeekend -> "Weekend Trip"
            else -> "$days-Day Trip"
        }
        return make(DetectedEvent.Kind.Trip, title, items, median?.let { (it / 10).roundToInt() * 10 })
    }

    private fun event(items: List<EventCandidate>, zone: ZoneId): DetectedEvent? {
        val theme = EventTheme.dominant(items)
        if (items.size < (if (theme == null) minEventItems else minThemedEventItems)) return null
        val first = Instant.ofEpochMilli(items.first().date).atZone(zone)
        val title = theme?.eventTitle(first.monthValue) ?: first.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.ENGLISH))
        return make(DetectedEvent.Kind.Event, title, items, null)
    }

    private fun make(kind: DetectedEvent.Kind, title: String, items: List<EventCandidate>, distanceKm: Int?): DetectedEvent {
        val videos = items.count { it.isVideo }
        return DetectedEvent(items.first().id, kind, title, items.first().date, items.last().date, items.map { it.id },
            items.size - videos, videos, distanceKm)
    }

    companion object {
        private const val HOUR = 3_600_000L
        private const val DAY = 86_400_000L

        /**
         * Where life happens: the ~25 km grid cell with shots on the most different days (a
         * photo-heavy trip has more shots than home, but home wins on days), averaged.
         */
        fun home(items: List<EventCandidate>, minimum: Int): Coordinate? {
            val located = items.mapNotNull { item ->
                val lat = item.latitude ?: return@mapNotNull null
                val lon = item.longitude ?: return@mapNotNull null
                Triple(lat, lon, Math.floorDiv(item.date, DAY))
            }
            if (located.size < minimum) return null
            val cells = located.groupBy { "${floor(it.first * 4).toInt()}:${floor(it.second * 4).toInt()}" }
            val busiest = cells.entries.maxWithOrNull(
                compareBy<Map.Entry<String, List<Triple<Double, Double, Long>>>> { e -> e.value.map { it.third }.toSet().size }
                    .thenBy { it.value.size }
                    .thenByDescending { it.key },
            )?.value ?: return null
            return Coordinate(busiest.sumOf { it.first } / busiest.size, busiest.sumOf { it.second } / busiest.size)
        }

        /** Great-circle distance in kilometres. */
        fun distanceKm(a: Coordinate, b: Coordinate): Double {
            val dLat = Math.toRadians(b.latitude - a.latitude)
            val dLon = Math.toRadians(b.longitude - a.longitude)
            val h = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2) * sin(dLon / 2)
            return 2 * 6371.0 * asin(min(1.0, sqrt(h)))
        }
    }
}

/** What an occasion is about, from the scene labels of its photos. */
enum class EventTheme(val labels: Set<String>, private val event: String, val tripTitle: String = "Trip") {
    Wedding(setOf("wedding", "bride", "bridesmaid", "wedding_dress", "wedding_cake"), "Wedding", "Wedding Trip"),
    Birthday(setOf("birthday_cake", "cake", "cupcake", "cake_regular"), "Birthday Party"),
    Concert(setOf("concert"), "Concert Night"),
    Sports(setOf("stadium", "sport", "motorsport"), "Game Day"),
    Christmas(setOf("christmas_tree", "christmas_decoration"), "Christmas"),
    Fireworks(setOf("fireworks"), "Fireworks Night"),
    Graduation(setOf("graduation"), "Graduation"),
    Beach(setOf("beach"), "Beach Day", "Beach Trip"),
    Snow(setOf("snow", "skiing", "snowboarding", "ski_equipment", "winter_sport", "snowman"), "Snow Day", "Snow Trip"),
    Hiking(setOf("hiking", "mountain"), "Hiking Day", "Mountain Trip"),
    Camping(setOf("camping"), "Camping", "Camping Trip"),
    ThemePark(setOf("amusement_park"), "Theme Park"),
    Zoo(setOf("zoo", "aquarium"), "Zoo Day"),
    Museum(setOf("museum"), "Museum Visit");

    /** Fireworks around the turn of the year are New Year's Eve. */
    fun eventTitle(month: Int): String = if (this == Fireworks && month in setOf(12, 1)) "New Year's Eve" else event

    companion object {
        /** The theme covering the most shots, if it is clearly present (≥ 2 shots and ≥ 10 %). */
        fun dominant(items: List<EventCandidate>): EventTheme? {
            val labels = items.mapNotNull { it.sceneLabel }
            val best = entries.map { theme -> theme to labels.count { it in theme.labels } }.maxByOrNull { it.second } ?: return null
            return if (best.second >= 2 && best.second >= items.size * 0.1) best.first else null
        }
    }
}
