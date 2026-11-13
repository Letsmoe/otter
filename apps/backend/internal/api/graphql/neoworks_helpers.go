package graphql

import (
	"context"
	"errors"
	"net/url"

	"github.com/Letsmoe/otter/internal/api/graphql/model"
	"github.com/Letsmoe/otter/internal/db"
	"github.com/Letsmoe/otter/internal/neoworks"
)

var errBadReturnOrigin = errors.New("returnTo must be the web app's origin, like http://nas.local:3000")

// signInReturnOrigin checks the web app's origin; nil or empty means the Android app.
func signInReturnOrigin(returnTo *string) (string, error) {
	if returnTo == nil || *returnTo == "" {
		return "", nil
	}
	parsed, err := url.Parse(*returnTo)
	if err != nil || parsed.Host == "" {
		return "", errBadReturnOrigin
	}
	if parsed.Scheme != "http" && parsed.Scheme != "https" {
		return "", errBadReturnOrigin
	}
	return parsed.Scheme + "://" + parsed.Host, nil
}

// syncNeoworks syncs now. A failed run is reported in lastSync; only being
// signed out is an error.
func (r *Resolver) syncNeoworks(ctx context.Context) (*model.NeoworksAccount, error) {
	if _, err := r.Neoworks.Sync(ctx); errors.Is(err, neoworks.ErrSignedOut) {
		return nil, err
	}
	status, err := r.Neoworks.Status(ctx)
	if err != nil {
		return nil, err
	}
	return neoworksAccountModel(status, r.Neoworks.LastSync()), nil
}

func neoworksAccountModel(status neoworks.Status, lastSync *neoworks.SyncResult) *model.NeoworksAccount {
	account := &model.NeoworksAccount{SignedIn: status.SignedIn}
	if status.Email != "" {
		account.Email = &status.Email
	}
	if status.Name != "" {
		account.Name = &status.Name
	}
	if lastSync != nil && status.SignedIn {
		account.LastSync = neoworksSyncModel(*lastSync)
	}
	return account
}

func neoworksSyncModel(result neoworks.SyncResult) *model.NeoworksSync {
	sync := &model.NeoworksSync{At: db.FormatTime(result.At), Received: result.Received, Sent: result.Sent}
	if result.Failure != "" {
		sync.Failure = &result.Failure
	}
	return sync
}
