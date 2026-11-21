package com.letsmoe.otter.ui.title

import android.text.format.DateFormat
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Date
import kotlin.math.roundToInt

private const val MILLISECONDS_PER_MINUTE = 60_000L

/** Minutes Play still has to run: the runtime less what the resume point covers; null when the runtime is unknown. */
fun remainingMinutes(runtimeMinutes: Int?, resumeFraction: Float?): Int? {
    if (runtimeMinutes == null || runtimeMinutes <= 0) {
        return null
    }
    var watchedFraction = 0f
    if (resumeFraction != null) {
        watchedFraction = resumeFraction
    }
    val remaining = (runtimeMinutes * (1f - watchedFraction)).roundToInt()
    if (remaining <= 0) {
        return null
    }
    return remaining
}

/** What Play of the series' up-next episode has left to run. */
fun upNextRemainingMinutes(upNext: UpNext?): Int? {
    if (upNext == null) {
        return null
    }
    var resumeFraction: Float? = null
    if (upNext.resume) {
        resumeFraction = upNext.episode.progress
    }
    return remainingMinutes(upNext.episode.runtime, resumeFraction)
}

fun endsAtMillis(nowMillis: Long, remainingMinutes: Int): Long = nowMillis + remainingMinutes * MILLISECONDS_PER_MINUTE

/** Time until the wall clock shows the next minute. */
fun millisUntilNextMinute(nowMillis: Long): Long = MILLISECONDS_PER_MINUTE - nowMillis % MILLISECONDS_PER_MINUTE

/** The current time, updated whenever the wall clock shows a new minute. */
@Composable
private fun rememberMinuteClock(): State<Long> {
    val nowMillis = remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(millisUntilNextMinute(System.currentTimeMillis()))
            nowMillis.value = System.currentTimeMillis()
        }
    }
    return nowMillis
}

/** "Ends at 23:10" if Play starts now, in the phone's 12 or 24 hour format. */
@Composable
fun EndTimeLabel(remainingMinutes: Int?, modifier: Modifier = Modifier) {
    if (remainingMinutes == null) {
        return
    }
    val context = LocalContext.current
    val nowMillis by rememberMinuteClock()
    val endsAt = Date(endsAtMillis(nowMillis, remainingMinutes))
    val clock = DateFormat.getTimeFormat(context).format(endsAt)
    Text("Ends at $clock", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1, modifier = modifier)
}
