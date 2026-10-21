package edu.saihs.tripclip.data

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Inserts mock journal photos for development: the images and `mock_photos.json` (Traditional
 * Chinese; `mock_photos_en.json` / `mock_photos_ja.json` for English / Japanese) in the debug
 * assets (`app/src/debug/assets/mock/`), so release builds don't ship them.
 *
 * Each JSON entry: `file`, a capture time, and optional `placeName`, `latitude`, `longitude`, `note`.
 * The capture time is either `takenAt` (ISO-8601 with offset) or, for dates relative to when the
 * data is inserted, `daysAgo` (0 = today) plus a local `time` ("HH:mm:ss"); a relative time that
 * hasn't happened yet today is moved to just before now, keeping the entries' order.
 *
 * Images are copied into app storage and shared through the FileProvider. Safe to run repeatedly:
 * mock photos inserted before are updated to match the JSON, and other photos already in the
 * journal at the same capture time (e.g. imported from the gallery) are left alone. [clear] removes
 * the mock photos (recognised by their FileProvider uri) and their copied images.
 */
class MockDataSeeder(
    private val context: Context,
    private val repository: PhotoRepository,
) {

    /** True in debuggable builds that bundle the mock assets. */
    val isAvailable: Boolean by lazy {
        val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        debuggable && runCatching { context.assets.list(AssetDir)?.contains(DataFile) == true }.getOrDefault(false)
    }

    /**
     * Inserts the mock photos with place names and notes in [languageTag] ("zh-TW", "en" or "ja"),
     * updating ones inserted before — so picking another language re-labels them.
     */
    suspend fun insert(languageTag: String): InsertResult = withContext(Dispatchers.IO) {
        val json = context.assets.open("$AssetDir/${dataFileFor(languageTag)}").bufferedReader().use { it.readText() }
        val entries = JSONArray(json)
        val imageDir = File(context.filesDir, ImageDir).apply { mkdirs() }
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        var updated = 0

        val photos = (0 until entries.length()).mapNotNull { index ->
            val entry = entries.getJSONObject(index)
            val takenAt = if (entry.has("takenAt")) {
                OffsetDateTime.parse(entry.getString("takenAt")).toInstant().toEpochMilli()
            } else {
                val day = LocalDate.now(zone).minusDays(entry.getLong("daysAgo"))
                val planned = day.atTime(LocalTime.parse(entry.getString("time"))).atZone(zone).toInstant().toEpochMilli()
                // Not yet happened today: just before now, later entries closer to now.
                if (planned <= now) planned else now - (entries.length() - index) * 60_000L
            }

            val fileName = entry.getString("file")
            val image = File(imageDir, fileName)
            if (!image.exists()) {
                context.assets.open("$AssetDir/$fileName").use { input ->
                    image.outputStream().use { input.copyTo(it) }
                }
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", image).toString()
            val photo = Photo(
                uri = uri,
                takenAt = takenAt,
                latitude = entry.optDoubleOrNull("latitude"),
                longitude = entry.optDoubleOrNull("longitude"),
                placeName = entry.optStringOrNull("placeName"),
                note = entry.optStringOrNull("note")?.take(NOTE_MAX_LENGTH),
            )
            val existing = repository.getPhotoByUri(uri)
            when {
                // Inserted by an earlier run: bring it in line with the JSON (e.g. a moved date).
                existing != null -> {
                    repository.updatePhoto(photo.copy(id = existing.id))
                    updated++
                    null
                }
                // The same shot is already in the journal from elsewhere (e.g. imported from the gallery).
                repository.hasPhotoTakenAt(takenAt) -> null
                else -> photo
            }
        }
        InsertResult(added = repository.addPhotos(photos).size, updated = updated)
    }

    /** Deletes the mock photos and their copied images; returns how many photos were removed. */
    suspend fun clear(): Int = withContext(Dispatchers.IO) {
        val imageDir = File(context.filesDir, ImageDir)
        // Every mock image is shared from the same FileProvider directory, so its uri prefix marks them.
        val prefix = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(imageDir, "x"))
            .toString().removeSuffix("x")
        val removed = repository.deletePhotosWithUriPrefix(prefix)
        imageDir.deleteRecursively()
        removed
    }

    /** [added] new photos; [updated] mock photos from an earlier run brought in line with the JSON. */
    data class InsertResult(val added: Int, val updated: Int)

    /** The same photos, times and GPS in every file; only `placeName` and `note` are translated. */
    private fun dataFileFor(languageTag: String): String = when {
        languageTag.startsWith("en") -> "mock_photos_en.json"
        languageTag.startsWith("ja") -> "mock_photos_ja.json"
        else -> DataFile // Traditional Chinese
    }

    private fun JSONObject.optDoubleOrNull(name: String): Double? =
        if (has(name) && !isNull(name)) getDouble(name) else null

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (has(name) && !isNull(name)) getString(name).ifBlank { null } else null

    private companion object {
        const val AssetDir = "mock"
        const val DataFile = "mock_photos.json"
        /** Under filesDir; must match the `files-path` in res/xml/file_paths.xml. */
        const val ImageDir = "mock"
    }
}
