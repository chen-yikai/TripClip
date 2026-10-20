package edu.saihs.tripclip.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PhotoRepository(
    private val dao: PhotoDao,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {

    fun observePhotosOn(date: LocalDate): Flow<List<Photo>> {
        val z = zone()
        val start = date.atStartOfDay(z).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(z).toInstant().toEpochMilli()
        return dao.observeBetween(start, end)
    }

    /** Photos from [start] (inclusive) to [endExclusive], oldest first. */
    fun observePhotosBetween(start: LocalDate, endExclusive: LocalDate): Flow<List<Photo>> {
        val z = zone()
        return dao.observeBetween(
            start.atStartOfDay(z).toInstant().toEpochMilli(),
            endExclusive.atStartOfDay(z).toInstant().toEpochMilli(),
        )
    }

    /** Number of photos per local date, for the date chips and the calendar. */
    fun observePhotoCountByDate(): Flow<Map<LocalDate, Int>> =
        dao.observeAllTakenAt()
            .map { times ->
                val z = zone()
                times.groupingBy { Instant.ofEpochMilli(it).atZone(z).toLocalDate() }.eachCount()
            }
            .distinctUntilChanged()

    fun observePhoto(id: Long): Flow<Photo?> = dao.observeById(id)

    suspend fun getPhoto(id: Long): Photo? = dao.getById(id)

    suspend fun getLatestPhoto(): Photo? = dao.getLatest()

    suspend fun getPhotoByUri(uri: String): Photo? = dao.getByUri(uri)

    suspend fun getPhotosWithoutLocation(): List<Photo> = dao.getWithoutLocation()

    /** Whether a photo captured within a second of [takenAt] is already stored. */
    suspend fun hasPhotoTakenAt(takenAt: Long): Boolean = dao.countBetween(takenAt - 1_000, takenAt + 1_000) > 0

    suspend fun addPhoto(photo: Photo): Long = dao.insert(photo)

    /** Inserts photos whose uri isn't stored yet; returns the new ids. */
    suspend fun addPhotos(photos: List<Photo>): List<Long> {
        if (photos.isEmpty()) return emptyList()
        val existing = dao.findExistingUris(photos.map { it.uri }).toSet()
        return dao.insertAll(photos.filterNot { it.uri in existing })
    }

    suspend fun updatePhoto(photo: Photo) = dao.update(photo)

    suspend fun updateNote(id: Long, note: String?) {
        val photo = dao.getById(id) ?: return
        dao.update(photo.copy(note = note?.trim()?.take(NOTE_MAX_LENGTH)?.ifEmpty { null }))
    }

    suspend fun deletePhoto(id: Long) {
        dao.getById(id)?.let { dao.delete(it) }
    }
}
