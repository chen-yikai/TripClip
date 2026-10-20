package edu.saihs.tripclip.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Turns gallery / picker uris into [Photo]s: capture time and GPS from EXIF, place from the geocoder. */
class PhotoImporter(
    private val context: Context,
    private val repository: PhotoRepository,
    private val locations: LocationRepository,
) {

    /**
     * Imports [uris] in the given order, each filed under the day it was captured.
     * Returns the resolved capture time (epoch millis) of every uri, in input order.
     */
    suspend fun import(uris: List<Uri>, knownTakenAt: Map<Uri, Long> = emptyMap()): List<Long> {
        val photos = uris.map { uri ->
            if (uri.authority != MediaStore.AUTHORITY || uri.pathSegments.firstOrNull() == "picker") {
                // System photo picker grants: keep read access across restarts.
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            val exif = readExif(uri)
            val takenAt = knownTakenAt[uri]
                ?: exif?.takenAt
                ?: queryTakenAt(uri)
                ?: System.currentTimeMillis()
            val place = exif?.latLng?.let { (lat, lng) -> locations.placeAt(lat, lng) }
            Photo(
                uri = uri.toString(),
                takenAt = takenAt,
                latitude = exif?.latLng?.first,
                longitude = exif?.latLng?.second,
                placeName = place?.name,
            )
        }
        repository.addPhotos(photos)
        return photos.map { it.takenAt }
    }

    /**
     * Capture time from the content provider when EXIF has none (e.g. stripped metadata).
     * Works for MediaStore and system photo picker uris (both expose `datetaken`).
     */
    private suspend fun queryTakenAt(uri: Uri): Long? = withContext(Dispatchers.IO) {
        fun queryLong(column: String): Long? = runCatching {
            context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0).takeIf { it > 0 } else null
            }
        }.getOrNull()
        queryLong(MediaStore.MediaColumns.DATE_TAKEN)
            ?: queryLong(MediaStore.MediaColumns.DATE_ADDED)?.times(1000) // seconds
    }

    /**
     * Fills in GPS + place for stored photos that have none, e.g. ones imported through the
     * system photo picker before it was resolved to the original. Safe to call repeatedly.
     */
    suspend fun backfillLocations() {
        for (photo in repository.getPhotosWithoutLocation()) {
            val latLng = readExif(Uri.parse(photo.uri))?.latLng ?: continue
            val place = locations.placeAt(latLng.first, latLng.second)
            repository.updatePhoto(
                photo.copy(latitude = latLng.first, longitude = latLng.second, placeName = photo.placeName ?: place?.name),
            )
        }
    }

    private class ExifData(val takenAt: Long?, val latLng: Pair<Double, Double>?)

    /**
     * The MediaStore image behind [uri]. System photo picker uris
     * (content://media/picker/0/<provider>/media/<id>) end with the MediaStore id of local photos.
     */
    private fun mediaStoreUri(uri: Uri): Uri? {
        if (uri.authority != MediaStore.AUTHORITY) return null
        val segments = uri.pathSegments
        if (segments.firstOrNull() != "picker") return uri
        // Only local items carry MediaStore ids; cloud items come from other providers.
        if (segments.getOrNull(2) != LocalPickerProvider) return null
        val id = uri.lastPathSegment?.toLongOrNull() ?: return null
        return ContentUris.withAppendedId(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL), id)
    }

    private fun openExif(uri: Uri): ExifInterface? =
        runCatching { context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) } }.getOrNull()

    private suspend fun readExif(uri: Uri): ExifData? = withContext(Dispatchers.IO) {
        // The photo picker and MediaStore both redact GPS tags; with ACCESS_MEDIA_LOCATION (and read
        // access to the MediaStore item) the original file, GPS included, can be opened instead.
        val canReadLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val original = if (canReadLocation) {
            mediaStoreUri(uri)?.let { runCatching { MediaStore.setRequireOriginal(it) }.getOrNull() }
        } else null
        val exif = original?.let(::openExif)?.takeIf { it.latLong != null }
            ?: openExif(uri)
            ?: return@withContext null
        val latLng = exif.latLong?.let { it[0] to it[1] }
        val takenAt = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let { value ->
            runCatching {
                LocalDateTime.parse(value, ExifDateFormat).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrNull()
        }
        ExifData(takenAt, latLng)
    }

    private companion object {
        val ExifDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
        const val LocalPickerProvider = "com.android.providers.media.photopicker"
    }
}
