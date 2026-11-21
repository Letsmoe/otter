package graphql

import (
	"context"
	"encoding/base64"
	"errors"

	"github.com/Letsmoe/otter/internal/api/graphql/model"
	"github.com/Letsmoe/otter/internal/letterboxd"
)

// maxExportZipBytes bounds an uploaded export; real ones are a few hundred KB.
const maxExportZipBytes = 64 << 20

var importStates = map[letterboxd.ImportState]model.LetterboxdImportState{
	letterboxd.ImportIdle:    model.LetterboxdImportStateIdle,
	letterboxd.ImportRunning: model.LetterboxdImportStateRunning,
	letterboxd.ImportDone:    model.LetterboxdImportStateDone,
	letterboxd.ImportFailed:  model.LetterboxdImportStateFailed,
}

func letterboxdImportModel(status letterboxd.ImportStatus) *model.LetterboxdImport {
	notMatched := make([]*model.LetterboxdFilm, 0, len(status.NotMatched))
	for _, missing := range status.NotMatched {
		notMatched = append(notMatched, &model.LetterboxdFilm{
			Name:   missing.Name,
			Year:   optional(missing.Year),
			URI:    optional(missing.URI),
			Reason: missing.Reason,
		})
	}
	return &model.LetterboxdImport{
		State:          importStates[status.State],
		Total:          status.Total,
		Processed:      status.Processed,
		Matched:        status.Matched,
		Watched:        status.Watched,
		Rated:          status.Rated,
		Watchlisted:    status.Watchlisted,
		UnmatchedCount: status.UnmatchedCount,
		NotMatched:     notMatched,
		Error:          optional(status.Error),
	}
}

func letterboxdAccountModel(connection *letterboxd.Connection) *model.LetterboxdAccount {
	if connection == nil {
		return nil
	}
	return &model.LetterboxdAccount{
		Username:     connection.Username,
		LastSyncedAt: optional(connection.LastSyncedAt),
		LastError:    optional(connection.LastError),
		LastWatched:  connection.LastWatched,
		LastRated:    connection.LastRated,
	}
}

func (r *Resolver) importLetterboxd(profileID, zipBase64 string) (*model.LetterboxdImport, error) {
	profile, err := parseProfile(profileID)
	if err != nil {
		return nil, err
	}
	if base64.StdEncoding.DecodedLen(len(zipBase64)) > maxExportZipBytes {
		return nil, errors.New("this file is too big for a Letterboxd export")
	}
	content, err := base64.StdEncoding.DecodeString(zipBase64)
	if err != nil {
		return nil, errors.New("the file could not be read")
	}
	status, err := r.Letterboxd.StartImport(profile, content)
	if err != nil {
		return nil, err
	}
	return letterboxdImportModel(status), nil
}

func (r *Resolver) letterboxdImport(profileID string) (*model.LetterboxdImport, error) {
	profile, err := parseProfile(profileID)
	if err != nil {
		return nil, err
	}
	return letterboxdImportModel(r.Letterboxd.ImportOf(profile)), nil
}

func (r *Resolver) letterboxdAccount(ctx context.Context, profileID string) (*model.LetterboxdAccount, error) {
	profile, err := parseProfile(profileID)
	if err != nil {
		return nil, err
	}
	connection, err := r.Letterboxd.ConnectionOf(ctx, profile)
	if err != nil {
		return nil, err
	}
	return letterboxdAccountModel(connection), nil
}

func (r *Resolver) connectLetterboxd(ctx context.Context, profileID, username string) (*model.LetterboxdAccount, error) {
	profile, err := parseProfile(profileID)
	if err != nil {
		return nil, err
	}
	connection, err := r.Letterboxd.Connect(ctx, profile, username)
	if err != nil {
		return nil, err
	}
	return letterboxdAccountModel(connection), nil
}

func (r *Resolver) disconnectLetterboxd(ctx context.Context, profileID string) (bool, error) {
	profile, err := parseProfile(profileID)
	if err != nil {
		return false, err
	}
	return true, r.Letterboxd.Disconnect(ctx, profile)
}

func (r *Resolver) syncLetterboxd(ctx context.Context, profileID string) (*model.LetterboxdAccount, error) {
	profile, err := parseProfile(profileID)
	if err != nil {
		return nil, err
	}
	connection, err := r.Letterboxd.SyncNow(ctx, profile)
	if err != nil {
		return nil, err
	}
	return letterboxdAccountModel(connection), nil
}
