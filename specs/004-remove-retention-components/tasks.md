# Tasks: Retire Retention Job and Transient Cache

**Input**: Design documents from `/specs/004-remove-retention-components/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

## Phase 1: Setup (Shared Infrastructure)

- [X] T001 Run `./mvnw -f event-log-api/pom.xml verify` to capture the current green baseline before removing RetentionJob/TransientLogCache.

---

## Phase 2: Foundational (Blocking Prerequisites)

- [X] T002 [P] Create `event-log-api/src/test/java/com/example/eventlog/support/EventRecordFixtures.java` to centralize builders for persisted `EventRecordEntity` rows reused across service/controller tests.
- [X] T003 [P] Add repository pagination regression test in `event-log-api/src/test/java/com/example/eventlog/repository/EventRecordRepositoryIT.java` that loads fixture rows and asserts deterministic ordering directly from SQLite.

**Checkpoint**: Shared fixtures and DB regression tests exist so all stories can build on a single source of truth.

---

## Phase 3: User Story 1 - Operate Without Background Purge (Priority: P1) 🎯 MVP

**Goal**: Remove the in-service retention scheduler and block legacy configuration so operators rely solely on database retention.

**Independent Test**: Boot with and without `eventlog.retention.*` properties; startup must fail fast when legacy settings exist and succeed otherwise, with `/actuator` showing zero scheduled jobs.

### Implementation

- [X] T004 [US1] Remove `@EnableScheduling`, delete `event-log-api/src/main/java/com/example/eventlog/service/RetentionJob.java`, and strip any scheduler wiring from `event-log-api/src/main/java/com/example/eventlog/EventLogApiApplication.java`.
- [X] T005 [US1] Drop `RetentionProperties` (delete `event-log-api/src/main/java/com/example/eventlog/config/RetentionProperties.java`), compute `expiresAt` directly inside `event-log-api/src/main/java/com/example/eventlog/service/EventWriteService.java`, and refresh `event-log-api/src/test/java/com/example/eventlog/service/EventWriteServiceTest.java` accordingly.
- [X] T006 [US1] Remove the `eventlog.retention.*` block from `event-log-api/src/main/resources/application.yaml` and scrub any sample config/env docs referencing those keys.
- [X] T007 [US1] Implement `event-log-api/src/main/java/com/example/eventlog/config/LegacyRetentionConfigValidator.java` that inspects `Environment` for `eventlog.retention.*` properties and raises a descriptive startup exception.
- [X] T008 [US1] Add unit tests in `event-log-api/src/test/java/com/example/eventlog/config/LegacyRetentionConfigValidatorTest.java` covering both failure (legacy properties present) and success paths.

**Checkpoint**: Service boots without scheduling, legacy configs are rejected, and persistence still sets retention metadata internally.

---

## Phase 4: User Story 2 - Retrieve Logs Without Cache Semantics (Priority: P2)

**Goal**: Serve log reads/writes directly from SQLite, eliminating `TransientLogCache` and providing a documented automation signal via `dataComplete`.

**Independent Test**: Insert sample events, restart the service, and call `GET /events` with identical filters before/after restart—responses must match exactly, omit cache metadata, and expose consistent `dataComplete` values.

### Implementation

- [X] T009 [US2] Delete `event-log-api/src/main/java/com/example/eventlog/service/TransientLogCache.java` and `event-log-api/src/main/java/com/example/eventlog/config/LogCacheProperties.java`, remove bean registration, and clean up related imports.
- [X] T010 [US2] Refactor `event-log-api/src/main/java/com/example/eventlog/service/EventWriteService.java` to drop cache interactions (ensure `ensurePresent/upsert` calls disappear) and update `event-log-api/src/test/java/com/example/eventlog/service/EventWriteServiceTest.java` to assert repository-only behavior.
- [X] T011 [US2] Rework `event-log-api/src/main/java/com/example/eventlog/service/EventReadService.java` to page fully from `EventRecordRepository`, remove `buildStatus`, and ensure pagination cursors rely solely on DB state.
- [X] T012 [US2] Update DTOs by removing cache metadata (`event-log-api/src/main/java/com/example/eventlog/model/CacheStatusResponse.java`, `CacheState`, and field usage in `LogPageResponse`), and adjust `event-log-api/src/main/java/com/example/eventlog/controller/EventController.java` serialization to match the lean response.
- [X] T013 [US2] Expand read-path tests (service + controller) to assert deterministic ordering and verify that `dataComplete` reflects whether additional pages exist after restarts.
- [X] T014 [US2] Delete cache-specific unit tests (`event-log-api/src/test/java/com/example/eventlog/service/TransientLogCacheTest.java` and any helpers) and run `rg` to ensure runtime code only references the database path.
- [X] T015 [US2] Update `specs/004-remove-retention-components/contracts/log-service-api.md` (and any downstream client samples) to explicitly instruct automation to rely on `dataComplete`/pagination tokens as the replacement signal for the old `cacheStatus`; document expected monitoring steps.

**Checkpoint**: GET/POST flows operate solely against SQLite, pagination tokens and `dataComplete` provide the automation signal, and no code references cache types.

---

## Phase 5: User Story 3 - Tooling Reflects the Simplified Architecture (Priority: P3)

**Goal**: Ensure documentation, runbooks, and CI tooling mention only the DB-backed behavior while preserving historical specs for reference.

**Independent Test**: A new engineer following README/quickstart/specs finds zero instructions about RetentionJob/TransientLogCache outside clearly labeled historical sections, and CI blocks regressions if old names reappear in active code/docs.

### Implementation

- [X] T016 [US3] Update `README.md` (and any runbook sections under `docs/` if present) to describe DB-only retention, the startup validator, and the new automation guidance.
- [X] T017 [US3] Annotate living documentation in `specs/002-log-readback/*.md` to mark the cache behavior as deprecated without rewriting historical context; add forward references to the new DB-only approach.
- [X] T018 [US3] Refresh `specs/004-remove-retention-components/quickstart.md` and operator checklists to emphasize removing obsolete config and verifying `dataComplete` instead of cache health.
- [X] T019 [US3] Add or update a CI guard script/checklist entry (e.g., `scripts/check-no-legacy-cache.sh`) that fails if `rg` finds `RetentionJob` or `TransientLogCache` outside historical spec folders; document the check in `specs/004-remove-retention-components/checklists/requirements.md`.

**Checkpoint**: Repository guidance and safeguards steer contributors away from reintroducing removed components while retaining historical references.

---

## Phase 6: Polish & Cross-Cutting

- [X] T020 Run the full regression suite via `./mvnw -f event-log-api/pom.xml verify` and capture artifacts/logs for release communication.
- [X] T021 Document the release in `docs/RELEASE_NOTES.md` (or similar) and record explicit approval from operations leadership confirming SC-004 acceptance.

---

## Dependencies & Execution Order

1. **Phase 1 → Phase 2**: Baseline verification precedes creating shared fixtures/tests.
2. **Phase 2 → User Stories**: Fixtures/tests from Phase 2 unblock user-story work; all stories depend on them.
3. **User Story Order**: Complete US1 (scheduler removal) before US2 (cache removal) to avoid double edits; US3 depends on final code shape from US1/US2.
4. **Polish**: Final verification and approval after user stories land.

## Parallel Opportunities

- `[P]` tasks (T002, T003) can run concurrently once the baseline build passes.
- After Phase 2, US1 and US2 tasks touching different files (e.g., validator vs. EventReadService) can run in parallel with coordination.
- Documentation/CI guard tasks in US3 can execute in parallel once corresponding code changes are merged.

## Implementation Strategy

- **MVP Scope**: Deliver Phase 3 (US1) first so production immediately stops running the retention job and rejects legacy configs.
- **Incremental Delivery**: Ship US2 next to remove cache semantics and solidify the new automation signal, then US3 to align tooling/docs; each story remains independently testable.
- **Testing Focus**: Prioritize repository/service + controller tests grounded in the new fixtures to prove DB-only behavior before removing components; re-run the full suite (T020) before final approval/sign-off (T021).
