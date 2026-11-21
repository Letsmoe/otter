package com.letsmoe.otter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.letsmoe.otter.graphql.fragment.LinkReportFields
import com.letsmoe.otter.ui.theme.OtterColors

/** What the backend kept about a link's plays: measured against what the site claimed. */
data class LinkReportInfo(
    val claimedHeight: Int,
    val measuredHeight: Int,
    val overclaimed: Boolean,
    val bitrateBps: Int,
    val stalls: Int,
    val stallMillis: Int,
    val firstFrameMillis: Int,
    val plays: Int,
    val roughPlays: Int,
)

fun LinkReportFields.toInfo(): LinkReportInfo {
    return LinkReportInfo(claimedHeight, measuredHeight, overclaimed, bitrateBps, stalls, stallMillis, firstFrameMillis, plays, roughPlays)
}

/**
 * Where a link sorts among links of the same language and verdict: 0 played smoothly and
 * delivered what it claimed, 1 nobody reported it yet, 2 stalled a lot or claimed more than it had.
 */
fun linkReportRank(report: LinkReportInfo?): Int {
    if (report == null || report.plays <= 0) {
        return 1
    }
    if (report.roughPlays > 0 || report.overclaimed) {
        return 2
    }
    return 0
}

enum class BadgeTone { GOOD, NEUTRAL, WARNING }

data class LinkBadge(val text: String, val tone: BadgeTone)

// Starts quicker than this are worth a badge, and so are starts slower than the other one.
private const val QUICK_START_MILLISECONDS = 2000
private const val SLOW_START_MILLISECONDS = 5000

/** The few facts about a link's last plays that decide whether to pick it: resolution, stalls, start, bitrate. */
fun linkBadges(report: LinkReportInfo?): List<LinkBadge> {
    if (report == null || report.plays <= 0) {
        return emptyList()
    }
    return listOfNotNull(resolutionBadge(report), stallBadge(report), startBadge(report), bitrateBadge(report))
}

private fun resolutionBadge(report: LinkReportInfo): LinkBadge? {
    if (report.measuredHeight <= 0) {
        return null
    }
    val measured = resolutionLabel(report.measuredHeight)
    if (!report.overclaimed) {
        return LinkBadge(measured, BadgeTone.GOOD)
    }
    return LinkBadge("$measured, not ${resolutionLabel(report.claimedHeight)}", BadgeTone.WARNING)
}

private fun stallBadge(report: LinkReportInfo): LinkBadge? {
    if (report.stalls > 0) {
        return LinkBadge("${stallWords(report.stalls)} (${seconds(report.stallMillis)})", BadgeTone.WARNING)
    }
    if (report.roughPlays > 0) {
        return LinkBadge("Stalled before", BadgeTone.WARNING)
    }
    return LinkBadge("No stalls", BadgeTone.GOOD)
}

private fun stallWords(stalls: Int): String {
    if (stalls == 1) {
        return "1 stall"
    }
    return "$stalls stalls"
}

private fun startBadge(report: LinkReportInfo): LinkBadge? {
    if (report.firstFrameMillis <= 0) {
        return null
    }
    if (report.firstFrameMillis < QUICK_START_MILLISECONDS) {
        return LinkBadge("Quick start", BadgeTone.GOOD)
    }
    if (report.firstFrameMillis > SLOW_START_MILLISECONDS) {
        return LinkBadge("Slow start, ${seconds(report.firstFrameMillis)}", BadgeTone.WARNING)
    }
    return null
}

private fun bitrateBadge(report: LinkReportInfo): LinkBadge? {
    if (report.bitrateBps <= 0) {
        return null
    }
    return LinkBadge("%.1f Mbit/s".format(report.bitrateBps / 1_000_000.0), BadgeTone.NEUTRAL)
}

private fun seconds(milliseconds: Int): String {
    return "%.0f s".format(milliseconds / 1000.0)
}

/** "1080p" for a pixel height, nearest standard step below it; "4K" from 2000 up. */
fun resolutionLabel(height: Int): String {
    return when {
        height >= 2000 -> "4K"
        height >= 1400 -> "1440p"
        height >= 1000 -> "1080p"
        height >= 650 -> "720p"
        height >= 450 -> "480p"
        height >= 300 -> "360p"
        else -> "${height}p"
    }
}

/** Small coloured chips: green delivered, red fell short, grey is plain information. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LinkBadgeRow(badges: List<LinkBadge>, modifier: Modifier = Modifier) {
    if (badges.isEmpty()) {
        return
    }
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (badge in badges) {
            Text(
                badge.text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = badgeColor(badge.tone),
                modifier = Modifier.background(badgeColor(badge.tone).copy(alpha = 0.14f), CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

private fun badgeColor(tone: BadgeTone): Color {
    return when (tone) {
        BadgeTone.GOOD -> OtterColors.success
        BadgeTone.WARNING -> OtterColors.warning
        BadgeTone.NEUTRAL -> Color.White.copy(alpha = 0.7f)
    }
}
