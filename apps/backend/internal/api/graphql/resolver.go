package graphql

import (
	"github.com/Letsmoe/otter/internal/accounts"
	"github.com/Letsmoe/otter/internal/downloads"
	"github.com/Letsmoe/otter/internal/library"
	"github.com/Letsmoe/otter/internal/neoworks"
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
	"github.com/Letsmoe/otter/internal/vpn"
)

// Services is everything the API reads from or acts on.
type Services struct {
	Library             *library.Service
	Profiles            *profiles.Service
	Notifications       *notify.Store
	NotificationWatcher *notify.Watcher
	Downloads           *downloads.Service
	Subtitles           *subtitles.Service
	Recommender         *recommend.Engine
	Settings            *settings.Service
	Plugins             *plugins.Registry
	// Accounts are the user's logins for plugins whose site needs one.
	Accounts *accounts.Service
	// PluginStore is nil while plugin updates are turned off.
	PluginStore    *pluginstore.Service
	StreamResolver *resolve.Service
	StreamProxy    *stream.Proxy
	TMDB           *tmdb.Client
	Skips          *skips.Client
	Together       *together.Service
	// VpnStatus reports whether the backend's traffic goes through a VPN.
	VpnStatus func() vpn.Status
	// Neoworks is the household's Neoworks sign-in for sync.
	Neoworks *neoworks.Service
}

// Resolver is the root gqlgen resolver.
type Resolver struct {
	Services
	streamChecks *streamChecks
}

func NewResolver(services Services) *Resolver {
	return &Resolver{Services: services, streamChecks: newStreamChecks()}
}
