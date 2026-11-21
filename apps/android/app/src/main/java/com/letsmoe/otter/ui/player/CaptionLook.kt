package com.letsmoe.otter.ui.player

import android.graphics.Color
import androidx.media3.ui.CaptionStyleCompat
import com.letsmoe.otter.graphql.fragment.ProfileFields
import com.letsmoe.otter.graphql.type.SubtitleSize
import com.letsmoe.otter.graphql.type.SubtitleStyle

// The SubtitleView default; Medium.
private const val MEDIUM_TEXT_FRACTION = 0.0533f

/** Caption colours shared by the phone's player and the TV's picture. */
object CaptionColors {
    val OUTLINE = Color.argb(230, 0, 0, 0)
    val BOX = Color.argb(190, 0, 0, 0)
    val YELLOW = Color.rgb(255, 221, 51)
    /** The second language's captions under white ones. */
    val SECOND = Color.rgb(205, 205, 205)
}

/** How the profile wants captions: text height as a fraction of the picture's, and the style. */
data class CaptionLook(val textFraction: Float, val style: SubtitleStyle) {
    companion object {
        val DEFAULT = CaptionLook(MEDIUM_TEXT_FRACTION, SubtitleStyle.OUTLINE)
    }
}

fun captionLookOf(profile: ProfileFields?): CaptionLook {
    if (profile == null) {
        return CaptionLook.DEFAULT
    }
    return CaptionLook(subtitleTextFraction(profile.subtitleSize), knownStyle(profile.subtitleStyle))
}

/** Text size as a fraction of the video height. */
fun subtitleTextFraction(size: SubtitleSize): Float {
    return when (size) {
        SubtitleSize.SMALL -> 0.045f
        SubtitleSize.LARGE -> 0.07f
        else -> MEDIUM_TEXT_FRACTION
    }
}

// A style from a newer backend reads as the outline.
fun knownStyle(style: SubtitleStyle): SubtitleStyle {
    if (style == SubtitleStyle.UNKNOWN__) {
        return SubtitleStyle.OUTLINE
    }
    return style
}

/** The phone player's SubtitleView style for [style]. */
fun phoneCaptionStyle(style: SubtitleStyle): CaptionStyleCompat {
    return when (style) {
        SubtitleStyle.BOX -> captionStyle(Color.WHITE, CaptionColors.BOX, CaptionStyleCompat.EDGE_TYPE_NONE)
        SubtitleStyle.YELLOW -> captionStyle(CaptionColors.YELLOW, Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE)
        else -> captionStyle(Color.WHITE, Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE)
    }
}

private fun captionStyle(foreground: Int, background: Int, edgeType: Int): CaptionStyleCompat {
    return CaptionStyleCompat(foreground, background, Color.TRANSPARENT, edgeType, Color.BLACK, null)
}
