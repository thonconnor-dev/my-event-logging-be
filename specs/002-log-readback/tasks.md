# Tasks: Log Retrieval API with Transient Cache

**Input**: Design documents from `/specs/002-log-readback/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Add tests where specified (unit + MockMvc) to protect pagination, cache integrity, and health signaling.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Task can run in parallel (no dependency conflicts)
- **[Story]**: User story label (US1/US2/US3) for story phases
- Include exact file paths in every description

---

## Phase 1: Setup (Shared Infrastructure)

Purpose: Establish baseline configuration and documentation for the cache feature.

- [X] T001 Update `event-log-api/src/main/resources/application.yml` with `log.cache.capacity`, `log.cache.staleness-seconds`, and `log.cache.max-page-size` defaults plus env var bindings.
- [X] T002 Document new LOG_CACHE_* environment variables and GET `/events` usage notes in `README.md`.

---

## Phase 2: Foundational (Blocking Prerequisites)

Purpose: Build shared components required by every story before exposing the API.

- [X] T003 Create `LogCacheProperties` config binding and bean wiring in `event-log-api/src/main/java/com/example/eventlog/config/LogCacheProperties.java`.
- [X] T004 Implement deterministic ID helper `LogIdentityGenerator` in `event-log-api/src/main/java/com/example/eventlog/service/LogIdentityGenerator.java`.
- [X] T005 Implement bounded `TransientLogCache` with deque + map storage in `event-log-api/src/main/java/com/example/eventlog/service/TransientLogCache.java`.
- [X] T006 [P] Add cache behavior unit tests (insert, dedupe, eviction) in `event-log-api/src/test/java/com/example/eventlog/service/TransientLogCacheTest.java`.
- [X] T007 Implement cursor codec utility (encode/decode + validation) in `event-log-api/src/main/java/com/example/eventlog/service/LogCursorCodec.java`.

Checkpoint: Cache infrastructure ready; user stories can begin.

---

## Phase 3: User Story 1 - Retrieve Current Logs (Priority: P1) 🎯 MVP

Goal: Return a paginated, timestamp-ordered list of recent logs via GET `/events`.
Independent Test: Trigger sample POSTs, call GET `/events?limit=50`, verify ordered payload + pagination cursors without needing other stories.

### Implementation

- [X] T008 [P] [US1] Create response DTOs (`LogRecordResponse`, `LogPageResponse`) in `event-log-api/src/main/java/com/example/eventlog/model/LogRecordResponse.java`.
- [X] T009 [US1] Implement `LogQueryService` read path with ordering + pagination in `event-log-api/src/main/java/com/example/eventlog/service/LogQueryService.java`.
- [X] T010 [US1] Expose GET `/events` endpoint with `limit` and `cursor` params in `event-log-api/src/main/java/com/example/eventlog/controller/EventController.java`.
- [X] T011 [P] [US1] Add MockMvc tests for ordering, pagination, and default limits in `event-log-api/src/test/java/com/example/eventlog/controller/EventControllerGetLogsTest.java`.

Checkpoint: API returns ordered log slices independently (MVP complete).

---

## Phase 4: User Story 2 - Backfill Missing Logs (Priority: P2)

Goal: Guarantee every log in the response exists in memory by backfilling cache entries.
Independent Test: Start with empty cache, POST sample logs, fetch GET `/events`, confirm cache contains each returned entry and duplicates are suppressed.

### Implementation

- [X] T012 [US2] Extend `EventLogService` to insert each POSTed event into `TransientLogCache` in `event-log-api/src/main/java/com/example/eventlog/service/EventLogService.java`.
- [X] T013 [US2] Add cache backfill + dedupe enforcement inside `LogQueryService` before responding in `event-log-api/src/main/java/com/example/eventlog/service/LogQueryService.java`.
- [X] T014 [P] [US2] Add service-level tests covering backfill + dedupe in `event-log-api/src/test/java/com/example/eventlog/service/LogQueryServiceTest.java`.
- [X] T015 [P] [US2] Add MockMvc regression test ensuring repeated GETs reuse cached entries in `event-log-api/src/test/java/com/example/eventlog/controller/EventControllerGetLogsTest.java`.

Checkpoint: Cache stays synchronized with every response.

---

## Phase 5: User Story 3 - Monitor Cache Health (Priority: P3)

Goal: Provide cache health metadata (empty/stale/truncated) with each response for operator awareness.
Independent Test: Force empty, stale, and truncated cache states, call GET `/events`, verify `cacheStatus` block, `dataComplete` flag, and warning logs.

### Implementation

- [ ] T016 [US3] Implement cache health evaluation (state, eviction tracking) in `event-log-api/src/main/java/com/example/eventlog/service/TransientLogCache.java`.
- [ ] T017 [US3] Include `cacheStatus` + `dataComplete` fields in response DTOs and wiring in `event-log-api/src/main/java/com/example/eventlog/model/LogPageResponse.java`.
- [ ] T018 [P] [US3] Add tests covering empty/stale/truncated scenarios in `event-log-api/src/test/java/com/example/eventlog/service/CacheHealthTest.java`.
- [ ] T019 [P] [US3] Emit metrics/log warnings for unhealthy states inside `event-log-api/src/main/java/com/example/eventlog/service/LogQueryService.java`.

Checkpoint: Operators receive health feedback with every call.

---

## Phase 6: Polish & Cross-Cutting Concerns

Purpose: Finalize documentation and verification across stories.

- [ ] T020 [P] Refresh `specs/002-log-readback/quickstart.md` and `README.md` examples with pagination + health instructions.
- [ ] T021 Run full `./mvnw test` and record results/troubleshooting tips in `specs/002-log-readback/quickstart.md#Testing`.

---

## Dependencies & Execution Order

1. Setup must finish before foundational components compile (T001-T002).
2. Foundational tasks (T003-T007) block all user stories; they establish cache + cursor utilities.
3. User stories can proceed in priority order but US2/US3 depend on US1 artifacts:
   - US1 (T008-T011) delivers MVP read path.
   - US2 (T012-T015) builds on US1 services/controllers to guarantee cache completeness.
   - US3 (T016-T019) layers in health metadata after US1 data contracts exist.
4. Polish tasks (T020-T021) run after desired user stories are delivered.

## Parallel Execution Examples

- During Phase 2, T006 [P] and T007 can run while T004/T005 finish because they target separate files.
- In US1, DTO work (T008) and tests (T011) can run parallel once service contract is sketched.
- US2 test tasks (T014, T015) can proceed concurrently with T013 once method signatures stabilize.
- US3 health tests (T018) can run parallel to metrics instrumentation (T019).

## Implementation Strategy

1. Deliver MVP quickly by completing US1 (T008-T011) immediately after foundational work.
2. Harden correctness with US2 backfill + dedupe to keep cache authoritative.
3. Add operational insight via US3 health reporting.
4. Finish with documentation + verification (T020-T021) so support teams can rely on the new endpoint.
