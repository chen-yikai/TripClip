package edu.saihs.tripclip.ui.collage

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** Saves or shares a rendered collage bitmap. */
object CollageExporter {

    private const val INSTAGRAM_PACKAGE = "com.instagram.android"
    private const val INSTAGRAM_STORY_ACTION = "com.instagram.share.ADD_TO_STORY"

    /** Writes the collage to Pictures/TripClip; returns the new MediaStore uri. */
    suspend fun saveToGallery(context: Context, bitmap: Bitmap, date: LocalDate): Uri? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "TripClip_${date}_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TripClip")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
        val ok = runCatching {
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) } == true
        }.getOrDefault(false)
        if (!ok) {
            resolver.delete(uri, null, null)
            return@withContext null
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        uri
    }

    /** Opens the system share sheet with the collage (any app); nothing is saved to the gallery. */
    suspend fun share(context: Context, bitmap: Bitmap) {
        val uri = writeShareFile(context, bitmap, "tripclip_collage.jpg")
        openShareSheet(context, uri)
    }

    /**
     * The system share sheet for an image. The uri also goes in as ClipData (with read permission),
     * which is what makes the share sheet show the image preview at the top.
     */
    private fun openShareSheet(context: Context, uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Writes the collage to the shared cache folder (see file_paths.xml) and returns its FileProvider uri. */
    private suspend fun writeShareFile(context: Context, bitmap: Bitmap, name: String): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** Opens Instagram's "add to story" with the collage, or the system share sheet if Instagram is missing. */
    suspend fun shareToStory(context: Context, bitmap: Bitmap): Boolean {
        val uri = writeShareFile(context, bitmap, "tripclip_story.jpg")
        val story = Intent(INSTAGRAM_STORY_ACTION).apply {
            setDataAndType(uri, "image/jpeg")
            setPackage(INSTAGRAM_PACKAGE)
            putExtra("source_application", context.packageName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.grantUriPermission(INSTAGRAM_PACKAGE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return try {
            context.startActivity(story)
            true
        } catch (_: ActivityNotFoundException) {
            openShareSheet(context, uri)
            true
        }
    }
}
