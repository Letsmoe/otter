package graphql

import (
	"github.com/neoworks/otter/internal/downloads"
	"github.com/neoworks/otter/internal/library"
	"github.com/neoworks/otter/internal/notify"
	"github.com/neoworks/otter/internal/plugins"
	"github.com/neoworks/otter/internal/profiles"
	"github.com/neoworks/otter/internal/recommend"
	"github.com/neoworks/otter/internal/resolve"
	"github.com/neoworks/otter/internal/settings"
	"github.com/neoworks/otter/internal/skips"
	"github.com/neoworks/otter/internal/stream"
	"github.com/neoworks/otter/internal/tmdb"
	"github.com/neoworks/otter/internal/together"
)

// Services is everything the API reads from or acts on.
type Services struct {
	Library        *library.Service
	Profiles       *profiles.Service
	Notifications  *notify.Store
	Downloads      *downloads.Service
	Recommender    *recommend.Engine
	Settings       *settings.Service
	Plugins        *plugins.Registry
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
}

func NewResolver(services Services) *Resolver {
	return &Resolver{Services: services}
}
