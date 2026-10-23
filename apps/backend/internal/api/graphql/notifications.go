package graphql

import (
	"context"

	"github.com/neoworks/otter/internal/api/graphql/model"
	"github.com/neoworks/otter/internal/library"
	"github.com/neoworks/otter/internal/notify"
)

var notificationKinds = map[notify.Kind]model.NotificationKind{
	notify.KindAvailable:  model.NotificationKindAvailable,
	notify.KindNewEpisode: model.NotificationKindNewEpisode,
	notify.KindComingSoon: model.NotificationKindComingSoon,
}

// notificationModels attaches each notification's title and episode; ones
// whose title is no longer stored are left out.
func (r *Resolver) notificationModels(ctx context.Context, stored []notify.Notification) ([]*model.Notification, error) {
	refs := make([]library.MediaRef, 0, len(stored))
	for _, notification := range stored {
		refs = append(refs, notification.Ref())
	}
	titles, err := r.titlesByRef(ctx, refs)
	if err != nil {
		return nil, err
	}
	models := make([]*model.Notification, 0, len(stored))
	for _, notification := range stored {
		title := titles[notification.Ref()]
		if title == nil {
			continue
		}
		episode, err := r.notificationEpisode(ctx, notification)
		if err != nil {
			return nil, err
		}
		models = append(models, notificationModel(notification, title, episode))
	}
	return models, nil
}

func (r *Resolver) notificationEpisode(ctx context.Context, notification notify.Notification) (*model.Episode, error) {
	if notification.EpisodeID == 0 {
		return nil, nil
	}
	episode, err := r.Library.Store.Episode(ctx, notification.EpisodeID)
	if err != nil || episode == nil {
		return nil, err
	}
	return episodeModel(*episode), nil
}

func notificationModel(notification notify.Notification, title *model.Title, episode *model.Episode) *model.Notification {
	return &model.Notification{
		ID:        formatID(kindNotification, notification.ID),
		Kind:      notificationKinds[notification.Kind],
		Heading:   notification.Heading,
		Body:      optional(notification.Body),
		CreatedAt: notification.CreatedAt,
		Read:      notification.ReadAt != "",
		Title:     title,
		Episode:   episode,
	}
}
