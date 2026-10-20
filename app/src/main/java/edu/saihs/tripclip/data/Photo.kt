package edu.saihs.tripclip.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

const val NOTE_MAX_LENGTH = 60

@Entity(
    tableName = "photos",
    indices = [Index("taken_at")],
)
data class Photo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** content:// uri of the image (MediaStore or app-private file provider). */
    val uri: String,
    /** Capture time, epoch millis. */
    @ColumnInfo(name = "taken_at") val takenAt: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @ColumnInfo(name = "place_name") val placeName: String? = null,
    /** Short caption, at most [NOTE_MAX_LENGTH] characters. */
    val note: String? = null,
)
