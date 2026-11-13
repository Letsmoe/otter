package neoworks

import (
	"context"
	"fmt"
	"strconv"
	"strings"

	"github.com/Letsmoe/otter/internal/db"
)

// itemSpec describes a table of per-profile, per-title records (watch progress,
// My List, ratings): the local table keyed by (profile_id, media_type, media_id)
// and the Neoworks table whose rows belong to the profile's row.
type itemSpec struct {
	table      string
	localTable string
	// content turns a local record into the row's fields, without "profile".
	content func(record db.Row) map[string]any
	// timeField is the content field that orders versions: the newer one wins.
	timeField string
	// localTime reads the same moment from a local record.
	localTime func(record db.Row) string
	// apply writes a row's content into the local record.
	apply func(ctx context.Context, database *db.DB, key itemKey, content map[string]any, local db.Row) error
}

// itemKey names a local record.
type itemKey struct {
	profileID string
	mediaType string
	mediaID   string
}

func (key itemKey) String() string {
	return key.profileID + "/" + key.mediaType + "/" + key.mediaID
}

func parseItemKey(text string) (itemKey, bool) {
	parts := strings.Split(text, "/")
	if len(parts) != 3 {
		return itemKey{}, false
	}
	return itemKey{profileID: parts[0], mediaType: parts[1], mediaID: parts[2]}, true
}

func keyOfRecord(record db.Row) itemKey {
	return itemKey{
		profileID: fmt.Sprint(record["profile_id"]),
		mediaType: fmt.Sprint(record["media_type"]),
		mediaID:   fmt.Sprint(record["media_id"]),
	}
}

// itemMirror mirrors one itemSpec table both ways.
type itemMirror struct {
	database *db.DB
	store    rowStore
	spec     itemSpec
}

// profileRows maps local profile ids to their row ids and back.
type profileRows struct {
	rowOfProfile map[string]string
	profileOfRow map[string]string
}

func loadProfileRows(ctx context.Context, database *db.DB) (profileRows, error) {
	links, err := loadMirroredRows(ctx, database, tableProfile)
	if err != nil {
		return profileRows{}, err
	}
	rows := profileRows{rowOfProfile: map[string]string{}, profileOfRow: map[string]string{}}
	for _, link := range links {
		rows.rowOfProfile[link.LocalKey] = link.RowID
		rows.profileOfRow[link.RowID] = link.LocalKey
	}
	return rows, nil
}

func (mirror itemMirror) localRecords(ctx context.Context) (map[string]db.Row, error) {
	records, err := mirror.database.Query(ctx, "SELECT * FROM "+mirror.spec.localTable, nil)
	if err != nil {
		return nil, err
	}
	byKey := map[string]db.Row{}
	for _, record := range records {
		byKey[keyOfRecord(record).String()] = record
	}
	return byKey, nil
}

func (mirror itemMirror) rowContent(record db.Row, profileRowID string) map[string]any {
	content := mirror.spec.content(record)
	content["profile"] = profileRowID
	return content
}

// pull applies rows that changed elsewhere: new and newer ones are written
// locally, and records whose rows were deleted are removed.
func (mirror itemMirror) pull(ctx context.Context, profiles profileRows) (int, error) {
	remote, deletedRowIDs, err := mirror.store.rows(mirror.spec.table)
	if err != nil {
		return 0, err
	}
	links, err := loadMirroredRows(ctx, mirror.database, mirror.spec.table)
	if err != nil {
		return 0, err
	}
	if err := addVanishedRows(mirror.store, remote, deletedRowIDs, links); err != nil {
		return 0, err
	}
	locals, err := mirror.localRecords(ctx)
	if err != nil {
		return 0, err
	}
	applied, err := mirror.applyDeletions(ctx, links, deletedRowIDs)
	if err != nil {
		return applied, err
	}
	state := pullState{links: indexLinks(links), locals: locals, profiles: profiles}
	for rowID, content := range remote {
		changed, err := mirror.pullRow(ctx, rowID, content, state)
		if err != nil {
			return applied, err
		}
		if changed {
			applied++
		}
	}
	return applied, nil
}

// pullState is what a pull looks rows up in.
type pullState struct {
	links    linkIndex
	locals   map[string]db.Row
	profiles profileRows
}

type linkIndex struct {
	byRowID    map[string]mirroredRow
	byLocalKey map[string]mirroredRow
}

func indexLinks(links []mirroredRow) linkIndex {
	index := linkIndex{byRowID: map[string]mirroredRow{}, byLocalKey: map[string]mirroredRow{}}
	for _, link := range links {
		index.byRowID[link.RowID] = link
		index.byLocalKey[link.LocalKey] = link
	}
	return index
}

func (mirror itemMirror) applyDeletions(ctx context.Context, links []mirroredRow, deletedRowIDs map[string]bool) (int, error) {
	applied := 0
	for _, link := range links {
		if !deletedRowIDs[link.RowID] {
			continue
		}
		if err := mirror.deleteLocal(ctx, link.LocalKey); err != nil {
			return applied, err
		}
		if err := deleteMirroredRow(ctx, mirror.database, link.RowID); err != nil {
			return applied, err
		}
		applied++
	}
	return applied, nil
}

