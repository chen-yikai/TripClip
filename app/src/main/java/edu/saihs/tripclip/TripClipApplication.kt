package edu.saihs.tripclip

import android.app.Application
import edu.saihs.tripclip.data.GalleryRepository
import edu.saihs.tripclip.data.LocationRepository
import edu.saihs.tripclip.data.MockDataSeeder
import edu.saihs.tripclip.data.PhotoImporter
import edu.saihs.tripclip.data.PhotoRepository
import edu.saihs.tripclip.data.TripClipDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TripClipApplication : Application() {

    val database: TripClipDatabase by lazy { TripClipDatabase.create(this) }

    val photoRepository: PhotoRepository by lazy { PhotoRepository(database.photoDao()) }

    val galleryRepository: GalleryRepository by lazy { GalleryRepository(this) }

    val locationRepository: LocationRepository by lazy { LocationRepository(this) }

    val photoImporter: PhotoImporter by lazy { PhotoImporter(this, photoRepository, locationRepository) }

    val mockDataSeeder: MockDataSeeder by lazy { MockDataSeeder(this, photoRepository) }

    /** For app-wide background work that outlives any screen. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Recover GPS/place for photos imported before their original EXIF could be read.
        applicationScope.launch { runCatching { photoImporter.backfillLocations() } }
    }
}
