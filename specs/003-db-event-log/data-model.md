# Data Model: Persistent Event Log Storage

## EventRecord (JPA Entity → `event_records` table)
| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `id` | UUID (PK) | Generated, non-null | Primary key returned to clients when POST succeeds. |
| `callerId` | VARCHAR(128) | Not null, indexed | Provided by clients; used for filtering and dedupe. |
| `severity` | ENUM (`INFO`,`WARN`,`ERROR`) | Default `INFO` | Derived from payload metadata; ensures consistent reporting. |
| `message` | TEXT | Not null | Original event message body. |
| `metadataJson` | TEXT | Nullable | Serialized metadata blob; stored as JSON string. |
| `clientTimestamp` | TIMESTAMP | Nullable | When client says event occurred; validated to not exceed +24h. |
| `receivedTimestamp` | TIMESTAMP | Not null, default NOW | Server receive time; used for ordering + retention. |
| `resolvedTimestamp` | TIMESTAMP | Not null | Either client timestamp (if valid) or received timestamp; primary ordering column. |
| `messageHash` | CHAR(44) | Not null | Base64 SHA-256 of canonicalized payload to support dedupe. |
| `status` | VARCHAR(32) | Not null | Values: `LOGGED`, `DROPPED_DUPLICATE`, `FAILED_VALIDATION`. |
| `expiresAt` | TIMESTAMP | Not null | resolvedTimestamp + 30 days for retention purge. |

**Indexes & Constraints**
- Unique constraint on `(caller_id, resolved_timestamp, message_hash)` enforces FR-007 (duplicate detection window). 5-second window enforced by hashing entire payload and checking timestamp difference before write.
- Index on `resolved_timestamp DESC` to speed pagination.
- Index on `caller_id, resolved_timestamp` for targeted history queries.

## PersistenceHealthSnapshot (Value Object)
| Field | Type | Source | Notes |
|-------|------|--------|-------|
| `status` | ENUM (`HEALTHY`,`DEGRADED`,`FAILED`) | Derived | Based on rolling success ratio of writes over last 60s. |
| `lastErrorTime` | TIMESTAMP | Derived | Last persistence failure time. |
| `pendingWrites` | INT | Derived | Count of queued writes when SQLite is locked; should remain 0. |
| `failureRate` | DECIMAL | Derived | (# failures / total) over 1-minute window; triggers alerts if >0.001. |

This object is stored in-memory but surfaced via REST headers and `/actuator/health`. It is recalculated continuously by a dedicated component.

## RetentionJobConfig (Configuration record)
| Field | Type | Notes |
|-------|------|-------|
| `retentionDays` | INT | Default 30, configurable through properties.
| `batchSize` | INT | Number of rows deleted per scheduled run (default 1000).
| `schedule` | CRON | Default nightly at 02:00 server time.

Although not persisted, codifying the configuration keys in code ensures the scheduler can be tested and tuned.
