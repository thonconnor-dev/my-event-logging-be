# Tasks: Persistent Event Log Storage

**Input**: Design documents from `/specs/003-db-event-log/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

## Phase 1: Setup (Shared Infrastructure)

Purpose: Prepare repository for SQLite-backed persistence work.

- [X] T001 Add SQLite + Flyway dependencies and Spring Data JPA starter in `event-log-api/pom.xml`
- [X] T002 Configure application defaults for SQLite datasource, Hibernate dialect bean, and Flyway locations in `event-log-api/src/main/resources/application.yml`
- [X] T003 [P] Create `event-log-api/src/main/java/com/example/eventlog/sqlite/SQLiteDialect.java` extending Hibernate dialect for SQLite support

---

## Phase 2: Foundational (Blocking Prerequisites)

Purpose: Core persistence scaffolding that every story depends on.

- [X] T004 Create Flyway baseline migration `event-log-api/src/main/resources/db/migration/V1__event_records.sql` with table, indexes, and unique constraint
- [X] T005 [P] Implement `EventRecordEntity` + JPA mapping in `event-log-api/src/main/java/com/example/eventlog/model/EventRecordEntity.java`
- [X] T006 [P] Add `EventRecordRepository` interface in `event-log-api/src/main/java/com/example/eventlog/repository/EventRecordRepository.java` with paging + dedupe queries
- [X] T007 Wire persistence retention scheduler skeleton in `event-log-api/src/main/java/com/example/eventlog/service/RetentionJob.java`
- [X] T008 Establish transactional `PersistenceService` base class in `event-log-api/src/main/java/com/example/eventlog/service/PersistenceService.java`

Checkpoint: Database schema, entities, repository, and retention hooks ready.

---

## Phase 3: User Story 1 – Durably Store Incoming Events (Priority: P1) 🎯 MVP

Goal: POST `/events` persists events durably and acknowledges only after success.
Independent Test: POST event, restart service, query DB to confirm record remains and matches response payload.

### Implementation Tasks
- [X] T009 [P] [US1] Map controller DTO -> entity conversion logic in `event-log-api/src/main/java/com/example/eventlog/controller/EventController.java`
- [X] T010 [US1] Implement `EventWriteService` to validate, hash, dedupe, and persist events in `event-log-api/src/main/java/com/example/eventlog/service/EventWriteService.java`
- [X] T011 [US1] Update POST `/events` handler to call `EventWriteService`, return persisted id, and surface persistence errors in `event-log-api/src/main/java/com/example/eventlog/controller/EventController.java`
- [X] T012 [US1] Add duplicate detection handling (catch `DataIntegrityViolationException`) returning prior id in `event-log-api/src/main/java/com/example/eventlog/service/EventWriteService.java`
- [X] T013 [US1] Add integration test covering POST success, duplicate, and DB failure cases in `event-log-api/src/test/java/com/example/eventlog/controller/EventControllerIntegrationTest.java`
- [X] T014 [US1] Document POST flow updates + restart verification in `specs/003-db-event-log/quickstart.md`

---

## Phase 4: User Story 2 – Query Full Event History (Priority: P2)

Goal: GET `/events` reads exclusively from SQLite with pagination + filters.
Independent Test: Seed DB with ≥200 events, call GET with range + page size, confirm chronological order and pagination tokens.

### Implementation Tasks
- [X] T015 [P] [US2] Implement paging query + projections in `EventRecordRepository` for GET filters in `event-log-api/src/main/java/com/example/eventlog/repository/EventRecordRepository.java`
- [X] T016 [US2] Create `EventReadService` that builds queries, maps entities to response DTOs, and handles page tokens in `event-log-api/src/main/java/com/example/eventlog/service/EventReadService.java`
- [X] T017 [US2] Update GET `/events` controller to use `EventReadService`, add cacheStatus + continuation token fields in `event-log-api/src/main/java/com/example/eventlog/controller/EventController.java`
- [X] T018 [US2] Implement pagination + date filter integration tests using seeded SQLite DB in `event-log-api/src/test/java/com/example/eventlog/controller/EventControllerIntegrationTest.java`
- [X] T019 [US2] Remove or adapt legacy in-memory cache references, ensuring cache now read-throughs from DB via `event-log-api/src/main/java/com/example/eventlog/service/EventReadService.java`

---

## Phase 5: User Story 3 – Monitor Persistence Health (Priority: P3)

Goal: Surface health signals when persistence degrades and expose via headers + actuator.
Independent Test: Simulate SQLite outage, assert POST/GET emit `X-Persistence-Status`, actuator reports degraded, and recovery flips status back.

### Implementation Tasks
- [ ] T020 [P] [US3] Implement `PersistenceHealthTracker` component recording recent successes/failures in `event-log-api/src/main/java/com/example/eventlog/health/PersistenceHealthTracker.java`
- [ ] T021 [US3] Inject tracker into write/read services to update status + set response headers in `event-log-api/src/main/java/com/example/eventlog/service/EventWriteService.java` and `EventReadService.java`
- [ ] T022 [US3] Expose custom Spring Boot actuator health indicator in `event-log-api/src/main/java/com/example/eventlog/health/PersistenceHealthIndicator.java`
- [ ] T023 [US3] Add integration test simulating DB outage + recovery verifying headers and actuator output in `event-log-api/src/test/java/com/example/eventlog/health/PersistenceHealthIntegrationTest.java`

---

## Phase 6: Polish & Cross-Cutting

- [ ] T024 [P] Update documentation and diagrams: `specs/003-db-event-log/quickstart.md`, `specs/003-db-event-log/contracts/events-api.md`
- [ ] T025 Add retention job scheduling + configuration wiring in `event-log-api/src/main/java/com/example/eventlog/config/RetentionConfig.java`
- [ ] T026 Run end-to-end smoke test: start service, POST events, restart, GET history, verify retention job metrics (`event-log-api/`)

---

## Dependencies & Execution Order

1. Phase 1 → Phase 2 → Phase 3 (US1) → Phase 4 (US2) → Phase 5 (US3) → Phase 6.
2. User stories can run in parallel once foundational Phase 2 completes, but US1 is recommended first for MVP delivery.
3. Integration tests align with each story’s completion; run after implementation tasks in the same phase.

## Parallel Opportunities

- T003, T005, T006, T015, T020 marked [P] can be tackled simultaneously once prerequisites met.
- Separate engineers can own US1/US2/US3 after foundational work; coordination required only on shared controller/service files.

## MVP Scope

- Completing Phase 3 (US1) delivers durable writes with restart-proof storage, satisfying the MVP requirement.

## Implementation Strategy

- Deliver incrementally: finish Setup + Foundational, ship US1 as MVP, then iterate on US2 and US3 once durability is live.
- Maintain backward compatibility by gating new persistence features behind feature flags until DB migration verified.
- Keep migrations idempotent and version-controlled via Flyway to support CI and local dev.
