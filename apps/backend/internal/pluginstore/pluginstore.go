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
	"sync"
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
// "@letsmoe/otter-plugin-eporner" → "eporner".
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
	checking    sync.Mutex
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

// Update is one plugin folder swapped to a newer release.
type Update struct {
	Folder      string
	Package     string
	FromVersion string
	ToVersion   string
}

// Report is what one update check did.
type Report struct {
	Updated []Update
	// Failed names the folders whose check, download or install failed.
	Failed []string
}

// UpdateAll updates every npm-installed plugin that has a newer release.
// Concurrent calls (a manual check during the periodic one) run one after the other.
func (s *Service) UpdateAll(ctx context.Context) Report {
	s.checking.Lock()
	defer s.checking.Unlock()
	report := Report{}
	entries, err := os.ReadDir(s.pluginDir)
	if err != nil {
		log.Printf("pluginstore: reading %s: %v", s.pluginDir, err)
		return report
	}
	for _, entry := range entries {
		if !entry.IsDir() {
			continue
		}
		update, err := s.updateFolder(ctx, filepath.Join(s.pluginDir, entry.Name()))
		if err != nil {
			log.Printf("pluginstore: %s: %v", entry.Name(), err)
			report.Failed = append(report.Failed, entry.Name())
			continue
		}
		if update != nil {
			report.Updated = append(report.Updated, *update)
		}
	}
	return report
}

// updateFolder returns the update it installed, or nil when the folder is current.
func (s *Service) updateFolder(ctx context.Context, folder string) (*Update, error) {
	installed, found, err := readInstalledPackage(folder)
	if err != nil || !found {
		return nil, err
	}
	// Dev builds from this checkout are never replaced.
	if installed.LocalBuild || installed.Name == "" {
		return nil, nil
	}
	latest, err := s.latestRelease(ctx, installed.Name)
	if err != nil {
		return nil, err
	}
	if !isNewer(latest.Version, installed.Version) {
		return nil, nil
	}
	files, err := s.downloadRelease(ctx, latest)
	if err != nil {
		return nil, fmt.Errorf("%s@%s: %w", installed.Name, latest.Version, err)
	}
	if err := install(folder, files); err != nil {
		return nil, err
	}
	log.Printf("pluginstore: updated %s %s → %s", installed.Name, installed.Version, latest.Version)
	update := Update{Folder: folder, Package: installed.Name, FromVersion: installed.Version, ToVersion: latest.Version}
	return &update, nil
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
