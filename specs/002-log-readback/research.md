# Phase 0 Research - Log Retrieval API with Transient Cache

## Decision 1: Transient Cache Structure
- **Decision**: Use a bounded `ConcurrentLinkedDeque` to maintain insertion order plus a `ConcurrentHashMap` keyed by deterministic log IDs for deduplication, wrapped in a `TransientLogCache` component with configurable capacity (default 500).
- **Rationale**: Deque preserves ordering for pagination, concurrent collections avoid locking under concurrent POST/GET traffic, and separate index supports O(1) dedupe/backfill operations while keeping heap usage predictable.
- **Alternatives Considered**:
  - `CopyOnWriteArrayList`: too expensive for frequent writes and would require linear scans for eviction.
  - Plain `List` + synchronized blocks: simpler but risks contention and accidental iteration/structure modification exceptions under concurrent access.
  - Third-party caches (Caffeine): overkill for the interim "Java list" requirement and introduces another dependency.

## Decision 2: Cache Backfill Trigger on Read
- **Decision**: The GET flow will iterate over the candidate result set, insert any missing entries into the cache before serialization, and emit metrics about insert vs. reuse counts.
- **Rationale**: Keeps cache + response aligned with spec requirement "each log is stored in memory" without requiring a second persistence system; instrumentation clarifies whether cache is healthy.
- **Alternatives Considered**:
  - Fail the read if cache missing entries: violates requirement to serve data even if cache was cold.
  - Lazy insert after response: risks inconsistent follow-up reads and contradicts spec.

## Decision 3: Log Identity & Deduplication
- **Decision**: Represent each log entry with a derived ID composed of `callerId + resolvedTimestamp + SHA-256(message + metadata)`; use that ID both for deduping and pagination tokens.
- **Rationale**: Provides deterministic IDs without altering POST contract, distinguishes repeated messages at different timestamps, and allows dedup even if correlationId changes.
- **Alternatives Considered**:
  - Use correlationId alone: collisions whenever clients omit header (service currently generates IDs per request).
  - Use random UUID on ingestion: dedupe impossible without storing full payload comparisons.

## Decision 4: Pagination Contract
- **Decision**: Implement cursor-based pagination with query params `limit` (default 50, max 200) and `cursor` (opaque base64 encoding of log ID). Responses include `nextCursor` when more data remains.
- **Rationale**: Cursor paging avoids issues when cache mutates between requests and scales better than page-number/offset in a bounded list.
- **Alternatives Considered**:
  - Offset pagination: brittle if cache evicts between requests.
  - Pure page-size w/o cursor: caller cannot page beyond first slice reliably.

## Decision 5: Cache Health Metadata
- **Decision**: Surface `cacheStatus` block with fields `{state: HEALTHY|EMPTY|STALE|TRUNCATED, lastRefresh, evictionCount}` plus boolean `dataComplete` (true unless evictions truncated results).
- **Rationale**: Directly maps to spec user stories (empty/stale warnings, truncation visibility) and keeps API self-describing.
- **Alternatives Considered**:
  - HTTP headers: harder for clients to consume and test; payload-level metadata keeps schema consistent.
  - Separate health endpoint: adds another hop and doesn't assist when retrieving data.

## Decision 6: Testing & Observability Approach
- **Decision**: Extend MockMvc tests to cover GET contract (pagination, cache health states) and add focused unit tests for `TransientLogCache` (eviction, dedup, concurrency) plus service-layer tests verifying backfill logic.
- **Rationale**: Aligns with existing testing stack, provides fast feedback at unit level, and ensures regressions are caught when cache rules change.
- **Alternatives Considered**:
  - Integration tests only: slower and harder to cover edge cases like dedup collisions.
  - Custom test harness: unnecessary given Spring test support already present.
