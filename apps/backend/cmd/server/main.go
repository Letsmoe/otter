// Command server is the whole otter backend in one binary: SQLite in
// DATA_DIR, files on disk, plugins in an embedded JS runtime. The same
// binary runs on a NAS and, cross-compiled, inside the Android app.
package main

import (
	"context"
	"errors"
	"log"
	"net"
	"net/http"
	"os"
	"os/signal"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"syscall"
	"time"

	gqlapi "github.com/neoworks/otter/internal/api/graphql"
	imgapi "github.com/neoworks/otter/internal/api/imgcache"
	"github.com/neoworks/otter/internal/blobcache"
	"github.com/neoworks/otter/internal/cache"
	"github.com/neoworks/otter/internal/db"
	"github.com/neoworks/otter/internal/downloads"
	"github.com/neoworks/otter/internal/hooks"
	"github.com/neoworks/otter/internal/library"
	"github.com/neoworks/otter/internal/netdns"
	"github.com/neoworks/otter/internal/notify"
	"github.com/neoworks/otter/internal/plugins"
	"github.com/neoworks/otter/internal/profiles"
	"github.com/neoworks/otter/internal/recommend"
	"github.com/neoworks/otter/internal/resolve"
	"github.com/neoworks/otter/internal/settings"
	"github.com/neoworks/otter/internal/skips"
	"github.com/neoworks/otter/internal/storage"
	"github.com/neoworks/otter/internal/stream"
	"github.com/neoworks/otter/internal/subtitles"
	"github.com/neoworks/otter/internal/tmdb"
	"github.com/neoworks/otter/internal/together"
	"github.com/neoworks/otter/internal/vpn"
)

// config is everything read from the environment.
type config struct {
	host string
	port string
	// Optional second port on every interface that serves only stream and
	// blob routes to LAN devices (Chromecast fed by the phone app).
	lanStreamPort string
	publicURL     string
	dataDir       string
	pluginDir     string
	hooksDir      string
	// Folder finished downloads are exported to; "" disables exporting.
	downloadsDir string
	tmdbAPIKey   string
	hasSolver    bool
}

func loadConfig() config {
	port := envOr("PORT", "8080")
	dataDir := envOr("DATA_DIR", "./data")
	return config{
		// HOST empty = every interface; the phone app binds to 127.0.0.1.
		host:          os.Getenv("HOST"),
		port:          port,
		lanStreamPort: os.Getenv("LAN_STREAM_PORT"),
		// Externally reachable origin; stream-proxy and blob URLs are built from it.
		publicURL:    envOr("PUBLIC_URL", "http://localhost:"+port),
		dataDir:      dataDir,
		pluginDir:    envOr("PLUGIN_DIR", filepath.Join(dataDir, "plugins")),
		hooksDir:     envOr("HOOKS_DIR", filepath.Join(dataDir, "hooks")),
		downloadsDir: downloadsDir(dataDir),
		tmdbAPIKey:   os.Getenv("TMDB_API_KEY"),
		// Plugins inherit the environment, FLARESOLVERR_URL included.
		hasSolver: strings.TrimSpace(os.Getenv("FLARESOLVERR_URL")) != "",
	}
}

// downloadsDir is off on Android, where no file manager can be opened and
// joined HLS files would double the space downloads take.
func downloadsDir(dataDir string) string {
	if runtime.GOOS == "android" {
		return os.Getenv("DOWNLOADS_DIR")
	}
	return envOr("DOWNLOADS_DIR", filepath.Join(dataDir, "downloads"))
}

func envOr(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}

func main() {
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	cfg := loadConfig()
	// Hosts without a system resolver Go can find (Android) pass their DNS
	// servers, as a list or a file kept current by the app.
	netdns.Configure(os.Getenv("DNS_SERVERS"), os.Getenv("DNS_SERVERS_FILE"))
	netdns.ConfigureAndroidVersion()

	database, err := db.Open(ctx, filepath.Join(cfg.dataDir, "otter.db"))
	if err != nil {
		log.Fatalf("db: %v", err)
	}
	defer database.Close()

	handlers, err := buildServer(ctx, cfg, database)
	if err != nil {
		log.Fatal(err)
	}
	if cfg.lanStreamPort != "" {
		go serveLanStreams(ctx, net.JoinHostPort("", cfg.lanStreamPort), handlers.lanStreams)
	}
	serve(ctx, net.JoinHostPort(cfg.host, cfg.port), handlers.api)
}

