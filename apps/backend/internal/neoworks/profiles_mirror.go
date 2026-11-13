package neoworks

import (
	"context"
	"encoding/json"
	"fmt"
	"strconv"
	"time"

	"github.com/Letsmoe/otter/internal/db"
	"github.com/Letsmoe/otter/internal/profiles"
)

// profileContent is a local profile as a Profile row (packages/otter-schema/otter.schema).
func profileContent(profile profiles.Profile, updatedAt time.Time) map[string]any {
	languages := profile.LanguageList()
	if len(languages) == 0 {
		languages = []string{"en"}
	}
	captions := map[string]any{"enabled": profile.SubtitlesEnabled, "size": captionSize(profile.SubtitleSize)}
	if profile.SubtitleLanguage != "" {
		captions["language"] = profile.SubtitleLanguage
	}
	content := map[string]any{
		"name":       profile.Name,
		"isChild":    profile.IsChild,
		"languages":  languages,
		"captions":   captions,
		"voiceBoost": profile.VoiceBoost,
		"createdAt":  timestampOf(profile.CreatedAt),
		"updatedAt":  db.FormatTime(updatedAt),
	}
	if profile.AvatarURL != "" {
		content["avatar"] = profile.AvatarURL
	}
	if profile.PinHash != "" {
		content["pinHash"] = profile.PinHash
	}
	return content
}

func captionSize(size string) string {
	if size == profiles.SubtitleSizeSmall || size == profiles.SubtitleSizeLarge {
		return size
	}
	return profiles.SubtitleSizeMedium
}

// timestampOf passes Otter's stored times on; an unreadable one becomes now.
func timestampOf(stored string) string {
	parsed, err := time.Parse(db.TimeFormat, stored)
	if err != nil {
		return db.Now()
	}
	return db.FormatTime(parsed)
}

func profileHash(content map[string]any) (string, error) {
	return contentHash(content, "updatedAt")
}

// remoteProfile is a Profile row as read from the local store.
type remoteProfile struct {
	Name     string   `json:"name"`
	Avatar   string   `json:"avatar"`
	PinHash  string   `json:"pinHash"`
	IsChild  bool     `json:"isChild"`
	Language []string `json:"languages"`
	Captions struct {
		Enabled  bool   `json:"enabled"`
		Language string `json:"language"`
		Size     string `json:"size"`
	} `json:"captions"`
	VoiceBoost bool   `json:"voiceBoost"`
	CreatedAt  string `json:"createdAt"`
}

// profileMirror mirrors the profile table and Profile rows both ways.
type profileMirror struct {
	database *db.DB
	store    rowStore
}

// pull applies Profile rows that changed elsewhere since the last sync; a row
// changed on both sides takes the other device's version.
func (mirror profileMirror) pull(ctx context.Context) (int, error) {
	remote, deletedRowIDs, err := mirror.store.rows(tableProfile)
	if err != nil {
		return 0, err
	}
	mirrored, err := loadMirroredRows(ctx, mirror.database, tableProfile)
	if err != nil {
		return 0, err
	}
	byRowID := map[string]mirroredRow{}
	for _, row := range mirrored {
		byRowID[row.RowID] = row
	}
	if err := addVanishedRows(mirror.store, remote, deletedRowIDs, mirrored); err != nil {
		return 0, err
	}
	applied := 0
	for rowID := range deletedRowIDs {
		link, found := byRowID[rowID]
		if !found {
			continue
		}
		if err := mirror.deleteLocal(ctx, link); err != nil {
			return applied, err
		}
		applied++
	}
	for rowID, content := range remote {
		changed, err := mirror.pullRow(ctx, rowID, content, byRowID)
		if err != nil {
			return applied, err
		}
		if changed {
			applied++
		}
	}
	return applied, nil
}

// addVanishedRows counts mirrored rows the store no longer has as deleted: a row
// deleted before it was ever pushed leaves no deletion mark. Only while the store
// holds the collection's root, so a wiped or unready store never deletes profiles.
func addVanishedRows(store rowStore, remote map[string]map[string]any, deletedRowIDs map[string]bool, mirrored []mirroredRow) error {
	present, err := store.hasRoot()
	if err != nil || !present {
		return err
	}
	for _, link := range mirrored {
		if _, live := remote[link.RowID]; !live {
			deletedRowIDs[link.RowID] = true
		}
	}
	return nil
}

func (mirror profileMirror) pullRow(ctx context.Context, rowID string, content map[string]any, byRowID map[string]mirroredRow) (bool, error) {
	hash, err := profileHash(content)
	if err != nil {
		return false, err
	}
	link, found := byRowID[rowID]
	if found && link.SyncedHash == hash {
		return false, nil
	}
	profile, err := decodeRemoteProfile(content)
	if err != nil {
		return false, err
	}
	localKey := ""
	if found {
		localKey = link.LocalKey
	}
	profileID, err := mirror.writeLocal(ctx, localKey, profile)
	if err != nil {
		return false, err
	}
	return true, saveMirroredRow(ctx, mirror.database, mirroredRow{TableName: tableProfile, LocalKey: profileID, RowID: rowID, SyncedHash: hash})
}

func decodeRemoteProfile(content map[string]any) (remoteProfile, error) {
	encoded, err := json.Marshal(content)
	if err != nil {
		return remoteProfile{}, err
	}
	var profile remoteProfile
	if err := json.Unmarshal(encoded, &profile); err != nil {
		return remoteProfile{}, fmt.Errorf("profile row: %w", err)
	}
	return profile, nil
}

