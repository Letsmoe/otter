package com.letsmoe.otter.ui.player

import com.letsmoe.otter.cast.overlay.CaptionCue
import com.letsmoe.otter.cast.overlay.loadCaptionCues
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToLong

/** Steps of the timing buttons, and how far captions can be moved either way. */
const val CAPTION_DELAY_STEP_SECONDS = 0.5
private const val CAPTION_DELAY_LIMIT_SECONDS = 30.0
// The phone reloads its caption file once the taps stop.
private const val PHONE_RELOAD_DELAY_MILLISECONDS = 700L

/**
 * The viewer's caption tweaks for the title playing: timing (positive:
 * later) and a second language with its cues, which the phone draws itself.
 */
data class CaptionExtrasState(
    val delaySeconds: Double = 0.0,
    val secondSubtitleId: String? = null,
    val secondCues: List<CaptionCue> = emptyList(),
    /** While casting, the phone lists what was just said, from this file. */
    val transcriptSubtitleId: String? = null,
    val transcriptCues: List<CaptionCue> = emptyList(),
)

/** Keeps the caption tweaks of one title; [onPhoneDelay] reloads the phone's captions with a new delay. */
class CaptionExtras(private val scope: CoroutineScope, private val onPhoneDelay: (Long) -> Unit) {

    private val mutableState = MutableStateFlow(CaptionExtrasState())
    val state: StateFlow<CaptionExtrasState> = mutableState

    private var phoneReload: Job? = null
    private var secondLoad: Job? = null
    private var transcriptLoad: Job? = null

    val delayMilliseconds: Long
        get() = (mutableState.value.delaySeconds * 1000).roundToLong()

    /** A new title starts with its captions as they are. */
    fun reset() {
        phoneReload?.cancel()
        secondLoad?.cancel()
        transcriptLoad?.cancel()
        mutableState.value = CaptionExtrasState()
    }

    /** Lists [subtitle]'s lines while casting; the same file isn't loaded twice. */
    fun showTranscript(subtitle: PlayerSubtitle?) {
        if (subtitle?.id == mutableState.value.transcriptSubtitleId) {
            return
        }
        transcriptLoad?.cancel()
        mutableState.update { current -> current.copy(transcriptSubtitleId = subtitle?.id, transcriptCues = emptyList()) }
        if (subtitle == null) {
            return
        }
        transcriptLoad = scope.launch {
            val cues = loadCaptionCues(subtitle.srtPath)
            mutableState.update { current -> current.copy(transcriptCues = cues) }
        }
    }

    /** Moves the captions by [stepSeconds] (negative: earlier); returns the new delay. */
    fun nudge(stepSeconds: Double): Double {
        val delaySeconds = (mutableState.value.delaySeconds + stepSeconds).coerceIn(-CAPTION_DELAY_LIMIT_SECONDS, CAPTION_DELAY_LIMIT_SECONDS)
        mutableState.update { current -> current.copy(delaySeconds = roundedToStep(delaySeconds)) }
        phoneReload?.cancel()
        phoneReload = scope.launch {
            delay(PHONE_RELOAD_DELAY_MILLISECONDS)
            onPhoneDelay(delayMilliseconds)
        }
        return mutableState.value.delaySeconds
    }

    /** Shows [subtitle] as the second language, or none. */
    fun chooseSecond(subtitle: PlayerSubtitle?) {
        secondLoad?.cancel()
        mutableState.update { current -> current.copy(secondSubtitleId = subtitle?.id, secondCues = emptyList()) }
        if (subtitle == null) {
            return
        }
        secondLoad = scope.launch {
            val cues = loadCaptionCues(subtitle.srtPath)
            mutableState.update { current -> current.copy(secondCues = cues) }
        }
    }
}

// Steps add up exactly, so 0 reads as 0 again after +0.5 and −0.5.
private fun roundedToStep(seconds: Double): Double {
    val steps = (seconds / CAPTION_DELAY_STEP_SECONDS).roundToLong()
    return steps * CAPTION_DELAY_STEP_SECONDS
}

/** "+1.5 s", "−0.5 s" or "0 s", as the timing row shows it. */
fun captionDelayLabel(delaySeconds: Double): String {
    if (delaySeconds == 0.0) {
        return "0 s"
    }
    val amount = abs(delaySeconds).toString().removeSuffix(".0")
    if (delaySeconds > 0) {
        return "+$amount s"
    }
    return "−$amount s"
}
