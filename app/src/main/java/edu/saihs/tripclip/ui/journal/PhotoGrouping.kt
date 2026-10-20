package edu.saihs.tripclip.ui.journal

import edu.saihs.tripclip.data.Photo
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class GroupMode { Place, Time }

/** One horizontal row of polaroids on the journal list. */
sealed interface PhotoGroup {
    val photos: List<Photo>
    val key: String

    /** A run of consecutive photos taken at the same place. */
    data class ByPlace(
        val placeName: String?,
        val start: LocalTime,
        val end: LocalTime,
        override val photos: List<Photo>,
    ) : PhotoGroup {
        override val key: String get() = "place-${photos.first().id}"
    }

    /** All photos taken within the same clock hour. */
    data class ByHour(
        val hour: LocalTime,
        /** Distinct place names in capture order; null entries are dropped. */
        val placeNames: List<String>,
        override val photos: List<Photo>,
    ) : PhotoGroup {
        override val key: String get() = "hour-$hour"
    }
}

/** Groups photos (any order) by [mode]; groups and their photos are ordered by capture time. */
fun groupPhotos(photos: List<Photo>, mode: GroupMode, zone: ZoneId = ZoneId.systemDefault()): List<PhotoGroup> {
    val sorted = photos.sortedBy { it.takenAt }
    fun Photo.localTime(): LocalTime = Instant.ofEpochMilli(takenAt).atZone(zone).toLocalTime()

    return when (mode) {
        GroupMode.Place -> buildList {
            var run = mutableListOf<Photo>()
            fun flush() {
                if (run.isEmpty()) return
                add(PhotoGroup.ByPlace(run.first().placeName, run.first().localTime(), run.last().localTime(), run))
                run = mutableListOf()
            }
            for (photo in sorted) {
                if (run.isNotEmpty() && run.last().placeName != photo.placeName) flush()
                run += photo
            }
            flush()
        }

        GroupMode.Time -> sorted
            .groupBy { it.localTime().truncatedTo(ChronoUnit.HOURS) }
            .map { (hour, inHour) ->
                PhotoGroup.ByHour(hour, inHour.mapNotNull { it.placeName }.distinct(), inHour)
            }
    }
}
