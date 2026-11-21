package com.letsmoe.otter.cast.browse

/**
 * Everything that decides what the TV shows while browsing. [televisionId] is
 * the connected DLNA TV (null for none or a Chromecast); [playing] is true
 * while a title plays on the TV, which then owns it.
 */
data class BrowseInputs(
    val preview: BrowsePreview?,
    val televisionId: String?,
    val playing: Boolean,
    val enabled: Boolean,
    val encoderUsable: Boolean,
)

/** The preview a TV shows right now. */
data class ShownPreview(val televisionId: String, val preview: BrowsePreview)

sealed interface BrowseAction {
    /** Leave the TV as it is. */
    data object Keep : BrowseAction

    /** Show [preview] on the TV. */
    data class Show(val preview: BrowsePreview) : BrowseAction

    /** Take the preview off the TV. */
    data object Clear : BrowseAction

    /** The TV isn't ours any more (a title plays, or it is gone): forget what it showed. */
    data object Forget : BrowseAction
}

/**
 * What to do about the TV. Leaving a title page keeps its preview up; a title
 * that starts playing, or a TV that goes away, ends it.
 */
fun nextBrowseAction(inputs: BrowseInputs, shown: ShownPreview?): BrowseAction {
    if (inputs.playing || inputs.televisionId == null) {
        return BrowseAction.Forget
    }
    if (!inputs.enabled) {
        return clearIfShown(shown)
    }
    val wanted = inputs.preview
    if (wanted == null || !inputs.encoderUsable) {
        return BrowseAction.Keep
    }
    if (shown == ShownPreview(inputs.televisionId, wanted)) {
        return BrowseAction.Keep
    }
    return BrowseAction.Show(wanted)
}

private fun clearIfShown(shown: ShownPreview?): BrowseAction {
    if (shown == null) {
        return BrowseAction.Keep
    }
    return BrowseAction.Clear
}
