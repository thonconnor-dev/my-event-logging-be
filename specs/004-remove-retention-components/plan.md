# Implementation Plan: Retire Retention Job and Transient Cache

**Branch**: `004-remove-retention-components` | **Date**: March 27, 2026 | **Spec**: `/specs/004-remove-retention-components/spec.md`
**Input**: Feature specification for removing `RetentionJob`, `TransientLogCache`, and every code/config/test reference.

**Note**: Generated via `/speckit.plan` based on `.specify/templates/plan-template.md`.

## Summary

Decommission the in-service data retention scheduler and the `TransientLogCache` so the Spring Boot event log API exclusively relies on SQLite persistence for both deletes and reads. Workstreams: rip out scheduler beans/config, enforce startup validation that blocks obsolete retention properties, refactor read/write services plus controllers to bypass the cache, reshape the GET `/events` response contract to drop cache health metadata while keeping pagination semantics, and scrub all build/tests/docs to ensure zero references to the removed components.

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: Java 21 (Temurin)  
**Primary Dependencies**: Spring Boot 3.2.x (web, validation, scheduling), Spring Data JPA (Hibernate), Jackson, SQLite JDBC, Flyway  
**Storage**: File-backed SQLite database (`event-log.db`) managed through JPA + Flyway  
**Testing**: JUnit 5 + Spring Boot Test (MockMvc + @DataJpaTest) with Mockito for isolated service tests  
**Target Platform**: Containerized Linux service deployable via Maven wrapper (`./mvnw spring-boot:run`)  
**Project Type**: REST web service  
**Performance Goals**: GET `/events` returns ≤500 rows in under 2 seconds without cache warmup; writes continue to acknowledge within 400 ms; startup validation completes in <5 seconds  
**Constraints**: Must run with zero background schedulers for retention, rely solely on database TTL policies, and preserve existing API pagination contracts despite model changes  
**Scale/Scope**: ≤100 sustained writes/sec, ≤150k stored rows (30-day horizon), dozens of concurrent readers primarily via automation scripts

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

`.specify/memory/constitution.md` still contains only placeholder headings, so no enforceable principles or gates exist. Documenting that absence here; proceed while flagging that a future constitution update should encode real guidelines (e.g., test-first, service limits).

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
event-log-api/
├── pom.xml
├── src/
│   ├── main/java/com/example/eventlog/
│   │   ├── config/
│   │   ├── controller/
│   │   ├── model/
│   │   ├── repository/
│   │   └── service/
│   ├── main/resources/
│   │   ├── application.yml
│   │   └── db/migration/
│   └── test/java/com/example/eventlog/
└── mvnw (+ wrapper files)

specs/
├── 001-event-log-api/
├── 002-log-readback/
├── 003-db-event-log/
└── 004-remove-retention-components/
    ├── spec.md
    └── plan.md (plus research/data-model/quickstart/contracts to be added)
```

**Structure Decision**: Single Spring Boot service (`event-log-api`) continues to host all runtime code; this feature removes classes/config/tests under `service/` and `config/` plus adjusts `controller/` and `model/` packages while updating the associated docs in `specs/004-remove-retention-components/`.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| _None_ | – | – |
