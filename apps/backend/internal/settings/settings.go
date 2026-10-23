// Package settings stores the app settings as one JSON document in the
// setting table and keeps an in-memory copy for hot paths.
package settings

import (
	"context"
	"encoding/json"
	"fmt"
	"maps"
	"slices"
	"sync"

	"github.com/neoworks/otter/internal/db"
)

const settingsKey = "app"

const defaultLanguage = "en-US"

// DefaultTMDBApiKey is the built-in TMDB key, used while no own key is saved.
// Builds can replace it with
// -ldflags "-X github.com/neoworks/otter/internal/settings.DefaultTMDBApiKey=…".
var DefaultTMDBApiKey = ""

type Settings struct {
	// TMDBApiKey is the user's own key; empty uses DefaultTMDBApiKey.
	TMDBApiKey   string `json:"tmdbApiKey"`
	TMDBLanguage string `json:"tmdbLanguage"`
	// TMDBKeyPromptSeen is set once the first-run key dialog was answered.
	TMDBKeyPromptSeen bool `json:"tmdbKeyPromptSeen"`
	// RequireVpn blocks stream lookups and playback while no VPN interface is up.
	RequireVpn      bool                         `json:"requireVpn"`
	DisabledPlugins []string                     `json:"disabledPlugins"`
	PluginSettings  map[string]map[string]string `json:"pluginSettings"`
}

// Update is a partial change: nil fields stay as they are.
type Update struct {
	TMDBApiKey        *string
	TMDBLanguage      *string
	TMDBKeyPromptSeen *bool
	RequireVpn        *bool
	DisabledPlugins   []string
	// PluginSettings replaces the settings of the plugins it names.
	PluginSettings map[string]map[string]string
}

type Service struct {
	database *db.DB
	mutex    sync.RWMutex
	current  Settings
}

func NewService(database *db.DB) *Service {
	return &Service{database: database}
}

// Init loads the stored settings, falling back to defaults when none exist.
// TMDB_API_KEY in the environment seeds the key on a fresh install.
func (s *Service) Init(ctx context.Context, environmentAPIKey string) error {
	loaded, err := s.load(ctx)
	if err != nil {
		return err
	}
	if loaded.TMDBApiKey == "" {
		loaded.TMDBApiKey = environmentAPIKey
	}
	s.store(loaded)
	return nil
}

// Cached returns a copy of the current settings.
func (s *Service) Cached() Settings {
	s.mutex.RLock()
	defer s.mutex.RUnlock()
	return cloneSettings(s.current)
}

// TMDB returns the TMDB API key in use (the own one, else the built-in one)
// and the language.
func (s *Service) TMDB() (apiKey string, language string) {
	current := s.Cached()
	if current.TMDBApiKey == "" {
		return DefaultTMDBApiKey, current.TMDBLanguage
	}
	return current.TMDBApiKey, current.TMDBLanguage
}

// UsesDefaultTMDBKey reports whether the built-in key is in use.
func (s *Service) UsesDefaultTMDBKey() bool {
	return s.Cached().TMDBApiKey == "" && DefaultTMDBApiKey != ""
}

// PluginEnv returns the per-plugin settings as KEY=value env entries.
func (s *Service) PluginEnv(pluginName string) []string {
	current := s.Cached()
	values := current.PluginSettings[pluginName]
	environment := make([]string, 0, len(values))
	for _, key := range slices.Sorted(maps.Keys(values)) {
		environment = append(environment, key+"="+values[key])
	}
	return environment
}

// Apply merges an update, persists it and returns the new settings.
func (s *Service) Apply(ctx context.Context, update Update) (Settings, error) {
	next := s.Cached()
	applyUpdate(&next, update)
	encoded, err := json.Marshal(next)
	if err != nil {
		return Settings{}, err
	}
	_, err = s.database.Exec(ctx,
		`INSERT INTO setting (key, value) VALUES ($key, $value)
		 ON CONFLICT (key) DO UPDATE SET value = excluded.value`,
		db.Vars{"key": settingsKey, "value": string(encoded)})
	if err != nil {
		return Settings{}, fmt.Errorf("save settings: %w", err)
	}
	s.store(next)
	return cloneSettings(next), nil
}

func applyUpdate(settings *Settings, update Update) {
	if update.TMDBApiKey != nil {
		settings.TMDBApiKey = *update.TMDBApiKey
	}
	if update.TMDBLanguage != nil {
		settings.TMDBLanguage = *update.TMDBLanguage
	}
	if update.TMDBKeyPromptSeen != nil {
		settings.TMDBKeyPromptSeen = *update.TMDBKeyPromptSeen
	}
	if update.RequireVpn != nil {
		settings.RequireVpn = *update.RequireVpn
	}
	if update.DisabledPlugins != nil {
		settings.DisabledPlugins = update.DisabledPlugins
	}
	for pluginName, values := range update.PluginSettings {
		settings.PluginSettings[pluginName] = values
	}
}

func (s *Service) load(ctx context.Context) (Settings, error) {
	loaded := Settings{TMDBLanguage: defaultLanguage}
	row, err := s.database.QueryRow(ctx, "SELECT value FROM setting WHERE key = $key", db.Vars{"key": settingsKey})
	if err != nil {
		return loaded, fmt.Errorf("load settings: %w", err)
	}
	if row != nil {
		if err := db.DecodeRow(row["value"].(map[string]any), &loaded); err != nil {
			return loaded, fmt.Errorf("decode settings: %w", err)
		}
	}
	if loaded.PluginSettings == nil {
		loaded.PluginSettings = map[string]map[string]string{}
	}
	if loaded.TMDBLanguage == "" {
		loaded.TMDBLanguage = defaultLanguage
	}
	return loaded, nil
}

func (s *Service) store(settings Settings) {
	s.mutex.Lock()
	defer s.mutex.Unlock()
	s.current = settings
}

func cloneSettings(settings Settings) Settings {
	settings.DisabledPlugins = slices.Clone(settings.DisabledPlugins)
	pluginSettings := make(map[string]map[string]string, len(settings.PluginSettings))
	for pluginName, values := range settings.PluginSettings {
		pluginSettings[pluginName] = maps.Clone(values)
	}
	settings.PluginSettings = pluginSettings
	return settings
}
