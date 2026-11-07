package graphql

import (
	"github.com/Letsmoe/otter/internal/downloads"
	"github.com/Letsmoe/otter/internal/library"
	"github.com/Letsmoe/otter/internal/notify"
	"github.com/Letsmoe/otter/internal/plugins"
	"github.com/Letsmoe/otter/internal/pluginstore"
	"github.com/Letsmoe/otter/internal/profiles"
	"github.com/Letsmoe/otter/internal/recommend"
	"github.com/Letsmoe/otter/internal/resolve"
	"github.com/Letsmoe/otter/internal/settings"
	"github.com/Letsmoe/otter/internal/skips"
	"github.com/Letsmoe/otter/internal/stream"
	"github.com/Letsmoe/otter/internal/subtitles"
	"github.com/Letsmoe/otter/internal/tmdb"
	"github.com/Letsmoe/otter/internal/together"
)

// Services is everything the API reads from or acts on.
type Services struct {
	Library       *library.Service
	Profiles      *profiles.Service
	Notifications *notify.Store
	Downloads     *downloads.Service
	Subtitles     *subtitles.Service
	Recommender   *recommend.Engine
	Settings      *settings.Service
	Plugins       *plugins.Registry
	// PluginStore is nil while plugin updates are turned off.
	PluginStore    *pluginstore.Service
	StreamResolver *resolve.Service
	StreamProxy    *stream.Proxy
	TMDB           *tmdb.Client
	Skips          *skips.Client
	Together       *together.Service
	// VpnConnected reports whether a VPN interface is up.
	VpnConnected func() bool
}

// Resolver is the root gqlgen resolver.
type Resolver struct {
	Services
	streamChecks *streamChecks
}

func NewResolver(services Services) *Resolver {
	return &Resolver{Services: services, streamChecks: newStreamChecks()}
}
