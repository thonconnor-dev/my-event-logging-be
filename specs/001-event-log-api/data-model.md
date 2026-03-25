# Data Model

## Entity: EventRequest
- **Fields**:
  - `callerId` (string, required, <=64 chars)
  - `message` (string, required, <=512 chars)
  - `metadata` (map<string,string>, optional, max 10 pairs)
  - `timestamp` (ISO 8601 string, optional, timezone required)
- **Validation Rules**:
  - `callerId` must match `[A-Za-z0-9_-]+`
  - `message` cannot be blank and trims whitespace
  - `timestamp`, when provided, must parse as `OffsetDateTime` and be no more than 24 hours in the future relative to server time
  - Reject payloads missing `callerId` or `message`
- **Relationships**: Consumed directly by the controller and converted into an `ResolvedEvent` value object for service logic.

## Value Object: ResolvedEvent
- **Fields**:
  - `callerId`
  - `message`
  - `metadata`
  - `resolvedTimestamp` (Instant)
  - `source` (`CLIENT` when user provided timestamp, otherwise `SERVER`)
  - `correlationId` (UUID generated per request)
- **Purpose**: Internal representation that the service logs; never persisted beyond request scope.

## Entity: EventResponse
- **Fields**:
  - `success` (boolean)
  - `timestamp` (ISO 8601 string matching resolved timestamp)
  - `status` (`logged` or `validation_error`)
  - `correlationId`
  - `errors` (list of strings, only present on validation failures)
- **Behavior**: Controller returns HTTP 200 with populated `EventResponse` on success; HTTP 400 with `success=false` for validation errors.

## LogLine Format
- **Template**: `eventlog callerId=<id> timestamp=<iso> status=<status> corr=<uuid> message="<trimmed message>"`
- **Emission**: Produced by `EventLogService` via SLF4J `info` level; errors logged at `warn` level with validation details.
