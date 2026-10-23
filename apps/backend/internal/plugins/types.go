package plugins

import (
	"net/url"
	"strings"
)

type Capability string

// Otter plugins do exactly two things: find stream links for a title TMDB
// already identified, and turn a hoster link into a playable media URL.
const (
	CapabilityStreams Capability = "streams"
	CapabilityResolve Capability = "resolve"
)

type MediaType string

const (
	MediaTypeMovie   MediaType = "movie"
	MediaTypeEpisode MediaType = "episode"
)

// PluginSettingField describes one configurable value a plugin needs.
// The value is injected into the plugin as an env var named by Key.
type PluginSettingField struct {
	Key         string `json:"key"`
	Label       string `json:"label"`
	Description string `json:"description,omitempty"`
	Type        string `json:"type"` // "string" | "password" | "boolean" | "number"
	Required    bool   `json:"required,omitempty"`
	Default     string `json:"default,omitempty"`
}

// PluginMeta is returned by a plugin when invoked with capability "meta".
type PluginMeta struct {
	Name         string       `json:"name"`
	DisplayName  string       `json:"display_name,omitempty"`
	Description  string       `json:"description,omitempty"`
	Icon         string       `json:"icon,omitempty"` // emoji or URL
	Version      string       `json:"version"`
	Capabilities []Capability `json:"capabilities"`
	// Domains lists provider hostnames this plugin can resolve (resolve capability only).
	// Use "*" to act as a wildcard fallback for any domain.
	Domains  []string             `json:"domains,omitempty"`
	Settings []PluginSettingField `json:"settings,omitempty"`
	// RequiresSolver marks plugins whose site sits behind a Cloudflare
	// challenge and only works through FlareSolverr.
	RequiresSolver bool `json:"requires_solver,omitempty"`
	// BaseURL is the site the plugin scrapes. The user can point it at a
	// mirror through the generated "Domain" setting (see SettingFields).
	BaseURL   string   `json:"base_url,omitempty"`
	Languages []string `json:"languages,omitempty"`
	// Media lists what the plugin finds streams for: "movie" and/or "episode".
	Media []MediaType `json:"media,omitempty"`
}

// BaseURLSettingKey is the env var through which a plugin receives the
// user's override of its BaseURL.
const BaseURLSettingKey = "OTTER_BASE_URL"

// SettingFields returns the settings a user can edit for this plugin: the ones
// it declares, preceded by the "Domain" field every plugin with a BaseURL gets.
func (m PluginMeta) SettingFields() []PluginSettingField {
	if m.BaseURL == "" {
		return m.Settings
	}
	domain := PluginSettingField{
		Key:         BaseURLSettingKey,
		Label:       "Domain",
		Description: "Address of the site. Change it when the site moves to a new domain.",
		Type:        "string",
		Default:     m.BaseURL,
	}
	return append([]PluginSettingField{domain}, m.Settings...)
}

// WithoutDefaultBaseURL drops the Domain value when it just repeats the
// plugin's own BaseURL, so a later change of the default still reaches users
// who never customised it.
func (m PluginMeta) WithoutDefaultBaseURL(values map[string]string) map[string]string {
	cleaned := make(map[string]string, len(values))
	for key, value := range values {
		cleaned[key] = value
	}
	override := cleaned[BaseURLSettingKey]
	if strings.TrimSpace(override) == "" || sameAddress(override, m.BaseURL) {
		delete(cleaned, BaseURLSettingKey)
	}
	return cleaned
}

func sameAddress(left, right string) bool {
	return strings.TrimRight(strings.TrimSpace(left), "/") == strings.TrimRight(strings.TrimSpace(right), "/")
}

func (m PluginMeta) Has(c Capability) bool {
	for _, capability := range m.Capabilities {
		if capability == c {
			return true
		}
	}
	return false
}

// Plugin is a loaded plugin bundle.
type Plugin struct {
	Meta       PluginMeta
	Dir        string // absolute path to plugin directory (contains package.json)
	EntryPoint string // relative entry file resolved from package.json "main"
	Package    string // npm package name from package.json
	LocalBuild bool   // bundled from this checkout by the dev bundler, not installed from npm
}

// ServesURL reports whether rawURL's host is one of the plugin's meta.domains
// (or a subdomain of one). The "*" wildcard does not count.
func (p *Plugin) ServesURL(rawURL string) bool {
	parsed, err := url.Parse(rawURL)
	if err != nil || parsed.Hostname() == "" {
		return false
	}
	host := strings.TrimPrefix(strings.ToLower(parsed.Hostname()), "www.")
	for _, domain := range p.Meta.Domains {
		if domain == "*" {
			continue
		}
		if host == domain || strings.HasSuffix(host, "."+domain) {
			return true
		}
	}
	return false
}

// ResolveArgs is passed to a plugin's resolve capability.
type ResolveArgs struct {
	URL string `json:"url"`
}

// ResolveResult is returned by a plugin's resolve capability.
type ResolveResult struct {
	URL        string            `json:"url"`
	MimeType   string            `json:"mime_type"`
	Headers    map[string]string `json:"headers,omitempty"`
	Quality    string            `json:"quality,omitempty"`
	LoadTimeMs int64             `json:"load_time_ms,omitempty"`
	SpeedBps   float64           `json:"speed_bps,omitempty"`
}

// StreamsArgs is passed to a plugin's streams capability. It identifies a media
// item already known from TMDB; the plugin locates it on its source site and
// returns playable stream links. Season/episode numbers are set for episodes.
type StreamsArgs struct {
	MediaType     MediaType `json:"media_type"`
	Title         string    `json:"title"`
	OriginalTitle string    `json:"original_title,omitempty"`
	Year          int       `json:"year,omitempty"`
	TMDBID        int       `json:"tmdb_id,omitempty"`
	IMDBID        string    `json:"imdb_id,omitempty"`
	SeasonNumber  int       `json:"season_number,omitempty"`
	EpisodeNumber int       `json:"episode_number,omitempty"`
}

// StreamsResult is returned by a plugin's streams capability.
type StreamsResult struct {
	Downloads []Download `json:"downloads"`
}

// Download is one playable source: usually a hoster page that still needs
// resolving, sometimes a direct media URL.
type Download struct {
	Label     string  `json:"label"`
	URL       string  `json:"url"`
	Quality   string  `json:"quality,omitempty"`
	Language  string  `json:"language,omitempty"`
	Format    string  `json:"format,omitempty"`
	SizeBytes int64   `json:"size_bytes,omitempty"`
	SpeedBps  float64 `json:"speed_bps,omitempty"`
}
