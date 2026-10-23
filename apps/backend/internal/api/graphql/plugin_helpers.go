package graphql

import (
	"context"
	"fmt"
	"log"

	"github.com/neoworks/otter/internal/api/graphql/model"
	"github.com/neoworks/otter/internal/library"
	"github.com/neoworks/otter/internal/settings"
)

func (r *Resolver) pluginByName(name string) (*model.Plugin, error) {
	plugin, found := r.Plugins.Get(name)
	if !found {
		return nil, fmt.Errorf("plugin %q is not installed", name)
	}
	values := r.Settings.Cached().PluginSettings[name]
	return pluginModel(plugin, r.Plugins, values), nil
}

// saveDisabledPlugins persists the registry's user-disabled plugins.
func (r *Resolver) saveDisabledPlugins(ctx context.Context) error {
	_, err := r.Settings.Apply(ctx, settings.Update{DisabledPlugins: r.Plugins.DisabledNames()})
	return err
}

// requireVpnIfConfigured blocks streaming when the VPN requirement is on
// and no VPN interface is up.
func (r *Resolver) requireVpnIfConfigured() error {
	if r.Settings.Cached().RequireVpn && !r.VpnConnected() {
		return fmt.Errorf("VPN required: connect a VPN before streaming")
	}
	return nil
}

// streamsOf lists the stored streams of a movie or episode.
func (r *Resolver) streamsOf(ctx context.Context, ref library.MediaRef) ([]*model.Stream, error) {
	streams, err := r.Library.Store.Streams(ctx, ref)
	if err != nil {
		return nil, err
	}
	return streamModels(streams), nil
}

// subscribeStreams sends the stored streams, then every update until ctx
// ends. A stale stream list triggers a lookup.
func (r *Resolver) subscribeStreams(ctx context.Context, ref library.MediaRef) (<-chan *model.StreamUpdate, error) {
	current, err := r.Library.Store.Streams(ctx, ref)
	if err != nil {
		return nil, err
	}
	updates := r.Library.StreamUpdates.Subscribe(ctx, ref)
	output := make(chan *model.StreamUpdate, 1)
	output <- &model.StreamUpdate{Streams: streamModels(current), Done: false}
	go func() {
		defer close(output)
		for update := range updates {
			select {
			case output <- &model.StreamUpdate{Streams: streamModels(update.Streams), Done: update.Done}:
			case <-ctx.Done():
				return
			}
		}
	}()
	go r.findStreamsIfStale(ref)
	return output, nil
}

// findStreamsIfStale looks up streams in the background. When no lookup
// ran, or it failed, it announces the final update itself so subscribers
// stop waiting.
func (r *Resolver) findStreamsIfStale(ref library.MediaRef) {
	ctx := context.Background()
	lookedUp, err := r.Library.FindStreamsIfStale(ctx, ref)
	if err != nil {
		log.Printf("streams for %s: %v", mediaID(ref), err)
	}
	if !lookedUp || err != nil {
		r.publishDone(ctx, ref)
	}
}

func (r *Resolver) publishDone(ctx context.Context, ref library.MediaRef) {
	streams, err := r.Library.Store.Streams(ctx, ref)
	if err != nil {
		return
	}
	r.Library.StreamUpdates.Publish(ref, library.StreamUpdate{Streams: streams, Done: true})
}
