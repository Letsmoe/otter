package plugins

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"sync"
	"time"

	"github.com/fsnotify/fsnotify"
)

type packageJSON struct {
	Name       string `json:"name"`
	Main       string `json:"main"`
	LocalBuild bool   `json:"localBuild"`
}

// Registry holds all loaded plugins and hot-reloads on directory changes.
type Registry struct {
	mu          sync.RWMutex
	plugins     map[string]*Plugin // keyed by plugin name
	disabled    map[string]bool    // keyed by plugin name
	dir         string
	runner      *Runner
	watcher     *fsnotify.Watcher
	onLoad      func(*Plugin)
	iconFetcher func(ctx context.Context, name, url string) string
	// hasSolver reports whether FlareSolverr is configured; plugins with
	// Meta.RequiresSolver are unavailable without it.
	hasSolver bool
}

// Option configures a Registry.
type Option func(*Registry)

// WithOnLoad sets a callback invoked every time a plugin is (re-)loaded.
func WithOnLoad(fn func(*Plugin)) Option {
	return func(r *Registry) { r.onLoad = fn }
}

// WithSolver tells the registry whether a FlareSolverr endpoint is configured.
func WithSolver(configured bool) Option {
	return func(r *Registry) { r.hasSolver = configured }
}

// WithIconFetcher sets a function called on plugin load to cache/proxy icon URLs.
// It receives the plugin name and raw icon URL, and returns the URL to store.
func WithIconFetcher(fn func(ctx context.Context, name, url string) string) Option {
	return func(r *Registry) { r.iconFetcher = fn }
}

func NewRegistry(dir string, runner *Runner, opts ...Option) (*Registry, error) {
	watcher, err := fsnotify.NewWatcher()
	if err != nil {
		return nil, err
	}

	r := &Registry{
		plugins:  make(map[string]*Plugin),
		disabled: make(map[string]bool),
		dir:      dir,
		runner:   runner,
		watcher:  watcher,
	}
	for _, o := range opts {
		o(r)
	}

	if err := r.loadAll(); err != nil {
		return nil, err
	}

	if err := watcher.Add(dir); err != nil {
		return nil, err
	}

	go r.watch()
	return r, nil
}

func (r *Registry) Get(name string) (*Plugin, bool) {
	r.mu.RLock()
	defer r.mu.RUnlock()
	p, ok := r.plugins[name]
	return p, ok
}

func (r *Registry) IsEnabled(name string) bool {
	r.mu.RLock()
	defer r.mu.RUnlock()
	return !r.disabled[name]
}

// IsAvailable reports whether the plugin can run in this deployment: it
// either doesn't need FlareSolverr or one is configured.
func (r *Registry) IsAvailable(name string) bool {
	r.mu.RLock()
	defer r.mu.RUnlock()
	plugin, ok := r.plugins[name]
	return ok && r.available(plugin)
}

// IsActive reports whether the plugin is both enabled and available.
func (r *Registry) IsActive(name string) bool {
	return r.IsEnabled(name) && r.IsAvailable(name)
}

// available reports whether plugin's requirements are met. Callers hold r.mu.
func (r *Registry) available(plugin *Plugin) bool {
	return !plugin.Meta.RequiresSolver || r.hasSolver
}

// SetEnabled enables or disables a plugin by name.
func (r *Registry) SetEnabled(name string, enabled bool) {
	r.mu.Lock()
	defer r.mu.Unlock()
	if enabled {
		delete(r.disabled, name)
	} else {
		r.disabled[name] = true
	}
}

// RestoreDisabled bulk-sets the disabled list (called on startup from persisted settings).
func (r *Registry) RestoreDisabled(names []string) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.disabled = make(map[string]bool, len(names))
	for _, n := range names {
		r.disabled[n] = true
	}
}

// DisabledNames returns the current list of disabled plugin names.
func (r *Registry) DisabledNames() []string {
	r.mu.RLock()
	defer r.mu.RUnlock()
	out := make([]string, 0, len(r.disabled))
	for name := range r.disabled {
		out = append(out, name)
	}
	return out
}

// InactiveNames returns the plugins whose content should be hidden: the
// user-disabled ones plus loaded plugins that are unavailable here.
func (r *Registry) InactiveNames() []string {
	r.mu.RLock()
	defer r.mu.RUnlock()
	out := make([]string, 0, len(r.disabled))
	for name := range r.disabled {
		out = append(out, name)
	}
	for name, plugin := range r.plugins {
		if !r.disabled[name] && !r.available(plugin) {
			out = append(out, name)
		}
	}
	return out
}

func (r *Registry) All() []*Plugin {
	r.mu.RLock()
	defer r.mu.RUnlock()
	out := make([]*Plugin, 0, len(r.plugins))
	for _, p := range r.plugins {
		out = append(out, p)
	}
	return out
}

// WithCapability returns all active plugins that support the given capability.
func (r *Registry) WithCapability(cap Capability) []*Plugin {
	return r.WithAnyCapability(cap)
}

