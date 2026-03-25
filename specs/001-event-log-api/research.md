# Research Summary

## Decision 1: Spring Boot stack
- **Decision**: Build the service with Spring Boot 3.2.x on Java 21 using the `spring-boot-starter-web` dependency and Maven wrapper.
- **Rationale**: Aligns with the user's requirement (Java 21, Spring Boot) while taking advantage of the latest LTS features, native validation, and embedded server.
- **Alternatives considered**: Micronaut (lighter but deviates from requirement); Quarkus (fast startup but unnecessary for console-only MVP).

## Decision 2: Logging mechanism
- **Decision**: Use Spring's SLF4J logger to print a single structured line per request (key=value pairs) so console aggregators can parse it without extra storage.
- **Rationale**: Keeps the MVP lightweight yet debuggable; leverages existing logging infrastructure and satisfies the "print to console" acceptance criterion.
- **Alternatives considered**: `System.out.println` (too primitive, no log levels); JSON logger library (overkill for MVP, adds dependencies).

## Decision 3: Timestamp validation
- **Decision**: Accept ISO 8601 timestamps with timezone (e.g., `OffsetDateTime`) and reject ones more than 24 hours in the future; default to `Instant.now()` in UTC when absent.
- **Rationale**: Matches spec requirements (server timestamp default, optional override) while preventing unrealistic future-dated logs.
- **Alternatives considered**: Accepting epoch milliseconds (less readable); skipping future skew checks (higher risk of bad data).

## Decision 4: Layered design
- **Decision**: Split responsibilities into controller (HTTP + validation), service (timestamp resolution + logging), and model (request/response DTOs) without adding repositories.
- **Rationale**: Follows user's instruction to structure as controller/services and keeps future persistence integration straightforward.
- **Alternatives considered**: Monolithic controller (hurts testability); adding repository now (opposes "no database" constraint).
