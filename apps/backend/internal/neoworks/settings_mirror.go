package neoworks

import (
	"context"
	"encoding/json"
	"fmt"
	"maps"
	"slices"
	"time"

	"github.com/Letsmoe/otter/internal/db"
	"github.com/Letsmoe/otter/internal/settings"
)

// settingsKey is the local key of the household's one Settings row.
const settingsKey = "household"

// HouseholdSettings is how sync reads and changes Otter's settings.
type HouseholdSettings interface {
	Cached() settings.Settings
	Apply(ctx context.Context, update settings.Update) (settings.Settings, error)
}

// settingsMirror mirrors the household's settings and the one Settings row.
// A row changed on both sides takes the other device's version.
type settingsMirror struct {
	database *db.DB
	store    rowStore
	settings HouseholdSettings
	// restoreDisabled switches plugins off in the running registry.
	restoreDisabled func(names []string)
}

func settingsContent(current settings.Settings, updatedAt time.Time) map[string]any {
	disabled := slices.Clone(current.DisabledPlugins)
	slices.Sort(disabled)
	if disabled == nil {
		disabled = []string{}
	}
	pluginSettings := []map[string]any{}
	for _, plugin := range slices.Sorted(maps.Keys(current.PluginSettings)) {
		pluginSettings = append(pluginSettings, map[string]any{"plugin": plugin, "values": current.PluginSettings[plugin]})
	}
	content := map[string]any{
		"tmdbLanguage":    current.TMDBLanguage,
		"requireVpn":      current.RequireVpn,
		"disabledPlugins": disabled,
		"pluginSettings":  pluginSettings,
		"updatedAt":       db.FormatTime(updatedAt),
	}
	if current.TMDBApiKey != "" {
		content["tmdbApiKey"] = current.TMDBApiKey
	}
	return content
}

func settingsHash(content map[string]any) (string, error) {
	return contentHash(content, "updatedAt")
}

// remoteSettings is a Settings row as read from the local store.
type remoteSettings struct {
	TMDBLanguage    string   `json:"tmdbLanguage"`
	RequireVpn      bool     `json:"requireVpn"`
	DisabledPlugins []string `json:"disabledPlugins"`
	PluginSettings  []struct {
		Plugin string            `json:"plugin"`
		Values map[string]string `json:"values"`
	} `json:"pluginSettings"`
	TMDBApiKey string `json:"tmdbApiKey"`
	UpdatedAt  string `json:"updatedAt"`
}

// sync applies the newest Settings row when it changed elsewhere, else writes
// the local settings when they changed here. It returns (received, sent).
func (mirror settingsMirror) sync(ctx context.Context) (int, int, error) {
	remote, _, err := mirror.store.rows(tableSettings)
	if err != nil {
		return 0, 0, err
	}
	links, err := loadMirroredRows(ctx, mirror.database, tableSettings)
	if err != nil {
		return 0, 0, err
	}
	link := mirroredRow{}
	if len(links) > 0 {
		link = links[0]
	}
	rowID, content, err := mirror.newestRow(remote)
	if err != nil {
		return 0, 0, err
	}
	received, err := mirror.applyRemote(ctx, link, rowID, content)
	if err != nil {
		return received, 0, err
	}
	if received > 0 {
		return received, 0, nil
	}
	sent, err := mirror.pushLocal(ctx, link, rowID)
	return 0, sent, err
}

// newestRow keeps the newest Settings row; two devices may each have written
// one before their first sync, and the older one is deleted.
func (mirror settingsMirror) newestRow(remote map[string]map[string]any) (string, map[string]any, error) {
	newestID := ""
	var newest map[string]any
	for rowID, content := range remote {
		updatedAt, _ := content["updatedAt"].(string)
		newestAt, _ := newest["updatedAt"].(string)
		if newest == nil || updatedAt > newestAt {
			newestID, newest = rowID, content
		}
	}
	for rowID := range remote {
		if rowID == newestID {
			continue
		}
		if err := mirror.store.deleteRow(rowID); err != nil {
			return "", nil, err
		}
	}
	return newestID, newest, nil
}

func (mirror settingsMirror) applyRemote(ctx context.Context, link mirroredRow, rowID string, content map[string]any) (int, error) {
	if rowID == "" {
		return 0, nil
	}
	hash, err := settingsHash(content)
	if err != nil {
		return 0, err
	}
	if link.RowID == rowID && link.SyncedHash == hash {
		return 0, nil
	}
	if link.RowID == "" && mirror.localMatches(hash) {
		// First sync with settings already alike: link them without a change.
		return 0, saveMirroredRow(ctx, mirror.database, mirroredRow{TableName: tableSettings, LocalKey: settingsKey, RowID: rowID, SyncedHash: hash})
	}
	if err := mirror.applyLocally(ctx, content); err != nil {
		return 0, err
	}
	return 1, saveMirroredRow(ctx, mirror.database, mirroredRow{TableName: tableSettings, LocalKey: settingsKey, RowID: rowID, SyncedHash: hash})
}

func (mirror settingsMirror) localMatches(hash string) bool {
	localHash, err := settingsHash(settingsContent(mirror.settings.Cached(), time.Now()))
	return err == nil && localHash == hash
}

func (mirror settingsMirror) applyLocally(ctx context.Context, content map[string]any) error {
	encoded, err := json.Marshal(content)
	if err != nil {
		return err
	}
	var remote remoteSettings
	if err := json.Unmarshal(encoded, &remote); err != nil {
		return fmt.Errorf("settings row: %w", err)
	}
	disabled := remote.DisabledPlugins
	if disabled == nil {
		disabled = []string{}
	}
	pluginSettings := map[string]map[string]string{}
	for _, plugin := range remote.PluginSettings {
		pluginSettings[plugin.Plugin] = plugin.Values
	}
	update := settings.Update{
		TMDBLanguage: &remote.TMDBLanguage, RequireVpn: &remote.RequireVpn, TMDBApiKey: &remote.TMDBApiKey,
		DisabledPlugins: disabled, PluginSettings: pluginSettings,
	}
	if _, err := mirror.settings.Apply(ctx, update); err != nil {
		return err
	}
	mirror.restoreDisabled(disabled)
	return nil
}

func (mirror settingsMirror) pushLocal(ctx context.Context, link mirroredRow, liveRowID string) (int, error) {
	content := settingsContent(mirror.settings.Cached(), time.Now())
	hash, err := settingsHash(content)
	if err != nil {
		return 0, err
	}
	rowID := link.RowID
	if rowID != "" && rowID != liveRowID {
		// The linked row is gone (deleted as an older duplicate); write anew.
		rowID = ""
	}
	if rowID != "" && link.SyncedHash == hash {
		return 0, nil
	}
	if rowID == "" {
		rowID, err = mirror.store.createRow(tableSettings, content)
	} else {
		err = mirror.store.updateRow(rowID, content)
	}
	if err != nil {
		return 0, fmt.Errorf("sync settings: %w", err)
	}
	return 1, saveMirroredRow(ctx, mirror.database, mirroredRow{TableName: tableSettings, LocalKey: settingsKey, RowID: rowID, SyncedHash: hash})
}