// WithAnyCapability returns all active (enabled and available) plugins that
// support at least one of the given capabilities.
func (r *Registry) WithAnyCapability(caps ...Capability) []*Plugin {
	r.mu.RLock()
	defer r.mu.RUnlock()
	var out []*Plugin
	for _, p := range r.plugins {
		if r.disabled[p.Meta.Name] || !r.available(p) {
			continue
		}
		for _, cap := range caps {
			if p.Meta.Has(cap) {
				out = append(out, p)
				break
			}
		}
	}
	return out
}

func (r *Registry) loadAll() error {
	entries, err := os.ReadDir(r.dir)
	if err != nil {
		if os.IsNotExist(err) {
			return nil
		}
		return err
	}

	for _, e := range entries {
		if !e.IsDir() {
			continue
		}
		pluginDir := filepath.Join(r.dir, e.Name())
		if err := r.loadDir(pluginDir); err != nil {
			log.Printf("plugin load error %s: %v", e.Name(), err)
		}
	}
	return nil
}

func (r *Registry) loadDir(dir string) error {
	pkgPath := filepath.Join(dir, "package.json")
	data, err := os.ReadFile(pkgPath)
	if err != nil {
		if os.IsNotExist(err) {
			return nil // not a plugin directory
		}
		return err
	}

	var pkg packageJSON
	if err := json.Unmarshal(data, &pkg); err != nil {
		return fmt.Errorf("parse package.json: %w", err)
	}
	if pkg.Main == "" {
		return nil // library package, not a plugin
	}

	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()

	// Install dependencies if node_modules is absent or package.json changed.
	if err := r.runner.install(ctx, dir); err != nil {
		log.Printf("plugin %s: bun install warning: %v", filepath.Base(dir), err)
		// Non-fatal — plugin may still work if deps already installed.
	}

	meta, err := r.runner.loadMeta(ctx, dir, pkg.Main)
	if err != nil {
		return fmt.Errorf("load meta: %w", err)
	}
	if meta.Name == "" {
		return fmt.Errorf("meta.name is empty")
	}

	if meta.Icon != "" && r.iconFetcher != nil {
		if cached := r.iconFetcher(ctx, meta.Name, meta.Icon); cached != "" {
			meta.Icon = cached
		}
	}

	p := &Plugin{Meta: meta, Dir: dir, EntryPoint: pkg.Main, Package: pkg.Name, LocalBuild: pkg.LocalBuild}

	r.mu.Lock()
	r.plugins[meta.Name] = p
	r.mu.Unlock()

	// Also watch the plugin directory for file changes.
	_ = r.watcher.Add(dir)

	log.Printf("loaded plugin: %s v%s caps=%v", meta.Name, meta.Version, meta.Capabilities)

	if r.onLoad != nil {
		r.onLoad(p)
	}
	return nil
}

func (r *Registry) unloadDir(dir string) {
	r.mu.Lock()
	defer r.mu.Unlock()
	for name, p := range r.plugins {
		if p.Dir == dir {
			delete(r.plugins, name)
			log.Printf("unloaded plugin: %s", name)
			return
		}
	}
}

func (r *Registry) watch() {
	for {
		select {
		case event, ok := <-r.watcher.Events:
			if !ok {
				return
			}
			r.handleEvent(event)
		case err, ok := <-r.watcher.Errors:
			if !ok {
				return
			}
			log.Printf("plugin watcher error: %v", err)
		}
	}
}

func (r *Registry) handleEvent(event fsnotify.Event) {
	name := filepath.Base(event.Name)

	// Ignore hidden files, lock files, and node_modules changes.
	if name[0] == '.' || name == "bun.lock" || name == "package-lock.json" {
		return
	}

	info, err := os.Stat(event.Name)
	isDir := err == nil && info.IsDir()

	switch {
	case event.Has(fsnotify.Create) && isDir:
		// New plugin directory dropped in
		if err := r.loadDir(event.Name); err != nil {
			log.Printf("plugin load error %s: %v", name, err)
		}

	case (event.Has(fsnotify.Write) || event.Has(fsnotify.Create)) && !isDir:
		// File changed inside a plugin dir — reload the parent plugin
		parent := filepath.Dir(event.Name)
		if filepath.Clean(parent) == filepath.Clean(r.dir) {
			return // top-level file, not a plugin
		}
		// Walk up to find the plugin root (immediate child of r.dir)
		pluginDir := pluginRoot(event.Name, r.dir)
		if pluginDir == "" {
			return
		}
		if err := r.loadDir(pluginDir); err != nil {
			log.Printf("plugin reload error %s: %v", filepath.Base(pluginDir), err)
		}

	case event.Has(fsnotify.Remove) || event.Has(fsnotify.Rename):
		// The path is gone, so it can't be stat'ed; unloadDir ignores paths
		// that aren't a plugin folder.
		r.unloadDir(event.Name)
	}
}

// pluginRoot returns the immediate child of baseDir that contains path,
// or "" if path is not under baseDir or is baseDir itself.
func pluginRoot(path, baseDir string) string {
	base := filepath.Clean(baseDir)
	p := filepath.Clean(path)
	for {
		parent := filepath.Dir(p)
		if filepath.Clean(parent) == base {
			return p
		}
		if parent == p {
			return ""
		}
		p = parent
	}
}
