package com.letsmoe.otter.ui.navigation

import androidx.navigation.NavHostController
import com.letsmoe.otter.cast.TelevisionConnection
import com.letsmoe.otter.ui.player.PlayerRequest
import com.letsmoe.otter.ui.player.TelevisionPlayback

/** Plays on the connected TV when there is one (the phone stays where it is), else opens the player screen. */
fun NavHostController.play(request: PlayerRequest) {
    if (TelevisionConnection.isConnected) {
        TelevisionPlayback.play(request)
        return
    }
    navigate(Routes.player(request.mediaId, request.streamId, request.downloadId, request.startSeconds))
}
