package graphql

import (
	"errors"
	"net/url"

	"github.com/Letsmoe/otter/internal/api/graphql/model"
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

func neoworksAccountModel(status neoworks.Status) *model.NeoworksAccount {
	account := &model.NeoworksAccount{SignedIn: status.SignedIn}
	if status.Email != "" {
		account.Email = &status.Email
	}
	if status.Name != "" {
		account.Name = &status.Name
	}
	return account
}
