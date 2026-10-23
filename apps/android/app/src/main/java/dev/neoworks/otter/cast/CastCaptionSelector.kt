package dev.neoworks.otter.cast

import androidx.media3.cast.CastTrackSelector
import androidx.media3.common.C
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionParameters
import com.google.common.collect.ImmutableSet

/**
 * Lets the player's caption choice reach the Chromecast receiver: the picked track, else the
 * preferred language, else none. Audio and video tracks stay as the receiver has them.
 */
class CastCaptionSelector : CastTrackSelector() {

    override fun evaluate(request: CastTrackSelectorRequest): CastTrackSelectorResult {
        val selections = ImmutableSet.builder<TrackGroup>()
        for (group in request.currentlySelectedTrackGroups) {
            if (group.type != C.TRACK_TYPE_TEXT) {
                selections.add(group)
            }
        }
        val captionGroup = chooseCaptionGroup(request.trackSelectionParameters, request.trackGroupList)
        if (captionGroup != null) {
            selections.add(captionGroup)
        }
        return request.buildResultUpon().setSelections(selections.build()).build()
    }

    private fun chooseCaptionGroup(parameters: TrackSelectionParameters, groups: List<TrackGroup>): TrackGroup? {
        if (parameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)) {
            return null
        }
        val captionGroups = groups.filter { group -> group.type == C.TRACK_TYPE_TEXT }
        val picked = captionGroups.firstOrNull { group -> parameters.overrides.containsKey(group) }
        if (picked != null) {
            return picked
        }
        return captionGroups.firstOrNull { group -> isPreferredLanguage(group, parameters.preferredTextLanguages) }
    }

    private fun isPreferredLanguage(group: TrackGroup, preferredLanguages: List<String>): Boolean {
        val language = group.getFormat(0).language ?: return false
        return preferredLanguages.any { preferred -> preferred.equals(language, ignoreCase = true) }
    }
}
