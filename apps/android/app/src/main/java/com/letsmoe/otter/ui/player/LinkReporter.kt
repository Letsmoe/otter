package com.letsmoe.otter.ui.player

import com.letsmoe.otter.AppScope
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.graphql.ReportLinkPlaybackMutation
import com.letsmoe.otter.graphql.type.LinkReportInput
import kotlinx.coroutines.launch

/** Sends what a link delivered to the backend, which ranks links with it. Best-effort. */
object LinkReporter {

    fun send(measurement: LinkMeasurement) {
        val input = LinkReportInput(
            streamId = measurement.streamId,
            widthPixels = measurement.widthPixels,
            heightPixels = measurement.heightPixels,
            bitrateBps = measurement.bitrateBps,
            stalls = measurement.stalls,
            stallMillis = measurement.stallMillis,
            firstFrameMillis = measurement.firstFrameMillis,
            playedMillis = measurement.playedMillis,
        )
        AppScope.launch {
            try {
                OtterApi.client.mutation(ReportLinkPlaybackMutation(input)).execute()
            } catch (error: Exception) {
                // A lost report only means the link is judged on fewer plays.
            }
        }
    }
}
