package edu.saihs.tripclip.ui.theme

import android.content.res.Resources
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.unit.sp
import edu.saihs.tripclip.R
import java.util.concurrent.ConcurrentHashMap
import android.graphics.Typeface as AndroidTypeface
import android.graphics.fonts.Font as AndroidFont
import android.graphics.fonts.FontFamily as AndroidFontFamily

/**
 * Handwriting font for the journal list, text inside polaroid frames and the collage.
 * Other screens use the default font.
 *
 * Latin letters, digits and symbols use Caveat (variable, wght 400–700); any glyph Caveat
 * lacks (CJK, full-width punctuation) falls back per character to 沉魚落雁體, or to
 * YorutegakiRegular when the UI language is Japanese.
 */
@Composable
fun handwritingFontFamily(weight: FontWeight = FontWeight.Normal): FontFamily {
    val resources = LocalResources.current
    val japanese = LocalConfiguration.current.locales[0].language == "ja"
    return remember(weight, japanese) { HandwritingFonts.get(resources, weight.weight, japanese) }
}

private object HandwritingFonts {
    private val families = ConcurrentHashMap<Pair<Int, Boolean>, FontFamily>()

    // The CJK faces are large (3–10 MB), so each is parsed at most once.
    private val cjkFallbacks = ConcurrentHashMap<Boolean, AndroidFontFamily>()

    fun get(resources: Resources, weight: Int, japanese: Boolean): FontFamily {
        val wght = weight.coerceIn(400, 700)
        return families.getOrPut(wght to japanese) {
            val caveat = AndroidFont.Builder(resources, R.font.caveat_variable)
                .setFontVariationSettings("'wght' $wght")
                .setWeight(wght)
                .build()
            val cjk = cjkFallbacks.getOrPut(japanese) {
                val res = if (japanese) R.font.yorutegaki_regular else R.font.chenyuluoyan_thin
                AndroidFontFamily.Builder(AndroidFont.Builder(resources, res).build()).build()
            }
            val typeface = AndroidTypeface.CustomFallbackBuilder(AndroidFontFamily.Builder(caveat).build())
                .addCustomFallback(cjk)
                .build()
            FontFamily(Typeface(typeface))
        }
    }
}

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)
