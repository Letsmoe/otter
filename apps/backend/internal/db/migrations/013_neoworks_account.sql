-- The household's Neoworks sign-in (sync). One row at most.
-- install is this backend's install identity, sealed by libneoworks under the
-- secrets key; access_token and refresh_token are sealed with the same key.
-- user_id, email and name describe the signed-in account and are empty until
-- a sign-in completes. Signing out deletes the row, install included.
CREATE TABLE neoworks_account (
    id                INTEGER PRIMARY KEY CHECK (id = 1),
    install           TEXT NOT NULL,
    user_id           TEXT NOT NULL DEFAULT '',
    email             TEXT NOT NULL DEFAULT '',
    name              TEXT NOT NULL DEFAULT '',
    access_token      TEXT NOT NULL DEFAULT '',
    refresh_token     TEXT NOT NULL DEFAULT '',
    access_expires_at TEXT NOT NULL DEFAULT '',
    signed_in_at      TEXT NOT NULL DEFAULT ''
);
