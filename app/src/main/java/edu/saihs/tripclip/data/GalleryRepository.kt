package edu.saihs.tripclip.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** An image in the system gallery (MediaStore). */
data class GalleryImage(val uri: Uri, val takenAt: Long)

class GalleryRepository(private val context: Context) {

    /** Images captured on [date], newest first. Needs READ_MEDIA_IMAGES (or partial access). */
    suspend fun imagesOn(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<GalleryImage> =
        imagesBetween(date, date.plusDays(1), zone)

    /** Images captured in the [days] days ending with [today] (inclusive), newest first. */
    suspend fun recentImages(today: LocalDate, days: Long, zone: ZoneId = ZoneId.systemDefault()): List<GalleryImage> =
        imagesBetween(today.minusDays(days - 1), today.plusDays(1), zone)

    private suspend fun imagesBetween(from: LocalDate, toExclusive: LocalDate, zone: ZoneId): List<GalleryImage> =
        withContext(Dispatchers.IO) {
            val start = from.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = toExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATE_ADDED,
            )
            // DATE_TAKEN can be missing (e.g. screenshots); fall back to DATE_ADDED (seconds).
            val takenExpr = "COALESCE(${MediaStore.Images.Media.DATE_TAKEN}, ${MediaStore.Images.Media.DATE_ADDED} * 1000)"
            // The bounds are inlined, not bound as `?`: selection args are always TEXT, and an expression
            // like COALESCE(...) has no column affinity, so INTEGER >= TEXT would never match.
            // Both values are Longs computed here, so inlining is safe.
            val selection = "$takenExpr >= $start AND $takenExpr < $end"
            val args: Array<String>? = null
            buildList {
                context.contentResolver.query(collection, projection, selection, args, "$takenExpr DESC")?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val takenCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                    val addedCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        val taken = if (c.isNull(takenCol)) c.getLong(addedCol) * 1000 else c.getLong(takenCol)
                        add(GalleryImage(ContentUris.withAppendedId(collection, id), taken))
                    }
                }
            }
        }
}