// serverHandlers are the full API and the stream-only subset for LAN devices.
type serverHandlers struct {
	api        http.Handler
	lanStreams http.Handler
}

// buildServer wires every service and returns the HTTP handlers.
func buildServer(ctx context.Context, cfg config, database *db.DB) (serverHandlers, error) {
	settingsService := settings.NewService(database)
	if err := settingsService.Init(ctx, cfg.tmdbAPIKey); err != nil {
		return serverHandlers{}, err
	}
	cacheClient := cache.New(ctx, database)
	store, err := storage.New(filepath.Join(cfg.dataDir, "blobs"), cfg.publicURL)
	if err != nil {
		return serverHandlers{}, err
	}

	registry, runner, err := loadPlugins(cfg, settingsService)
	if err != nil {
		return serverHandlers{}, err
	}
	hookRunner := hooks.New(cfg.hooksDir, []string{"OTTER_URL=" + cfg.publicURL})
	tmdbClient := tmdb.NewClient(settingsService.TMDB)
	// Find out early whether the key works, so the apps can ask for a new one.
	go tmdbClient.VerifyKey(ctx)
	streamsAllowed := vpnGate(settingsService)
	libraryService := library.NewService(library.NewStore(database), tmdbClient, registry, runner, hookRunner, streamsAllowed)
	if err := libraryService.ExpireStreamsIfPluginsChanged(ctx, cacheClient); err != nil {
		log.Printf("library: expiring streams after a plugin change: %v", err)
	}

	streamCache := stream.NewCacheService(cacheClient, store)
	streamCache.SetUpstreamGate(streamsAllowed)
	streamResolver := resolve.New(registry, runner)
	streamResolver.SetCache(streamCache)
	streamProxy := stream.NewProxy(streamCache, cacheClient, cfg.publicURL)
	imageCache := imgapi.New(store)
	// Downloads pin their blobs, so they register before eviction starts.
	downloadService := downloads.NewService(downloads.NewStore(database), libraryService, streamResolver, streamCache)
	streamCache.SetPinnedURLs(downloadService.Store.PinnedURLs)
	if cfg.downloadsDir != "" {
		downloadService.SetExporter(downloads.NewExporter(cfg.downloadsDir, store, libraryService.Store))
	}
	startCacheEviction(ctx, store, streamCache)
	subtitleService := subtitles.NewService(subtitles.NewStore(database), registry, runner, libraryService,
		cacheClient, store, streamsAllowed)
	notificationStore := notify.NewStore(database)
	go downloadService.Run(ctx)
	go notify.NewWatcher(notificationStore, database, libraryService).Run(ctx)

	resolver := gqlapi.NewResolver(gqlapi.Services{
		Library:        libraryService,
		Profiles:       profiles.NewService(database, cacheClient, hookRunner),
		Notifications:  notificationStore,
		Downloads:      downloadService,
		Subtitles:      subtitleService,
		Recommender:    recommend.New(database),
		Settings:       settingsService,
		Plugins:        registry,
		StreamResolver: streamResolver,
		StreamProxy:    streamProxy,
		TMDB:           tmdbClient,
		Skips:          skips.New(cacheClient),
		Together:       togetherService(),
		VpnConnected:   vpnConnected,
	})
	graphqlHandler, err := gqlapi.NewHandler(resolver)
	if err != nil {
		return serverHandlers{}, err
	}

	mux := http.NewServeMux()
	mux.Handle("/graphql", graphqlHandler)
	mux.Handle("/api/img", imageCache)
	mux.Handle("/api/stream/", streamProxy)
	mux.Handle(storage.RoutePrefix, store.Handler())
	mux.Handle(subtitles.RoutePrefix, subtitleService.Handler())
	mux.HandleFunc("/health", func(writer http.ResponseWriter, request *http.Request) {
		writer.WriteHeader(http.StatusOK)
	})

	lanMux := http.NewServeMux()
	lanMux.Handle("/api/stream/", streamProxy)
	lanMux.Handle(storage.RoutePrefix, store.Handler())
	lanMux.Handle(subtitles.RoutePrefix, subtitleService.Handler())
	return serverHandlers{api: corsMiddleware(mux), lanStreams: privateNetworkOnly(dlnaHeaders(corsMiddleware(lanMux)))}, nil
}

