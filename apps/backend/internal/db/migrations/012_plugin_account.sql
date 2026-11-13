-- A plugin's login on its site (e.g. proxer.me). password and session are
-- sealed with the secrets key (AES-256-GCM, base64), never stored readable.
-- error is why the last login failed, shown next to the account.
CREATE TABLE plugin_account (
    plugin             TEXT PRIMARY KEY,
    username           TEXT NOT NULL,
    password           TEXT NOT NULL,
    session            TEXT NOT NULL DEFAULT '',
    session_expires_at TEXT NOT NULL DEFAULT '',
    error              TEXT NOT NULL DEFAULT '',
    updated_at         TEXT NOT NULL
);
