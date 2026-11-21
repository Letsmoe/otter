package com.letsmoe.otter.cast.browse

/**
 * What decides whether an idle TV shows the screensaver: [browsing] is true while a title page is
 * open on the phone, [playing] while a title plays on the TV, [televisionId] the connected DLNA TV.
 */
data class ScreensaverInputs(
    val browsing: Boolean,
    val televisionId: String?,
    val playing: Boolean,
    val enabled: Boolean,
    val encoderUsable: Boolean,
)

sealed interface ScreensaverAction {
    /** Leave the TV as it is. */
    data object Keep : ScreensaverAction

    /** Start the screensaver once the TV has been idle this long. */
    data object StartWhenIdle : ScreensaverAction

    /** Take the screensaver off the TV. */
    data object Clear : ScreensaverAction

    /** The screensaver is over without being stopped: something else loaded the TV, or it is gone. */
    data object Forget : ScreensaverAction
}

fun nextScreensaverAction(inputs: ScreensaverInputs, running: Boolean): ScreensaverAction {
    if (inputs.playing || inputs.televisionId == null) {
        return ScreensaverAction.Forget
    }
    if (!inputs.enabled) {
        return clearIfRunning(running)
    }
    // A title page loads its own picture over the screensaver.
    if (inputs.browsing) {
        return forgetIfRunning(running)
    }
    if (running || !inputs.encoderUsable) {
        return ScreensaverAction.Keep
    }
    return ScreensaverAction.StartWhenIdle
}

private fun clearIfRunning(running: Boolean): ScreensaverAction {
    if (running) {
        return ScreensaverAction.Clear
    }
    return ScreensaverAction.Keep
}

private fun forgetIfRunning(running: Boolean): ScreensaverAction {
    if (running) {
        return ScreensaverAction.Forget
    }
    return ScreensaverAction.Keep
}