// loadPlugins loads the plugin bundles (scripts/bundle-plugins.ts) into the
// embedded JS runtime, hot-reloading them on change.
func loadPlugins(cfg config, settingsService *settings.Service) (*plugins.Registry, *plugins.Runner, error) {
	if err := os.MkdirAll(cfg.pluginDir, 0o755); err != nil {
		return nil, nil, err
	}
	runner := plugins.NewEmbeddedRunner()
	runner.SetSettingsProvider(settingsService)
	registry, err := plugins.NewRegistry(cfg.pluginDir, runner, plugins.WithSolver(cfg.hasSolver))
	if err != nil {
		return nil, nil, err
	}
	registry.RestoreDisabled(settingsService.Cached().DisabledPlugins)
	return registry, runner, nil
}

// togetherService meets peers on TOGETHER_RELAYS (comma-separated wss:// URLs)
// or the public default relays.
func togetherService() *together.Service {
	relays := together.DefaultRelays
	if configured := os.Getenv("TOGETHER_RELAYS"); configured != "" {
		relays = strings.Split(configured, ",")
	}
	return together.NewService(together.NostrSignaling{Relays: relays}, together.DefaultICEServers)
}

func vpnConnected() bool {
	connected, _ := vpn.IsConnected()
	return connected
}

// vpnGate refuses stream lookups while the VPN requirement is on and no VPN
// interface is up.
func vpnGate(settingsService *settings.Service) func() error {
	return func() error {
		if settingsService.Cached().RequireVpn && !vpnConnected() {
			return errVpnRequired
		}
		return nil
	}
}

var errVpnRequired = errors.New("VPN required: connect a VPN before streaming")

// startCacheEviction keeps the stream and image caches under CACHE_MAX_BYTES,
// least recently used first.
func startCacheEviction(ctx context.Context, store *storage.Client, streamCache *stream.CacheService) {
	evictor := blobcache.New(store, cacheMaxBytes(), streamCache.EvictionSource(), imgapi.EvictionSource(), subtitles.EvictionSource())
	store.OnPut(evictor.NoteWrite)
	go func() {
		if removed, err := streamCache.Reconcile(ctx); err != nil {
			log.Printf("stream cache reconcile: %v", err)
		} else if removed > 0 {
			log.Printf("stream cache reconcile: dropped %d entries for missing blobs", removed)
		}
		evictor.Run(ctx)
	}()
}

// cacheMaxBytes returns CACHE_MAX_BYTES when it is a positive byte count,
// else blobcache.DefaultMaxBytes.
func cacheMaxBytes() int64 {
	configured := os.Getenv("CACHE_MAX_BYTES")
	if configured == "" {
		return blobcache.DefaultMaxBytes
	}
	maxBytes, err := strconv.ParseInt(configured, 10, 64)
	if err != nil || maxBytes <= 0 {
		log.Printf("CACHE_MAX_BYTES %q is not a positive byte count, using %d", configured, blobcache.DefaultMaxBytes)
		return blobcache.DefaultMaxBytes
	}
	return maxBytes
}

func corsMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		writer.Header().Set("Access-Control-Allow-Origin", "*")
		writer.Header().Set("Access-Control-Allow-Methods", "POST, GET, OPTIONS")
		writer.Header().Set("Access-Control-Allow-Headers", "Content-Type")
		if request.Method == http.MethodOptions {
			writer.WriteHeader(http.StatusNoContent)
			return
		}
		next.ServeHTTP(writer, request)
	})
}

// serve runs the HTTP server until ctx ends, then shuts it down gracefully.
func serve(ctx context.Context, address string, handler http.Handler) {
	if err := listenUntilDone(ctx, address, handler); err != nil {
		log.Fatal(err)
	}
}

// serveLanStreams is best effort: without it only casting is unavailable.
func serveLanStreams(ctx context.Context, address string, handler http.Handler) {
	if err := listenUntilDone(ctx, address, handler); err != nil {
		log.Printf("LAN stream listener: %v", err)
	}
}

func listenUntilDone(ctx context.Context, address string, handler http.Handler) error {
	server := &http.Server{Addr: address, Handler: handler}
	go func() {
		<-ctx.Done()
		shutdownContext, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		_ = server.Shutdown(shutdownContext)
	}()
	log.Printf("listening on %s", address)
	if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		return err
	}
	return nil
}
