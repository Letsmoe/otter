package graphql

import (
	"context"
	"fmt"
	"strings"

	"github.com/neoworks/otter/internal/api/graphql/model"
	"github.com/neoworks/otter/internal/plugins"
	"github.com/neoworks/otter/internal/together"
)

// togetherSelf is the profile as others in the room see it.
func (r *Resolver) togetherSelf(ctx context.Context, profileID string) (together.Peer, error) {
	id, err := parseProfile(profileID)
	if err != nil {
		return together.Peer{}, err
	}
	profile, err := r.Services.Profiles.Get(ctx, id)
	if err != nil {
		return together.Peer{}, err
	}
	if profile == nil {
		return together.Peer{}, fmt.Errorf("profile %s not found", profileID)
	}
	return together.Peer{Name: profile.Name, Avatar: profile.AvatarURL}, nil
}

func togetherRoomModel(snapshot *together.Snapshot) *model.TogetherRoom {
	if snapshot == nil {
		return nil
	}
	peers := make([]*model.TogetherPeer, 0, len(snapshot.Peers))
	for _, peer := range snapshot.Peers {
		peers = append(peers, togetherPeerModel(peer))
	}
	return &model.TogetherRoom{
		Code:    snapshot.Code,
		Hosting: snapshot.Hosting,
		Status:  model.TogetherStatus(snapshot.Status),
		Self:    togetherPeerModel(snapshot.Self),
		Peers:   peers,
	}
}

func togetherPeerModel(peer together.Peer) *model.TogetherPeer {
	result := &model.TogetherPeer{ID: peer.ID, Name: peer.Name}
	if peer.Avatar != "" {
		result.Avatar = &peer.Avatar
	}
	return result
}

func togetherUpdateModel(update together.Update) *model.TogetherUpdate {
	result := &model.TogetherUpdate{Room: togetherRoomModel(update.Room)}
	if update.Message != nil {
		result.Message = &model.TogetherMessage{
			From:    togetherPeerModel(update.Message.From),
			Payload: update.Message.Payload,
		}
	}
	return result
}

// togetherUpdates converts the service's updates until the subscription ends.
func togetherUpdates(ctx context.Context, source <-chan together.Update) <-chan *model.TogetherUpdate {
	updates := make(chan *model.TogetherUpdate)
	go func() {
		defer close(updates)
		for update := range source {
			select {
			case updates <- togetherUpdateModel(update):
			case <-ctx.Done():
				return
			}
		}
	}()
	return updates
}

// sharedDownload is a link another participant plays; only web links are
// accepted, since resolving fetches it.
func sharedDownload(url string, label *string) (plugins.Download, error) {
	if !strings.HasPrefix(url, "https://") && !strings.HasPrefix(url, "http://") {
		return plugins.Download{}, fmt.Errorf("shared link must be http(s): %q", url)
	}
	download := plugins.Download{URL: url, Label: "Shared link"}
	if label != nil && *label != "" {
		download.Label = *label
	}
	return download, nil
}
