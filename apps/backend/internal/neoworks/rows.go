package neoworks

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"

	"github.com/Letsmoe/otter/internal/db"
)

// mirroredRow links a local record to the Neoworks row that mirrors it.
type mirroredRow struct {
	TableName  string `json:"table_name"`
	LocalKey   string `json:"local_key"`
	RowID      string `json:"row_id"`
	SyncedHash string `json:"synced_hash"`
}

func loadMirroredRows(ctx context.Context, database *db.DB, table string) ([]mirroredRow, error) {
	return db.QueryAs[mirroredRow](ctx, database, `SELECT * FROM neoworks_row WHERE table_name = $table`, db.Vars{"table": table})
}

func saveMirroredRow(ctx context.Context, database *db.DB, row mirroredRow) error {
	_, err := database.Exec(ctx,
		`INSERT INTO neoworks_row (table_name, local_key, row_id, synced_hash)
		 VALUES ($table, $localKey, $rowId, $hash)
		 ON CONFLICT (table_name, local_key) DO UPDATE SET row_id = excluded.row_id, synced_hash = excluded.synced_hash`,
		db.Vars{"table": row.TableName, "localKey": row.LocalKey, "rowId": row.RowID, "hash": row.SyncedHash})
	return err
}

func deleteMirroredRow(ctx context.Context, database *db.DB, rowID string) error {
	_, err := database.Exec(ctx, `DELETE FROM neoworks_row WHERE row_id = $rowId`, db.Vars{"rowId": rowID})
	return err
}

// forgetMirroredRows drops every link; signing out starts the next account fresh.
func forgetMirroredRows(ctx context.Context, database *db.DB) error {
	_, err := database.Exec(ctx, `DELETE FROM neoworks_row`, nil)
	return err
}

// contentHash identifies content regardless of key order. Fields named in
// ignored (timestamps that change on every write) are left out.
func contentHash(content map[string]any, ignored ...string) (string, error) {
	kept := map[string]any{}
	for field, value := range content {
		kept[field] = value
	}
	for _, field := range ignored {
		delete(kept, field)
	}
	// Through JSON and back, local values take the shapes read rows have (numbers
	// as float64, slices as []any); encoding/json writes map keys sorted, so equal
	// content then hashes equally.
	encoded, err := json.Marshal(kept)
	if err != nil {
		return "", err
	}
	var normalized any
	if err := json.Unmarshal(encoded, &normalized); err != nil {
		return "", err
	}
	canonical, err := json.Marshal(normalized)
	if err != nil {
		return "", err
	}
	digest := sha256.Sum256(canonical)
	return hex.EncodeToString(digest[:]), nil
}
