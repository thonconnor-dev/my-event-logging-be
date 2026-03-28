# Research: Retire Retention Job and Transient Cache

## Decision 1: Database-Only Retention Enforcement
- **Decision**: Remove the Spring `@Scheduled` `RetentionJob` entirely and rely on SQLite-level TTL (existing `expires_at` column + scheduled DELETE in Flyway migration or DBA-managed cron outside the service).
- **Rationale**: Prevents double-deletes and eliminates cron configuration that is now redundant with DB lifecycle controls, satisfying FR-001/FR-006 and keeping runtime predictable.
- **Alternatives Considered**:
  - **Keep job but disable by default**: Risky because environments might accidentally re-enable it and it still bloats startup logging/tests.
  - **Replace with manual admin endpoint**: Adds operational steps and requires auth; no user requirement for on-demand purge.

## Decision 2: Direct-to-Database Read/Write Path
- **Decision**: Refactor `EventReadService`, `EventWriteService`, and supporting helpers so they read/write exclusively via `EventRecordRepository`; remove `TransientLogCache` bean plus its configuration and tests.
- **Rationale**: Simplifies consistency guarantees (every response reflects DB state), avoids cache warmups, and removes eviction/dedupe drift; aligns with FR-003 and SC-002.
- **Alternatives Considered**:
  - **Replace cache with Caffeine/other**: Still duplicates persistence state and reintroduces staleness edge cases.
  - **Retain cache only for hot paths**: Adds complexity without clear requirement; persistence already meets scale goals.

## Decision 3: API Contract Simplification
- **Decision**: Keep pagination fields (`events`, `nextPageToken`, `dataComplete`) but delete `cacheStatus` from `LogPageResponse` and document the change as a breaking contract update for internal consumers; rely on durable data completeness to explain progress.
- **Rationale**: FR-004 requires removing cache-specific metadata while still signaling completion, and SC-003 expects no trace of legacy types.
- **Alternatives Considered**:
  - **Return `cacheStatus` stub with static values**: Misleading to clients and invites future reliance.
  - **Move status to headers**: Still exposes meaningless data; easier to drop entirely and clarify docs.

## Decision 4: Legacy Configuration Guardrail
- **Decision**: Fail startup if `eventlog.retention.*` properties remain set, emitting a clear actionable error so ops teams remove stale config before the service runs.
- **Rationale**: Prevents false confidence that retention still occurs in-service and gives a single remediation path (FR-002).
- **Alternatives Considered**:
  - **Log a warning and continue**: Silent failures could leave teams believing retention still occurs.
  - **Auto-ignore properties**: Harder to detect misconfiguration, especially with IaC templates.
