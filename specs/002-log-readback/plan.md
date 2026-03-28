# Implementation Plan: Log Retrieval API with Transient Cache

> **Status:** Superseded by `/specs/004-remove-retention-components/`. The cache-specific implementation described here is historical—current work reads directly from SQLite.

**Branch**: `002-log-readback` | **Date**: 2026-03-25 | **Spec**: specs/002-log-readback/spec.md
**Input**: Feature specification from `/specs/002-log-readback/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

Introduce a read-only log retrieval surface on the existing Spring Boot service so operations teams can fetch recent log entries in a stable, paginated JSON payload. The plan adds an in-memory `TransientLogCache` backed by a bounded concurrent deque plus a hash index for deduplication, persists every POSTed event into that cache, and exposes a GET endpoint that backfills missing entries before returning data along with cache health metadata.

## Technical Context

**Language/Version**: Java 21 (Temurin)  
**Primary Dependencies**: Spring Boot 3.2.x (web + validation starters), Jackson (JSON), Lombok (optional)  
**Storage**: Bounded in-memory cache (ConcurrentLinkedDeque + LinkedHashMap) with configurable capacity (default 500)  
**Testing**: JUnit 5 with Spring Boot test slices + MockMvc  
**Target Platform**: Containerized Linux server hosting Spring Boot application  
**Project Type**: RESTful web service  
**Performance Goals**: Return <=500 records within 2s for 95% of requests; sustain <5% stale-cache warnings  
**Constraints**: Keep heap impact <10 MB for cache, enforce pagination + eviction when over capacity, deduplicate incoming records  
**Scale/Scope**: Internal observability clients (support + SRE) with <=5k log entries/day during pilot

## Constitution Check

- The constitution file currently contains only placeholders and defines no enforceable principles. Default organizational practices (single service, test-first, documentation) are already satisfied by this plan.
- No gating violations identified -> **PASS** pre-design; the same holds after Phase 1 because the design still fits a single Spring Boot service with automated tests.

## Project Structure

### Documentation (this feature)

```text
specs/002-log-readback/
|-- spec.md
|-- plan.md
|-- research.md
|-- data-model.md
|-- quickstart.md
|-- contracts/
|   `-- log-readback-api.md
|-- checklists/
|   `-- requirements.md
`-- tasks.md   # (created during /speckit.tasks)
```

### Source Code (repository root)

```text
event-log-api/
|-- pom.xml
|-- src/
|   |-- main/java/com/example/eventlog/
|   |   |-- config/
|   |   |-- controller/
|   |   |-- model/
|   |   `-- service/
|   `-- test/java/com/example/eventlog/
|-- target/
`-- mvnw{,.cmd}
```

**Structure Decision**: Continue enhancing the single Spring Boot project (`event-log-api`). New cache, controller DTOs, and services will live under `com.example.eventlog` with associated unit + MockMvc tests in the matching `src/test/java` tree.

## Complexity Tracking

No constitution violations detected; additional complexity tracker entries are unnecessary at this stage.
