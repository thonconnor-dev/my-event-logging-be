# Research: Persistent Event Log Storage

## Decision 1: SQLite via Spring Data JPA (Hibernate)
- **Decision**: Use Spring Data JPA with Hibernate plus the Xerial SQLite JDBC driver configured through `spring.jpa.database-platform=org.hibernate.dialect.SQLiteDialect` (custom dialect bean) so entities map directly to a local SQLite file.
- **Rationale**: Aligns with user directive to "implement it with Spring JPA" while letting us keep the existing Spring Boot stack and repository abstractions; SQLite keeps deployment lightweight yet provides durability beyond the transient cache.
- **Alternatives Considered**:
  - **H2 in file mode**: Simpler Spring integration but contradicts explicit SQLite requirement and complicates future parity between local and production.
  - **PostgreSQL container**: More scalable but exceeds MVP scope and adds infrastructure the user did not request.

## Decision 2: Schema Management via Flyway
- **Decision**: Introduce Flyway migrations under `src/main/resources/db/migration` to create and evolve the EventRecord table plus indexes (timestamp, callerId) and pruning helpers.
- **Rationale**: Flyway already meshes with Spring Boot starters, gives deterministic, version-controlled schema updates, and can execute during startup before the application accepts traffic.
- **Alternatives Considered**:
  - **Hibernate `ddl-auto`**: Quick but unsafe for production (risk of destructive diffs, no reviewable scripts).
  - **Manual SQL scripts**: Harder to automate and validate during CI/CD.

## Decision 3: Duplicate Detection & Retention Strategy
- **Decision**: Enforce a unique constraint on `(caller_id, resolved_timestamp, message_hash)` and let the service catch `DataIntegrityViolationException` to return idempotent success; schedule a nightly job (Spring `@Scheduled`) to delete rows older than 30 days.
- **Rationale**: Keeps duplicate handling inside the database, honoring FR-007, while a scheduled deletion honors FR-006 without manual intervention.
- **Alternatives Considered**:
  - **Application-level dedupe cache**: Adds memory pressure and restarts would forget prior entries.
  - **Batch archival service**: Overkill for MVP scale; we only need deletion today.

## Decision 4: Persistence Health Monitoring
- **Decision**: Add an `@Component` that records recent persistence failures/success counts (e.g., Micrometer counter + health indicator) and expose the flag via response headers plus `/actuator/health` customization.
- **Rationale**: Meets FR-005/SC-004 with minimal overhead by piggybacking on existing Spring Actuator patterns.
- **Alternatives Considered**:
  - **External monitoring agent**: Too heavy for MVP and outside app control.
  - **Database-specific watchdog**: SQLite lacks built-in monitoring hooks; embedding health logic in the service is simpler.
