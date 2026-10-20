package edu.saihs.tripclip.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {

    /** Photos whose capture time falls in [startMillis, endMillis), oldest first. */
    @Query("SELECT * FROM photos WHERE taken_at >= :startMillis AND taken_at < :endMillis ORDER BY taken_at ASC")
    fun observeBetween(startMillis: Long, endMillis: Long): Flow<List<Photo>>

    @Query("SELECT taken_at FROM photos ORDER BY taken_at ASC")
    fun observeAllTakenAt(): Flow<List<Long>>

    @Query("SELECT * FROM photos WHERE id = :id")
    fun observeById(id: Long): Flow<Photo?>

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun getById(id: Long): Photo?

    @Query("SELECT * FROM photos ORDER BY taken_at DESC LIMIT 1")
    suspend fun getLatest(): Photo?

    @Query("SELECT * FROM photos WHERE latitude IS NULL OR longitude IS NULL")
    suspend fun getWithoutLocation(): List<Photo>

    @Query("SELECT COUNT(*) FROM photos WHERE taken_at BETWEEN :fromMillis AND :toMillis")
    suspend fun countBetween(fromMillis: Long, toMillis: Long): Int

    @Query("SELECT * FROM photos WHERE uri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): Photo?

    @Query("SELECT uri FROM photos WHERE uri IN (:uris)")
    suspend fun findExistingUris(uris: List<String>): List<String>

    @Insert
    suspend fun insert(photo: Photo): Long

    @Insert
    suspend fun insertAll(photos: List<Photo>): List<Long>

    @Update
    suspend fun update(photo: Photo)

    @Delete
    suspend fun delete(photo: Photo)
}
