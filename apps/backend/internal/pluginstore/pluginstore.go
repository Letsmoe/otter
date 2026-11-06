// Package pluginstore keeps installed plugins current with their npm
// releases: it asks the registry for each plugin's latest version and swaps in
// newer bundles, which the plugin registry then hot-reloads.
package pluginstore

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"
)

// Keyword marks an npm package as an Otter plugin.
const Keyword = "otter-plugin"

// packagePrefix is stripped from package names to name their folder.
const packagePrefix = "otter-plugin-"

const (
	DefaultRegistryURL = "https://registry.npmjs.org"
	startupDelay       = 30 * time.Second
	checkInterval      = 6 * time.Hour
)

// FolderName is the installed-plugin folder of a package:
// "@neoworks-dev/otter-plugin-eporner" → "eporner".
func FolderName(packageName string) string {
	unscoped := packageName[strings.LastIndex(packageName, "/")+1:]
	return strings.TrimPrefix(unscoped, packagePrefix)
}

// installedPackage is the part of a plugin folder's package.json the store reads.
type installedPackage struct {
	Name       string `json:"name"`
	Version    string `json:"version"`
	LocalBuild bool   `json:"localBuild"`
}

// Service updates the plugins in one plugin directory from an npm registry.
type Service struct {
	pluginDir   string
	registryURL string
	client      *http.Client
}

func New(pluginDir string) *Service {
	return &Service{
		pluginDir:   pluginDir,
		registryURL: DefaultRegistryURL,
		client:      &http.Client{Timeout: 2 * time.Minute},
	}
}

// Run checks for updates shortly after start and then every few hours until ctx ends.
func (s *Service) Run(ctx context.Context) {
	timer := time.NewTimer(startupDelay)
	defer timer.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-timer.C:
			s.UpdateAll(ctx)
			timer.Reset(checkInterval)
		}
	}
}

// UpdateAll updates every npm-installed plugin that has a newer release.
func (s *Service) UpdateAll(ctx context.Context) {
	entries, err := os.ReadDir(s.pluginDir)
	if err != nil {
		log.Printf("pluginstore: reading %s: %v", s.pluginDir, err)
		return
	}
	for _, entry := range entries {
		if !entry.IsDir() {
			continue
		}
		if err := s.updateFolder(ctx, filepath.Join(s.pluginDir, entry.Name())); err != nil {
			log.Printf("pluginstore: %s: %v", entry.Name(), err)
		}
	}
}

func (s *Service) updateFolder(ctx context.Context, folder string) error {
	installed, found, err := readInstalledPackage(folder)
	if err != nil || !found {
		return err
	}
	// Dev builds from this checkout are never replaced.
	if installed.LocalBuild || installed.Name == "" {
		return nil
	}
	latest, err := s.latestRelease(ctx, installed.Name)
	if err != nil {
		return err
	}
	if !isNewer(latest.Version, installed.Version) {
		return nil
	}
	files, err := s.downloadRelease(ctx, latest)
	if err != nil {
		return fmt.Errorf("%s@%s: %w", installed.Name, latest.Version, err)
	}
	if err := install(folder, files); err != nil {
		return err
	}
	log.Printf("pluginstore: updated %s %s → %s", installed.Name, installed.Version, latest.Version)
	return nil
}

func readInstalledPackage(folder string) (installedPackage, bool, error) {
	var installed installedPackage
	data, err := os.ReadFile(filepath.Join(folder, "package.json"))
	if os.IsNotExist(err) {
		return installed, false, nil
	}
	if err != nil {
		return installed, false, err
	}
	if err := json.Unmarshal(data, &installed); err != nil {
		return installed, false, fmt.Errorf("parse package.json: %w", err)
	}
	return installed, true, nil
}

// Enabled reports whether auto-updates are on; PLUGIN_AUTO_UPDATE=false turns them off.
func Enabled() bool {
	return strings.TrimSpace(strings.ToLower(os.Getenv("PLUGIN_AUTO_UPDATE"))) != "false"
}
