package neoworks

import (
	"context"
	"encoding/base64"
	"time"

	"github.com/Letsmoe/otter/internal/db"
)

// accountRow is the neoworks_account row. Install is libneoworks' sealed install
// as base64; the tokens are sealed with the secrets box.
type accountRow struct {
	Install         string `json:"install"`
	UserID          string `json:"user_id"`
	Email           string `json:"email"`
	Name            string `json:"name"`
	AccessToken     string `json:"access_token"`
	RefreshToken    string `json:"refresh_token"`
	AccessExpiresAt string `json:"access_expires_at"`
	SignedInAt      string `json:"signed_in_at"`
}

func (row accountRow) signedIn() bool {
	return row.UserID != ""
}

func loadAccountRow(ctx context.Context, database *db.DB) (accountRow, error) {
	found, err := db.QueryOneAs[accountRow](ctx, database, `SELECT * FROM neoworks_account WHERE id = 1`, nil)
	if err != nil || found == nil {
		return accountRow{}, err
	}
	return *found, nil
}

func saveInstall(ctx context.Context, database *db.DB, sealedInstall []byte) error {
	_, err := database.Exec(ctx,
		`INSERT INTO neoworks_account (id, install) VALUES (1, $install)
		 ON CONFLICT (id) DO UPDATE SET install = excluded.install`,
		db.Vars{"install": base64.StdEncoding.EncodeToString(sealedInstall)})
	return err
}

// saveSession stores a sign-in; the tokens arrive sealed.
func saveSession(ctx context.Context, database *db.DB, row accountRow) error {
	_, err := database.Exec(ctx,
		`UPDATE neoworks_account SET user_id = $userId, email = $email, name = $name,
		   access_token = $accessToken, refresh_token = $refreshToken,
		   access_expires_at = $accessExpiresAt, signed_in_at = $signedInAt
		 WHERE id = 1`,
		db.Vars{
			"userId": row.UserID, "email": row.Email, "name": row.Name,
			"accessToken": row.AccessToken, "refreshToken": row.RefreshToken,
			"accessExpiresAt": row.AccessExpiresAt, "signedInAt": row.SignedInAt,
		})
	return err
}

// saveAccessToken keeps a refreshed token pair.
func saveAccessToken(ctx context.Context, database *db.DB, sealedAccess, sealedRefresh string, expires time.Time) error {
	_, err := database.Exec(ctx,
		`UPDATE neoworks_account SET access_token = $accessToken, refresh_token = $refreshToken,
		   access_expires_at = $accessExpiresAt WHERE id = 1`,
		db.Vars{"accessToken": sealedAccess, "refreshToken": sealedRefresh, "accessExpiresAt": db.FormatTime(expires)})
	return err
}

// forgetAccount removes the sign-in and the install: the next sign-in starts
// with a fresh install identity and an empty local store.
func forgetAccount(ctx context.Context, database *db.DB) error {
	_, err := database.Exec(ctx, `DELETE FROM neoworks_account WHERE id = 1`, nil)
	return err
}