// writeLocal updates the mirrored profile, or adopts a local one of the same
// name (both devices had it before their first sync), or creates one.
func (mirror profileMirror) writeLocal(ctx context.Context, localKey string, profile remoteProfile) (string, error) {
	if localKey == "" {
		existing, err := mirror.unmirroredProfileNamed(ctx, profile.Name)
		if err != nil {
			return "", err
		}
		localKey = existing
	}
	languages, err := json.Marshal(profile.Language)
	if err != nil {
		return "", err
	}
	values := db.Vars{
		"name": profile.Name, "avatar": profile.Avatar, "pinHash": profile.PinHash, "isChild": profile.IsChild,
		"languages": string(languages), "subtitlesEnabled": profile.Captions.Enabled,
		"subtitleLanguage": nullableText(profile.Captions.Language), "subtitleSize": captionSize(profile.Captions.Size),
		"voiceBoost": profile.VoiceBoost, "createdAt": timestampOf(profile.CreatedAt),
	}
	if localKey == "" {
		return mirror.insertLocal(ctx, values)
	}
	values["id"] = localKey
	_, err = mirror.database.Exec(ctx,
		`UPDATE profile SET name = $name, avatar_url = $avatar, pin_hash = $pinHash, is_child = $isChild,
		   languages = $languages, subtitles_enabled = $subtitlesEnabled, subtitle_language = $subtitleLanguage,
		   subtitle_size = $subtitleSize, voice_boost = $voiceBoost WHERE id = $id`, values)
	return localKey, err
}

func (mirror profileMirror) insertLocal(ctx context.Context, values db.Vars) (string, error) {
	_, err := mirror.database.Exec(ctx,
		`INSERT INTO profile (name, avatar_url, pin_hash, is_child, created_at, languages, subtitles_enabled,
		   subtitle_language, subtitle_size, voice_boost)
		 VALUES ($name, $avatar, $pinHash, $isChild, $createdAt, $languages, $subtitlesEnabled,
		   $subtitleLanguage, $subtitleSize, $voiceBoost)`, values)
	if err != nil {
		return "", fmt.Errorf("add synced profile %q: %w", values["name"], err)
	}
	id, err := mirror.database.Int(ctx, `SELECT id FROM profile WHERE name = $name`, db.Vars{"name": values["name"]})
	return strconv.Itoa(id), err
}

// unmirroredProfileNamed is the id of a local profile with that name that no row mirrors yet, or "".
func (mirror profileMirror) unmirroredProfileNamed(ctx context.Context, name string) (string, error) {
	ids, err := mirror.database.Strings(ctx,
		`SELECT CAST(id AS TEXT) FROM profile WHERE name = $name
		   AND CAST(id AS TEXT) NOT IN (SELECT local_key FROM neoworks_row WHERE table_name = $table)`,
		db.Vars{"name": name, "table": tableProfile})
	if err != nil || len(ids) == 0 {
		return "", err
	}
	return ids[0], nil
}

// deleteLocal removes a profile another device deleted, with what it recorded.
func (mirror profileMirror) deleteLocal(ctx context.Context, link mirroredRow) error {
	err := mirror.database.Tx(ctx, func(tx *db.DB) error {
		for _, table := range []string{"watch_history", "user_rating", "watchlist"} {
			if _, err := tx.Exec(ctx, "DELETE FROM "+table+" WHERE profile_id = $id", db.Vars{"id": link.LocalKey}); err != nil {
				return err
			}
		}
		_, err := tx.Exec(ctx, "DELETE FROM profile WHERE id = $id", db.Vars{"id": link.LocalKey})
		return err
	})
	if err != nil {
		return err
	}
	return deleteMirroredRow(ctx, mirror.database, link.RowID)
}

// push writes local profiles that changed since the last sync, and deletes the
// rows of profiles deleted here. It returns how many rows it wrote.
func (mirror profileMirror) push(ctx context.Context) (int, error) {
	locals, err := db.QueryAs[profiles.Profile](ctx, mirror.database, "SELECT * FROM profile ORDER BY id", nil)
	if err != nil {
		return 0, err
	}
	mirrored, err := loadMirroredRows(ctx, mirror.database, tableProfile)
	if err != nil {
		return 0, err
	}
	byLocalKey := map[string]mirroredRow{}
	for _, row := range mirrored {
		byLocalKey[row.LocalKey] = row
	}
	written := 0
	for _, profile := range locals {
		localKey := strconv.Itoa(profile.ID)
		changed, err := mirror.pushProfile(ctx, profile, byLocalKey[localKey])
		if err != nil {
			return written, err
		}
		if changed {
			written++
		}
		delete(byLocalKey, localKey)
	}
	// What remains mirrors profiles that are gone here.
	for _, link := range byLocalKey {
		if err := mirror.store.deleteRow(link.RowID); err != nil {
			return written, err
		}
		if err := deleteMirroredRow(ctx, mirror.database, link.RowID); err != nil {
			return written, err
		}
		written++
	}
	return written, nil
}

func (mirror profileMirror) pushProfile(ctx context.Context, profile profiles.Profile, link mirroredRow) (bool, error) {
	content := profileContent(profile, time.Now())
	hash, err := profileHash(content)
	if err != nil {
		return false, err
	}
	if link.RowID != "" && link.SyncedHash == hash {
		return false, nil
	}
	rowID := link.RowID
	if rowID == "" {
		rowID, err = mirror.store.createRow(tableProfile, content)
	} else {
		err = mirror.store.updateRow(rowID, content)
	}
	if err != nil {
		return false, fmt.Errorf("sync profile %q: %w", profile.Name, err)
	}
	return true, saveMirroredRow(ctx, mirror.database, mirroredRow{TableName: tableProfile, LocalKey: strconv.Itoa(profile.ID), RowID: rowID, SyncedHash: hash})
}

func nullableText(value string) any {
	if value == "" {
		return nil
	}
	return value
}
