# Feature Specification: Persistent Event Log Storage

**Feature Branch**: `003-db-event-log`  
**Created**: March 27, 2026  
**Status**: Draft  
**Input**: User description: "Implement storing event log in database instead of memory. Make sure to change code in services class to read and write into database."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Durably Store Incoming Events (Priority: P1)

An API client submits an event through POST `/events` and expects the platform to persist the payload—including metadata and resolved timestamp—in a durable datastore before acknowledging success so that the record survives restarts and supports future analytics.

**Why this priority**: Without durable storage, logged events disappear when the service restarts, undermining regulatory and operational needs; persistence is therefore the most critical upgrade.  
**Independent Test**: Trigger a POST, restart the service, then query the datastore to verify the event persists and matches the response payload.

**Acceptance Scenarios**:

1. **Given** the datastore is reachable, **When** a valid event is posted, **Then** the service writes a unique record with caller id, timestamp, message, metadata, and status, and only after the write succeeds does it return HTTP 200 with the persisted identifier.  
2. **Given** the datastore write fails, **When** an event is posted, **Then** the service returns a clear 5xx error, does not claim success, and logs diagnostics for operators.

---

### User Story 2 - Query Full Event History (Priority: P2)

Operations staff need GET `/events` to return the authoritative event timeline from the datastore (not transient memory) so they can review past incidents, paginate through high-volume periods, and filter by time range without missing entries.

**Why this priority**: Reading from the same persistent source guarantees analysts see every stored event, even after cache eviction or restart.  
**Independent Test**: Seed multiple events directly in the datastore, call GET `/events` with pagination and time filters, and confirm results match the stored records and order.

**Acceptance Scenarios**:

1. **Given** at least 200 events exist over the past 24 hours, **When** an operator requests GET `/events?from=<t1>&to=<t2>&pageSize=50`, **Then** the service reads exclusively from the datastore, returns a chronologically sorted page, and exposes a continuation token for the next slice.  
2. **Given** an event was persisted more than 24 hours prior, **When** the operator queries for an older time range, **Then** the record remains retrievable regardless of previous in-memory cache evictions.

---

### User Story 3 - Monitor Persistence Health (Priority: P3)

As a support engineer, I want visibility when persistence lags or the database is degraded so I can take corrective action and avoid assuming logs were stored when they were not.

**Why this priority**: Durability only helps if failures are surfaced; proactive health reporting prevents silent data loss.  
**Independent Test**: Simulate a database outage, invoke POST and GET operations, and verify that health indicators in responses and metrics alert operators without corrupting stored data.

**Acceptance Scenarios**:

1. **Given** the persistence layer is slow or unreachable, **When** POST `/events` is called, **Then** the service rejects the request with a retriable error, emits a persistence health flag, and queues no partial writes.  
2. **Given** the datastore recovers, **When** the service resumes processing, **Then** it updates the health indicator to “healthy” and continues writing new events without manual intervention.

### Edge Cases

- Database connection drops mid-transaction; service must roll back and report the failure without duplicating writes.  
- Event payloads arrive faster than the datastore SLA; batching or throttling logic must protect the database while preserving ordering guarantees.  
- Duplicate submissions with the same external identifier must not result in multiple stored records.  
- Legacy in-memory cache contains entries not yet persisted; deployment must flush or migrate them so no data disappears during cutover.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: POST `/events` MUST persist each accepted event into the authoritative datastore, capturing caller id, message, metadata blob, severity, resolved timestamp, and a generated persistence identifier.  
- **FR-002**: The service MUST acknowledge success to clients only after the datastore confirms the write; partial writes or asynchronous confirmations are not allowed for this release.  
- **FR-003**: GET `/events` MUST read from the datastore as the single source of truth, applying pagination and time-window filters while guaranteeing chronological ordering.  
- **FR-004**: Existing in-memory cache (if retained for hot reads) MUST be treated as a read-through cache backed by the datastore, and it MUST automatically repopulate from the database after restarts without data loss.  
- **FR-005**: The system MUST expose health signals (response headers, metrics, and/or status endpoint) indicating whether persistence operations are succeeding, degraded, or failing, so operators can act quickly.  
- **FR-006**: The service MUST enforce a retention policy of 30 days (configurable) by expiring or archiving older records without impacting more recent queries.  
- **FR-007**: Duplicate detection MUST prevent storing multiple rows for identical `callerId + timestamp + message` combinations within a five-second window, returning HTTP 200 with the original persistence identifier instead.  
- **FR-008**: During deployment, any events still resident only in memory MUST be flushed into the datastore exactly once so that no historical log entries are lost in the transition.

### Key Entities *(include if feature involves data)*

- **EventRecord**: Represents the durable log entry with attributes for eventId, callerId, severity, payload, metadata, timestamps (client-provided and server-received), and retention expiration date.  
- **PersistenceHealthSnapshot**: Aggregates recent write/read success rates, latency percentiles, and last error timestamp to power health indicators and dashboards.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 99% of write operations acknowledge within 400 ms while guaranteeing the event is durable and queryable afterward.  
- **SC-002**: 100% of events retrievable via GET `/events` survive process restarts and cache resets across a 30-day retention window.  
- **SC-003**: Under nominal load (≤100 writes per second), no more than 0.1% of requests fail due to persistence errors in a 24-hour period.  
- **SC-004**: Persistence health telemetry generates an alert within 60 seconds of consecutive failures so operators can intervene before backlog exceeds 500 events.

## Assumptions

- A managed relational datastore with automatic backups already exists and credentials are managed outside this feature.  
- API surface area (endpoints and payload formats) remains unchanged; only the storage backing evolves.  
- Existing observability tooling can ingest new persistence health metrics without additional work.  
- Retention requirements beyond 30 days will be addressed in a later archival effort and are explicitly out of scope here.
