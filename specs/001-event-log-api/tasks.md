# Tasks: Daily Event Logging API

**Input**: Design documents from `/specs/001-event-log-api/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Targeted unit/integration tests are called out per user story for critical behaviors (timestamp resolution, validation).

**Organization**: Tasks are grouped by user story so each slice can be delivered and tested independently.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Scaffold the Spring Boot project and baseline documentation.

- [x] T001 Generate Spring Boot 3.2 project with Java 21, `spring-boot-starter-web`, and validation starters in `event-log-api/pom.xml`.
- [x] T002 Add Maven Wrapper scripts and configure Java 21 toolchain in `event-log-api/.mvn/jvm.config` so builds run consistently.
- [x] T003 Document bootstrap and run commands from quickstart in root `README.md` referencing `event-log-api` module.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core shared components every story depends on (logging + shared DTOs/handlers).

- [x] T004 Create `ResolvedEvent` value object capturing callerId/message/metadata/timestamp/source/correlation in `event-log-api/src/main/java/com/example/eventlog/model/ResolvedEvent.java`.
- [x] T005 Configure structured logging pattern and UTC timezone defaults in `event-log-api/src/main/resources/application.yaml`.
- [x] T006 Implement global API exception & validation handler returning `EventResponse` payloads in `event-log-api/src/main/java/com/example/eventlog/config/ApiExceptionHandler.java`.

**Checkpoint**: Resolved event model, logging config, and error handling ready → user stories can start.

---

## Phase 3: User Story 1 - Submit Event With Default Timestamp (Priority: P1) 🎯 MVP

**Goal**: POST `/events` without timestamp stamps server time, logs once, and responds with success payload.
**Independent Test**: POST valid payload without timestamp → expect HTTP 200, resolved timestamp from server, and single console log line containing same timestamp/correlation id.

### Tests (US1)

- [x] T007 [P] [US1] Add `EventLogServiceTest` verifying default timestamp & log emission in `event-log-api/src/test/java/com/example/eventlog/service/EventLogServiceTest.java`.
- [x] T008 [P] [US1] Add MockMvc test covering POST `/events` without timestamp in `event-log-api/src/test/java/com/example/eventlog/controller/EventControllerTest.java`.

### Implementation (US1)

- [x] T009 [US1] Create `EventRequest` + `EventResponse` DTOs with validation annotations in `event-log-api/src/main/java/com/example/eventlog/model/EventRequest.java` and `EventResponse.java`.
- [x] T010 [US1] Implement `EventLogService` default timestamp resolution & SLF4J logging in `event-log-api/src/main/java/com/example/eventlog/service/EventLogService.java`.
- [x] T011 [US1] Build `EventController` POST `/events` endpoint wiring validation → service → response in `event-log-api/src/main/java/com/example/eventlog/controller/EventController.java`.

**Checkpoint**: User Story 1 delivers runnable MVP with server-side timestamps.

---

## Phase 4: User Story 2 - Override Timestamp (Priority: P2)

**Goal**: Allow clients to provide ISO 8601 timestamps (validated, max +24h) and surface errors without console logs on failure.
**Independent Test**: POST with valid timestamp → response/console show client value; POST with invalid/future timestamp → HTTP 400 with errors and no log entry.

### Tests (US2)

- [x] T012 [P] [US2] Extend service tests for client timestamp + future-limit validation in `event-log-api/src/test/java/com/example/eventlog/service/EventLogServiceTest.java`.
- [x] T013 [P] [US2] Add controller tests for invalid timestamp responses in `event-log-api/src/test/java/com/example/eventlog/controller/EventControllerTest.java`.

### Implementation (US2)

- [x] T014 [US2] Update `EventRequest` validation to parse ISO 8601 offsets and reject timestamps >24h ahead in `event-log-api/src/main/java/com/example/eventlog/model/EventRequest.java`.
- [x] T015 [US2] Enhance `EventLogService` to honor client timestamps (mark source) and skip logging when validation fails in `event-log-api/src/main/java/com/example/eventlog/service/EventLogService.java`.
- [x] T016 [US2] Update `EventController` to propagate validation errors via `ApiExceptionHandler` and ensure response timestamps mirror client input in `event-log-api/src/main/java/com/example/eventlog/controller/EventController.java`.

**Checkpoint**: Users can override timestamps safely; invalid inputs produce descriptive 400 responses.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Final quality touches shared by all stories.

- [x] T017 [P] Add README quickstart validation steps plus sample cURL payloads aligned with contracts in `README.md` and `specs/001-event-log-api/quickstart.md`.
- [x] T018 Verify console log format matches contract (`events-api.md`) via manual run notes in `specs/001-event-log-api/contracts/events-api.md`.
- [x] T019 [P] Add GitHub issue template or checklist referencing `specs/001-event-log-api/checklists/requirements.md` to ensure ongoing compliance.

---

## Dependencies & Execution Order

| Phase | Depends On |
|-------|------------|
| Setup | — |
| Foundational | Setup |
| US1 (P1) | Foundational |
| US2 (P2) | Foundational (can start after US1 core if shared files stabilized) |
| Polish | All targeted user stories |

**User Story Dependencies**
- US1 is MVP; no dependency on other stories.
- US2 builds on shared DTOs/service logic from US1 but can develop in parallel once core interfaces stabilize.

## Parallel Opportunities
- Tasks marked `[P]` may run concurrently (e.g., US1 tests vs. implementation in different files).
- Separate contributors can tackle US1 vs. US2 after foundational phase.
- Documentation polish tasks `[P]` can run while final validations occur.

## Implementation Strategy
- Ship MVP after Phase 3; validate via MockMvc + manual cURL.
- Layer US2 once MVP stable to avoid regression risk.
- Polish tasks ensure docs/checklists reflect final behavior before handoff.
