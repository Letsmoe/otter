package graphql

import (
	"context"

	"github.com/Letsmoe/otter/internal/api/graphql/model"
	"github.com/Letsmoe/otter/internal/library"
)

func linkReportModel(report *library.LinkReport) *model.LinkReport {
	if report == nil {
		return nil
	}
	return &model.LinkReport{
		ClaimedHeight:    report.ClaimedHeight,
		MeasuredHeight:   report.MeasuredHeight,
		Overclaimed:      report.Overclaimed(),
		BitrateBps:       report.BitrateBps,
		Stalls:           report.StallCount,
		StallMillis:      report.StallMillis,
		FirstFrameMillis: report.FirstFrameMillis,
		Plays:            report.Plays,
		RoughPlays:       report.RoughPlays,
	}
}

// reportLinkPlayback stores what the player measured, then announces the
// title's streams so open source lists show it.
func (r *Resolver) reportLinkPlayback(ctx context.Context, input model.LinkReportInput) (bool, error) {
	streamID, err := parseStream(input.StreamID)
	if err != nil {
		return false, err
	}
	measurement := library.PlaybackMeasurement{
		WidthPixels:      input.WidthPixels,
		HeightPixels:     input.HeightPixels,
		BitrateBps:       input.BitrateBps,
		StallCount:       input.Stalls,
		StallMillis:      input.StallMillis,
		FirstFrameMillis: input.FirstFrameMillis,
		PlayedMillis:     input.PlayedMillis,
	}
	if _, err := r.Library.Store.RecordLinkReport(ctx, streamID, measurement); err != nil {
		return false, err
	}
	stream, err := r.Library.Store.Stream(ctx, streamID)
	if err != nil || stream == nil {
		return true, err
	}
	r.Library.AnnounceStreams(context.Background(), library.MediaRef{MediaType: stream.MediaType, MediaID: stream.MediaID})
	return true, nil
}
