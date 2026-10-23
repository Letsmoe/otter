package graphql

import (
	"context"

	"github.com/neoworks/otter/internal/api/graphql/model"
	"github.com/neoworks/otter/internal/downloads"
	"github.com/neoworks/otter/internal/library"
)

var downloadStatuses = map[downloads.Status]model.DownloadStatus{
	downloads.StatusQueued:      model.DownloadStatusQueued,
	downloads.StatusDownloading: model.DownloadStatusDownloading,
	downloads.StatusDone:        model.DownloadStatusDone,
	downloads.StatusFailed:      model.DownloadStatusFailed,
}

// downloadModel attaches the movie (or the episode and its series); nil when
// the title is no longer stored.
func (r *Resolver) downloadModel(ctx context.Context, download downloads.Download) (*model.Download, error) {
	titleRef := download.Ref()
	var episode *model.Episode
	if download.MediaType == library.MediaTypeEpisode {
		titleRef = library.MediaRef{MediaType: library.MediaTypeSeries, MediaID: download.SeriesID}
		stored, err := r.Library.Store.Episode(ctx, download.MediaID)
		if err != nil {
			return nil, err
		}
		if stored != nil {
			episode = episodeModel(*stored)
		}
	}
	title, err := r.Library.Store.Title(ctx, titleRef)
	if err != nil || title == nil {
		return nil, err
	}
	return &model.Download{
		ID:        formatID(kindDownload, download.ID),
		Status:    downloadStatuses[download.Status],
		Progress:  download.Progress,
		SizeBytes: float64(download.SizeBytes),
		Error:     optional(download.Error),
		CreatedAt: download.CreatedAt,
		Title:     titleModel(*title),
		Episode:   episode,
		URL:       optional(download.FileURL),
		MimeType:  optional(download.MimeType),
	}, nil
}

// optionalStream parses an optional stream id; 0 when none was given.
func optionalStream(streamID *string) (int, error) {
	if streamID == nil || *streamID == "" {
		return 0, nil
	}
	return parseStream(*streamID)
}