func (mirror itemMirror) pullRow(ctx context.Context, rowID string, content map[string]any, state pullState) (bool, error) {
	hash, err := contentHash(content)
	if err != nil {
		return false, err
	}
	if link, found := state.links.byRowID[rowID]; found && link.SyncedHash == hash {
		return false, nil
	}
	key, known := mirror.keyOfRow(content, state.profiles)
	if !known {
		// Its profile hasn't arrived yet; the next sync applies it.
		return false, nil
	}
	local := state.locals[key.String()]
	if other, taken := state.links.byLocalKey[key.String()]; taken && other.RowID != rowID {
		return mirror.settleDuplicate(ctx, rowID, other, content, local)
	}
	if local != nil && mirror.localIsNewer(local, content) {
		// The next push writes the newer local version over the row.
		return false, nil
	}
	if err := mirror.spec.apply(ctx, mirror.database, key, content, local); err != nil {
		return false, err
	}
	return true, saveMirroredRow(ctx, mirror.database, mirroredRow{TableName: mirror.spec.table, LocalKey: key.String(), RowID: rowID, SyncedHash: hash})
}

// settleDuplicate keeps one of two rows two devices made for the same record
// before they synced: the newer one stays, the other is deleted.
func (mirror itemMirror) settleDuplicate(ctx context.Context, rowID string, kept mirroredRow, content map[string]any, local db.Row) (bool, error) {
	if local != nil && mirror.localIsNewer(local, content) {
		return false, mirror.store.deleteRow(rowID)
	}
	if err := mirror.store.deleteRow(kept.RowID); err != nil {
		return false, err
	}
	if err := deleteMirroredRow(ctx, mirror.database, kept.RowID); err != nil {
		return false, err
	}
	key, _ := parseItemKey(kept.LocalKey)
	if err := mirror.spec.apply(ctx, mirror.database, key, content, local); err != nil {
		return false, err
	}
	hash, err := contentHash(content)
	if err != nil {
		return false, err
	}
	return true, saveMirroredRow(ctx, mirror.database, mirroredRow{TableName: mirror.spec.table, LocalKey: kept.LocalKey, RowID: rowID, SyncedHash: hash})
}

func (mirror itemMirror) keyOfRow(content map[string]any, profiles profileRows) (itemKey, bool) {
	profileRowID, _ := content["profile"].(string)
	profileID, found := profiles.profileOfRow[profileRowID]
	if !found {
		return itemKey{}, false
	}
	mediaType, _ := content["mediaType"].(string)
	mediaID, _ := content["tmdbId"].(string)
	if mediaType == "" || mediaID == "" {
		return itemKey{}, false
	}
	return itemKey{profileID: profileID, mediaType: mediaType, mediaID: mediaID}, true
}

// localIsNewer compares the version moments; timestamps in Otter's format sort as text.
func (mirror itemMirror) localIsNewer(local db.Row, content map[string]any) bool {
	remoteTime, _ := content[mirror.spec.timeField].(string)
	return mirror.spec.localTime(local) > remoteTime
}

func (mirror itemMirror) deleteLocal(ctx context.Context, localKey string) error {
	key, valid := parseItemKey(localKey)
	if !valid {
		return nil
	}
	_, err := mirror.database.Exec(ctx,
		"DELETE FROM "+mirror.spec.localTable+" WHERE profile_id = $profileId AND media_type = $mediaType AND media_id = $mediaId",
		db.Vars{"profileId": key.profileID, "mediaType": key.mediaType, "mediaId": key.mediaID})
	return err
}

// push writes records that changed here since the last sync and deletes the
// rows of records removed here.
func (mirror itemMirror) push(ctx context.Context, profiles profileRows) (int, error) {
	locals, err := mirror.localRecords(ctx)
	if err != nil {
		return 0, err
	}
	links, err := loadMirroredRows(ctx, mirror.database, mirror.spec.table)
	if err != nil {
		return 0, err
	}
	byLocalKey := indexLinks(links).byLocalKey
	written := 0
	for localKey, record := range locals {
		changed, err := mirror.pushRecord(ctx, localKey, record, byLocalKey[localKey], profiles)
		if err != nil {
			return written, err
		}
		if changed {
			written++
		}
		delete(byLocalKey, localKey)
	}
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

func (mirror itemMirror) pushRecord(ctx context.Context, localKey string, record db.Row, link mirroredRow, profiles profileRows) (bool, error) {
	profileRowID, mirrored := profiles.rowOfProfile[keyOfRecord(record).profileID]
	if !mirrored {
		// The profile's row is written first; the record follows next sync.
		return false, nil
	}
	content := mirror.rowContent(record, profileRowID)
	hash, err := contentHash(content)
	if err != nil {
		return false, err
	}
	if link.RowID != "" && link.SyncedHash == hash {
		return false, nil
	}
	rowID := link.RowID
	if rowID == "" {
		rowID, err = mirror.store.createRow(mirror.spec.table, content)
	} else {
		err = mirror.store.updateRow(rowID, content)
	}
	if err != nil {
		return false, fmt.Errorf("sync %s %s: %w", mirror.spec.table, localKey, err)
	}
	return true, saveMirroredRow(ctx, mirror.database, mirroredRow{TableName: mirror.spec.table, LocalKey: localKey, RowID: rowID, SyncedHash: hash})
}

// tmdbIDText is a media_id as the decimal string i64 fields travel as.
func tmdbIDText(record db.Row) string {
	return fmt.Sprint(record["media_id"])
}

func mediaIDOf(key itemKey) (int64, error) {
	return strconv.ParseInt(key.mediaID, 10, 64)
}
