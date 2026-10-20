package edu.saihs.tripclip.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Photo::class], version = 1, exportSchema = true)
abstract class TripClipDatabase : RoomDatabase() {

    abstract fun photoDao(): PhotoDao

    companion object {
        fun create(context: Context): TripClipDatabase =
            Room.databaseBuilder(context, TripClipDatabase::class.java, "tripclip.db").build()
    }
}
