package edu.saihs.tripclip.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import edu.saihs.tripclip.TripClipApplication
import edu.saihs.tripclip.data.Photo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Debug-only: fills the database with sample photos from `src/debug/assets/samples`. */
class SampleDataReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as TripClipApplication
        val clear = intent.getBooleanExtra("clear", false)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.database.clearAllTables()
                if (!clear) {
                    val photos = samplePhotos(LocalDate.now())
                    app.database.photoDao().insertAll(photos)
                    Log.i(TAG, "Inserted ${photos.size} sample photos")
                } else {
                    Log.i(TAG, "Cleared all photos")
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun samplePhotos(today: LocalDate): List<Photo> {
        val zone = ZoneId.systemDefault()
        fun photo(daysAgo: Long, time: String, asset: String, place: String?, note: String?) = Photo(
            uri = "file:///android_asset/samples/$asset.jpg",
            takenAt = today.minusDays(daysAgo).atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli(),
            latitude = 25.04,
            longitude = 121.56,
            placeName = place,
            note = note,
        )
        return listOf(
            photo(3, "09:15", "tao_zhu_yin_yuan", "信義區", "早上的街道很安靜。"),
            photo(2, "13:40", "saihs_portrait", "松山文創園區", null),
            photo(2, "14:05", "saihs", "松山文創園區", "Sunny afternoon, perfect light."),
            photo(1, "10:20", "tao_zhu_yin_yuan", "陶朱隱園", "整棟樓像在慢慢轉身。"),
            photo(1, "10:34", "tao_zhu_yin_yuan", "陶朱隱園", "路口的斑馬線也好看。"),
            photo(1, "16:40", "saihs_portrait", "松山工農", "雲很厚，光很溫柔。"),
            photo(1, "16:52", "saihs", "松山工農", null),
            photo(1, "17:05", "saihs", "松山工農", "鐘塔剛好敲了五下。"),
            photo(1, "19:30", "tao_zhu_yin_yuan", "回家路上", "有點暗，但很開心。"),
            photo(0, "08:12", "tao_zhu_yin_yuan", null, "今日も一日がんばろう。"),
        )
    }

    private companion object {
        const val TAG = "SampleData"
    }
}
