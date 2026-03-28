# Feature Specification: Retire Retention Job and Transient Cache

**Feature Branch**: `004-remove-retention-components`  
**Created**: March 27, 2026  
**Status**: Draft  
**Input**: User description: "remove RetentionJob, TransientLogCache and any references include unit test"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Operate Without Background Purge (Priority: P1)

Platform owners need the event log service to stop running the RetentionJob because retention is now handled directly by the database, eliminating duplicated deletes and surprise load.

**Why this priority**: Leaving the job in place risks deleting data twice or at the wrong cadence, which creates compliance exposure.

**Independent Test**: Stand up the service with standard configuration, inspect the scheduled tasks/health page, and verify that no cleanup job exists while historical data still follows the database retention window.

**Acceptance Scenarios**:

1. **Given** the service is deployed with baseline configuration, **When** operations inspect scheduled tasks, **Then** there are zero background jobs referencing event retention and no cron configuration is required.
2. **Given** an environment that still sets legacy retention properties, **When** the service starts, **Then** it fails fast with an explicit message instructing operators to remove the obsolete settings so no one mistakenly expects an internal purge.

---

### User Story 2 - Retrieve Logs Without Cache Semantics (Priority: P2)

Operations analysts need to query recent logs and receive consistent, paginated data sourced solely from the durable store so that restarts or cache warmups do not change the response.

**Why this priority**: Removing the cache only works if the GET contract still provides predictable data to humans and automation.

**Independent Test**: Insert sample events, restart the service, and call the log retrieval API twice with identical filters; both responses must return the same records and metadata even though the in-memory cache no longer exists.

**Acceptance Scenarios**:

1. **Given** events are stored and the service is restarted, **When** a client calls the log retrieval API with the same filters before and after the restart, **Then** the result count, order, and pagination tokens are identical.
2. **Given** a client previously read cache health metadata, **When** they call the API after this change, **Then** the response excludes cache-specific fields yet still communicates completion state so the client contract remains clear.

---

### User Story 3 - Tooling Reflects the Simplified Architecture (Priority: P3)

Developers and QA engineers need the build, tests, and documentation to stop referencing the removed components so that maintenance and onboarding effort drops.

**Why this priority**: Outdated tests or docs create confusion and mask regressions when the cache/job no longer exists.

**Independent Test**: Run the full CI pipeline and static checks; no unit, integration, or documentation artifacts mention the removed job or cache, and all suites remain green.

**Acceptance Scenarios**:

1. **Given** the codebase is searched for the legacy component names, **When** CI runs, **Then** no references remain outside of historical specs/changelogs.
2. **Given** contribution guides and runbooks, **When** a new engineer follows them, **Then** there are no steps about seeding or observing the cache/job and quality gates still pass.

---

### Edge Cases

- Legacy deployments might still ship `eventlog.retention.*` properties; startup must surface a single, actionable error instead of silently ignoring them.
- Automation that parsed cache health metadata needs a clear alternative signal (e.g., rely on `dataComplete`), so contract docs must flag the removal ahead of release.
- If the log retrieval API receives overlapping pagination tokens created before the change, the service must tolerate them without referencing cache state.
- Service restarts occurring during long-running reads should not lose events because persistence now acts as the only source of truth.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The service must no longer register or execute any background retention job; deployments succeed without retention-specific cron configuration.
- **FR-002**: Startup validation must detect deprecated retention configuration and halt launch with guidance so operators clean up obsolete settings before proceeding.
- **FR-003**: Log ingestion and retrieval flows must interact directly with the persistent event store without relying on any in-memory `TransientLogCache` behavior (dedupe, eviction, staleness tracking).
- **FR-004**: Public API responses must remove cache-specific metadata while preserving pagination tokens, completion indicators, and record payloads so external clients remain compatible.
- **FR-005**: Quality gates (unit, integration, contract tests, linters) must be updated to eliminate references to the removed components and to verify the direct-to-database behavior outlined above.
- **FR-006**: Release notes and runbooks must call out the architectural simplification, explicitly stating that data retention is enforced by database policies or downstream tooling rather than the service itself.

### Key Entities *(include if feature involves data)*

- **EventRecord**: The persisted log entry that represents the single source of truth for all reads after the cache is removed; contains caller identifiers, message, structured metadata, timestamps, severity, and correlation identifiers.
- **Log Retrieval Request**: A client-provided filter (time window, severity, pagination token) that determines which `EventRecord` rows are streamed back directly from storage and how pagination cursors are generated.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: `/actuator` or equivalent scheduled-task inspection reports zero background jobs related to retention across all environments after deployment.
- **SC-002**: Contract tests that issue identical log retrieval queries before and after a controlled restart observe 100% identical record counts and pagination cursors, proving there is no dependency on cache warmup.
- **SC-003**: CI search plus unit/integration suites confirm zero references to `RetentionJob` or `TransientLogCache` outside of historical documentation, and the pipeline remains fully green.
- **SC-004**: Release communication and runbooks are updated and approved by operations leadership, acknowledging that retention responsibility moved to the database/on-call team with no remaining service-level mechanisms.

## Assumptions

- Database-level retention (or downstream data processing) already enforces the necessary deletion policy, so removing the internal job will not violate compliance commitments.
- No external client contract legally depends on cache health metadata; they only require pagination and completion indicators, which remain in place.
- Deployments can tolerate a startup failure that calls out deprecated retention settings because environments are managed via infrastructure as code.
- Operational teams agree that a cold service should still answer read requests directly from storage without performance compromises that would have justified keeping the cache.
