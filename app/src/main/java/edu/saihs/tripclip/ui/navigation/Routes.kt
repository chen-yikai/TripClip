package edu.saihs.tripclip.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * 旅程列表（首頁）. [epochDay] is the day to open on; null means today.
 * [focusPhotoUri] scrolls the list to that photo once it shows up (e.g. right after adding it).
 */
@Serializable
data class Journal(val epochDay: Long? = null, val focusPhotoUri: String? = null) : NavKey {
    val date: LocalDate get() = epochDay?.let(LocalDate::ofEpochDay) ?: LocalDate.now()
}

/** 新增照片：today's photos from the system gallery. */
@Serializable
data object PhotoPicker : NavKey

/** 拍照記錄. */
@Serializable
data object Camera : NavKey

/**
 * 添加心得 for a stored photo. [fromCamera] is true right after a capture, enabling "retake".
 * [photoUri] lets the screen draw the photo on its first frame, for the shared-element transition.
 */
@Serializable
data class EditNote(val photoId: Long, val fromCamera: Boolean = false, val photoUri: String? = null) : NavKey

/** 查看照片: swipe through the photos of [epochDay], starting at [photoId]. */
@Serializable
data class PhotoViewer(val epochDay: Long, val photoId: Long, val photoUri: String? = null) : NavKey {
    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)
}

/** 每日拼貼 for one day. */
@Serializable
data class DailyCollage(val epochDay: Long) : NavKey {
    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)
}

/** 月曆選擇日期, opened on the month of [epochDay]. */
@Serializable
data class CalendarPicker(val epochDay: Long) : NavKey {
    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)
}

/** 設定：UI language and the about section. */
@Serializable
data object Settings : NavKey
