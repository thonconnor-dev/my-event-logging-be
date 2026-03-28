# Data Model: Retire Retention Job and Transient Cache

## EventRecord (Durable Source of Truth)
| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `id` | UUID | Primary key, generated | Unchanged; referenced in pagination tokens.
| `callerId` | VARCHAR(128) | Not null, indexed | Still used for filtering/reporting.
| `message` | TEXT | Not null | Body stored exactly as submitted.
| `metadataJson` | TEXT | Nullable | Parsed per request when building responses.
| `severity` | ENUM (`INFO`,`WARN`,`ERROR`,`DEBUG`) | Not null | Already enforced via validation.
| `resolvedTimestamp` | TIMESTAMP | Not null, indexed desc | Primary ordering + pagination column.
| `correlationId` | VARCHAR(64) | Nullable | Propagated end-to-end.
| `messageHash` | CHAR(44) | Unique with caller/timestamp | Enables upstream idempotence checks without cache.
| `expiresAt` | TIMESTAMP | Not null | Used by DB-level retention cron or VACUUM job; service no longer touches it.

**Behavioral Notes**
- Deduplication stays inside the repository by checking the unique constraint before write; no cache dedupe path remains.
- Pagination tokens only encode `id` + `resolvedTimestamp`; removal of cache metadata does not change token shape.

## LogPageResponse (API DTO)
| Field | Type | Notes |
|-------|------|-------|
| `events` | List<LogRecordResponse> | Same payload previously exposed, sourced directly from persistence.
| `nextPageToken` | String (nullable) | When `null`, indicates no more pages; format unchanged so existing clients continue working.
| `dataComplete` | boolean | Indicates whether the page contains all entries covering the requested window.
| ~~`cacheStatus`~~ | *Removed* | Dropped entirely; clients must not expect cache metadata moving forward.

## LegacyRetentionProperties (Startup Validation Helper)
| Field | Type | Notes |
|-------|------|-------|
| `propertiesPresent` | boolean | Derived by scanning `Environment` for keys prefixed with `eventlog.retention.`.
| `keys` | List<String> | Names of offending properties surfaced in the startup error message.

Used only during bootstrap to instruct operators to delete obsolete settings before the app proceeds.
