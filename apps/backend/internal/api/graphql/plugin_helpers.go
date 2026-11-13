package graphql

import (
	"context"
	"errors"
	"fmt"
	"log"

	"github.com/Letsmoe/otter/internal/api/graphql/model"
	"github.com/Letsmoe/otter/internal/library"
	"github.com/Letsmoe/otter/internal/plugins"
	"github.com/Letsmoe/otter/internal/pluginstore"
	"github.com/Letsmoe/otter/internal/settings"
)

func (r *Resolver) pluginByName(name string) (*model.Plugin, error) {
	plugin, found := r.Plugins.Get(name)
	if !found {
		return nil, fmt.Errorf("plugin %q is not installed", name)
	}
	values := r.Settings.Cached().PluginSettings[name]
	return r.pluginWithAccount(plugin, values), nil
}

// pluginWithAccount is the plugin's model with its login, for plugins whose site needs one.
func (r *Resolver) pluginWithAccount(plugin *plugins.Plugin, values map[string]string) *model.Plugin {
	pluginModel := pluginModel(plugin, r.Plugins, values)
	if plugin.Meta.Account == nil {
		return pluginModel
	}
	pluginModel.Account = &model.PluginAccount{SignupURL: optional(plugin.Meta.Account.SignupURL)}
	status := r.Accounts.Status(plugin.Meta.Name)
	if status == nil {
		return pluginModel
	}
	pluginModel.Account.Username = optional(status.Username)
	pluginModel.Account.LoggedIn = status.LoggedIn
	pluginModel.Account.Error = optional(status.Error)
	return pluginModel
}

// loginErrorMessage is what the login form shows: the site's own words when it refused the credentials.
func loginErrorMessage(err error) error {
	var pluginError *plugins.PluginError
	if errors.As(err, &pluginError) {
		return errors.New(pluginError.Message)
	}
	return fmt.Errorf("logging in failed: %w", err)
}

// pluginToLogIn is an installed plugin whose site needs an account.
func (r *Resolver) pluginToLogIn(name string) (*plugins.Plugin, error) {
	plugin, found := r.Plugins.Get(name)
	if !found {
		return nil, fmt.Errorf("plugin %q is not installed", name)
	}
	if plugin.Meta.Account == nil {
		return nil, fmt.Errorf("plugin %q needs no account", name)
	}
	return plugin, nil
}

// reloadUpdatedPlugin loads a freshly installed bundle now, so the apps'
// next plugin list shows the new version, and names it by its plugin name.
func (r *Resolver) reloadUpdatedPlugin(update pluginstore.Update) *model.PluginUpdate {
	result := &model.PluginUpdate{Name: update.Package, FromVersion: update.FromVersion, ToVersion: update.ToVersion}
	plugin, err := r.Plugins.ReloadDir(update.Folder)
	if err != nil {
		log.Printf("plugins: reloading %s after an update: %v", update.Folder, err)
		return result
	}
	result.Name = plugin.Meta.Name
	return result
}

// saveDisabledPlugins persists the registry's user-disabled plugins.
func (r *Resolver) saveDisabledPlugins(ctx context.Context) error {
	_, err := r.Settings.Apply(ctx, settings.Update{DisabledPlugins: r.Plugins.DisabledNames()})
	return err
}

// requireVpnIfConfigured blocks streaming when the VPN requirement is on
// and no VPN interface is up.
func (r *Resolver) requireVpnIfConfigured() error {
	if r.Settings.Cached().RequireVpn && !r.VpnStatus().Connected {
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

// findStreamsIfStale looks up streams in the background, then checks the
// untested links. When no lookup ran, or it failed, it announces the final
// update itself so subscribers stop waiting.
func (r *Resolver) findStreamsIfStale(ref library.MediaRef) {
	ctx := context.Background()
	lookedUp, err := r.Library.FindStreamsIfStale(ctx, ref)
	if err != nil {
		log.Printf("streams for %s: %v", mediaID(ref), err)
	}
	if !lookedUp || err != nil {
		r.publishDone(ctx, ref)
	}
	if err == nil {
		r.checkStreams(ref, false)
	}
}

func (r *Resolver) publishDone(ctx context.Context, ref library.MediaRef) {
	streams, err := r.Library.Store.Streams(ctx, ref)
	if err != nil {
		return
	}
	r.Library.StreamUpdates.Publish(ref, library.StreamUpdate{Streams: streams, Done: true})
}
