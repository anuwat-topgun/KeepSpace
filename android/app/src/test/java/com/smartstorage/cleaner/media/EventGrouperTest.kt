package com.smartstorage.cleaner.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

// Mirrors EventGrouperTests in ios/SmartStorageTests/MediaEngineTests.swift.
class EventGrouperTest {
    private val zone = ZoneId.of("Asia/Bangkok")
    private val bangkok = 13.75 to 100.5
    private val chiangMai = 18.79 to 98.98 // ~583 km away

    private fun date(month: Int, day: Int, hour: Int) = LocalDateTime.of(2026, month, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    /** [count] shots a few minutes apart starting at [start]. */
    private fun burst(prefix: String, count: Int, start: Long, place: Pair<Double, Double>?, label: String? = null, everyMinutes: Long = 3) =
        (0 until count).map { i ->
            EventCandidate("$prefix$i", start + i * everyMinutes * 60_000, latitude = place?.first, longitude = place?.second, sceneLabel = label)
        }

    /** Everyday shots at home spread over the month (not on 12–14 Sep: that's the trip). */
    private val dailyLife = ((1..11) + (15..25)).map { EventCandidate("home$it", date(9, it, 18), latitude = bangkok.first, longitude = bangkok.second) }

    @Test fun daysAwayBecomeOneTrip() {
        val trip = burst("a", 15, date(9, 12, 9), chiangMai) + burst("b", 15, date(9, 13, 10), chiangMai) + burst("c", 5, date(9, 14, 11), chiangMai)
        val events = EventGrouper().events(dailyLife + trip, zone)
        assertEquals(1, events.size)
        val event = events.single()
        assertEquals(DetectedEvent.Kind.Trip, event.kind)
        assertEquals(35, event.photoCount)
        assertEquals("Weekend Trip", event.title) // 12–14 Sep 2026 is Sat–Mon
        assertEquals(580, event.distanceKm)
    }

    @Test fun themedBurstAtHomeIsAnEvent() {
        val party = burst("p", 14, date(9, 27, 19), bangkok, "birthday_cake") + burst("q", 6, date(9, 27, 20), bangkok)
        val events = EventGrouper().events(dailyLife + party, zone)
        assertEquals(listOf("Birthday Party"), events.map { it.title })
        assertEquals(DetectedEvent.Kind.Event, events.single().kind)
    }

    @Test fun ordinaryDaysAreNotEvents() {
        assertTrue(EventGrouper().events(dailyLife + burst("s", 12, date(9, 21, 12), bangkok), zone).isEmpty())
        val busy = EventGrouper().events(burst("b", 40, date(9, 26, 10), bangkok, everyMinutes = 5), zone)
        assertEquals(listOf("Saturday, 26 Sep"), busy.map { it.title })
    }

    @Test fun noLocationsMeansNoTrips() {
        assertEquals(listOf(DetectedEvent.Kind.Event), EventGrouper().events(burst("x", 30, date(9, 12, 9), null), zone).map { it.kind })
    }

    @Test fun memoriesProtectBlurryPhotos() {
        val items = (0 until 30).map { i ->
            MediaItem("m$i", MediaItem.Kind.Photo, date(9, 27, 19) + i * 120_000L, 1_000_000, 4032, 3024, 0, false)
        }
        // Distinct hashes so nothing groups as similar; one blurry shot.
        val analyzed = items.mapIndexed { i, item ->
            AnalyzedPhoto(item, ImageFeatures(if (i % 2 == 0) i.toLong() * 0x0F0F0F0F0F0F0F0FL else -i.toLong() * 0x3333333333333333L,
                if (i == 3) 10.0 else 500.0, 0.5, null, 0))
        }
        val content = LibraryReportBuilder(zone = zone).build(items, analyzed, 100, 50)
        assertEquals(1, content.memories.size)
        assertEquals(1, content.memories.single().blurryCount)
        assertEquals(1, content.tripBlurryShots)
        assertEquals(listOf(false), content.reviewSets[ReviewKind.Blurry]?.map { it.preselected })
    }
}
