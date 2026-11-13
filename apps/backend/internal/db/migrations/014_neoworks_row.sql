-- Which Neoworks row mirrors which local record (Neoworks sync).
-- table_name is the collection's table ("Profile"), local_key the record's key
-- here (a profile id). synced_hash is the hash of the content both sides agreed
-- on at the last sync: a side whose content hashes differently changed since.
CREATE TABLE neoworks_row (
    table_name  TEXT NOT NULL,
    local_key   TEXT NOT NULL,
    row_id      TEXT NOT NULL UNIQUE,
    synced_hash TEXT NOT NULL,
    PRIMARY KEY (table_name, local_key)
);
