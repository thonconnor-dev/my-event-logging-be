# Data Model - Log Retrieval API with Transient Cache

## Entities

### LogEntry
| Field | Type | Description | Validation/Rule |
|-------|------|-------------|-----------------|
| `id` | String (SHA-256 hex) | Deterministic identifier derived from callerId + resolvedTimestamp + payload hash | 64 hex chars; unique in cache |
| `callerId` | String | Originating producer ID | Reuse POST validation (`[A-Za-z0-9_-]{1,64}`) |
| `message` | String | Human-readable event message | Trimmed, <=512 chars |
| `metadata` | Map<String,String> | Key/value annotations | <=10 entries, keys/values non-blank, values <=128 chars |
| `severity` | Enum (`INFO`, `WARN`, `ERROR`, `DEBUG`) | Derived from metadata tag or default `INFO` | Defaults to INFO when unspecified |
| `source` | Enum (`CLIENT`, `SERVER`) | Timestamp provenance mirroring `ResolvedEvent.TimestampSource` | Required |
| `timestamp` | Instant | Resolved event time | Must be within 24h future drift (leverages existing logic) |
| `correlationId` | String | Tracking identifier for downstream calls | Auto-generated when POST lacks header |

### TransientLogCache
| Field | Type | Description |
|-------|------|-------------|
| `entries` | Deque<LogEntry> | Ordered window of most recent log entries |
| `index` | Map<String,LogEntry> | Fast lookup by `id` for dedupe/backfill |
| `capacity` | int | Configurable upper bound (default 500, max 2000) |
| `lastRefresh` | Instant | Timestamp when cache last ingested or backfilled data |
| `evictionCount` | long | Rolling count of evicted entries |
| `state` | Enum (`HEALTHY`, `EMPTY`, `STALE`, `TRUNCATED`) | Derived from size + freshness + recent evictions |

### LogPage
| Field | Type | Description |
|-------|------|-------------|
| `items` | List<LogEntry> | Current page payload |
| `nextCursor` | String? | Base64 cursor referencing last entry in page | null when no more data |
| `cacheStatus` | CacheStatus | Snapshot of cache metadata |
| `dataComplete` | boolean | False when cache truncated due to eviction |

### CacheStatus
| Field | Type | Description |
|-------|------|-------------|
| `state` | Enum (`HEALTHY`, `EMPTY`, `STALE`, `TRUNCATED`) | Summarizes readiness |
| `lastRefresh` | Instant | Mirror of cache timestamp |
| `evictionCount` | long | Total evictions since boot |
| `stalenessSeconds` | long | Difference between now and `lastRefresh` |

## Relationships & Notes

- `EventLogService` will emit `LogEntry` objects into `TransientLogCache` immediately after logging; the GET service also references the same cache to satisfy "store missing entries" by inserting before returning.
- `LogPage.items` references the same `LogEntry` projections that clients expect; serialization occurs through new DTOs (e.g., `LogRecordResponse`).
- Pagination cursors store the `id` (and optionally timestamp) so the service can resume iteration from the correct deque position even if evictions occurred.
- Cache staleness is defined as `Duration.between(lastRefresh, now) > configurableThreshold` (default 60 seconds) and drives `cacheStatus.state` as well as the warning flag described in the spec.
