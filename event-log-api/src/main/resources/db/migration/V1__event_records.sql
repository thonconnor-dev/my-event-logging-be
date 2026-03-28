CREATE TABLE IF NOT EXISTS event_records (
    id TEXT PRIMARY KEY,
    caller_id TEXT NOT NULL,
    severity TEXT NOT NULL,
    message TEXT NOT NULL,
    metadata_json TEXT,
    client_timestamp TIMESTAMP,
    received_timestamp TIMESTAMP NOT NULL,
    resolved_timestamp TIMESTAMP NOT NULL,
    timestamp_source TEXT NOT NULL,
    correlation_id TEXT NOT NULL,
    message_hash TEXT NOT NULL,
    status TEXT NOT NULL,
    expires_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_event_records_dedupe
    ON event_records (caller_id, resolved_timestamp, message_hash);

CREATE INDEX IF NOT EXISTS ix_event_records_resolved_timestamp
    ON event_records (resolved_timestamp DESC);

CREATE INDEX IF NOT EXISTS ix_event_records_caller_timestamp
    ON event_records (caller_id, resolved_timestamp DESC);
