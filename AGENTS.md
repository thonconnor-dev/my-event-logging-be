# be Development Guidelines

Auto-generated from all feature plans. Last updated: 2026-03-27

## Active Technologies
- Java 21 (Temurin) + Spring Boot 3.2.x (web + validation starters), Jackson (JSON), Lombok (optional) (002-log-readback)
- Bounded in-memory cache (ConcurrentLinkedDeque + LinkedHashMap) with configurable capacity (default 500) (002-log-readback)
- Java 21 (Temurin) + Spring Boot 3.2.x (web, validation), Spring Data JPA (Hibernate), SQLite JDBC driver (Xerial), Flyway for schema managemen (003-db-event-log)
- File-backed SQLite database (`event-log.db`) managed via JPA entities + Flyway migrations (003-db-event-log)

- Java 21 (Temurin) + Spring Boot 3.2.x (web starter), Spring Validation, Lombok (optional), Jackson (bundled) (001-event-log-api)

## Project Structure

```text
src/
tests/
```

## Commands

# Add commands for Java 21 (Temurin)

## Code Style

Java 21 (Temurin): Follow standard conventions

## Recent Changes
- 003-db-event-log: Added Java 21 (Temurin) + Spring Boot 3.2.x (web, validation), Spring Data JPA (Hibernate), SQLite JDBC driver (Xerial), Flyway for schema managemen
- 002-log-readback: Added Java 21 (Temurin) + Spring Boot 3.2.x (web + validation starters), Jackson (JSON), Lombok (optional)

- 001-event-log-api: Added Java 21 (Temurin) + Spring Boot 3.2.x (web starter), Spring Validation, Lombok (optional), Jackson (bundled)

<!-- MANUAL ADDITIONS START -->
<!-- MANUAL ADDITIONS END -->
