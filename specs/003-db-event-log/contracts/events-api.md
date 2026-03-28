# Contract: Event Logging REST API

## POST /events
- **Purpose**: Accept event payloads, validate, persist via SQLite, and return persistence metadata.
- **Request**
  - Headers: `Content-Type: application/json`
  - Body schema:
    | Field | Type | Required | Notes |
    |-------|------|----------|-------|
    | `callerId` | string | Yes | Identifier of emitting system (max 128 chars).
    | `message` | string | Yes | Event text (max 4 KB before JSON encoding).
    | `severity` | string | No | `INFO` (default), `WARN`, `ERROR`.
    | `timestamp` | string (ISO 8601) | No | Client event timestamp; must be within +24h future window.
    | `metadata` | object | No | Arbitrary JSON metadata; persisted as JSON string.
  - Validation failures return HTTP 400 with `errors[]` array.
- **Responses**
  - `200 OK` success payload:
    ```json
    {
      "success": true,
      "eventId": "1f9c...",
      "resolvedTimestamp": "2026-03-27T07:00:00Z",
      "status": "LOGGED"
    }
    ```
    Headers include `X-Persistence-Status: healthy|degraded|failed`.
  - `409 Conflict` when duplicate detected (returns `success: true` and existing `eventId`).
  - `5xx` when database unavailable, plus `Retry-After` guidance.

## GET /events
- **Purpose**: Provide ordered event history backed by SQLite, supporting pagination + time filters.
- **Query Parameters**
  | Name | Type | Default | Notes |
  |------|------|---------|-------|
  | `from` | ISO timestamp | 30 days ago | Inclusive lower bound on `resolvedTimestamp`.
  | `to` | ISO timestamp | now | Exclusive upper bound.
  | `pageSize` | integer | 50 | Max 500. |
  | `pageToken` | string | null | Continuation token from prior response. |
- **Responses**
  - `200 OK`:
    ```json
    {
      "events": [
        {
          "eventId": "1f9c...",
          "callerId": "support-tool",
          "severity": "INFO",
          "message": "user login",
          "metadata": {"userId": "123"},
          "resolvedTimestamp": "2026-03-27T07:00:00Z"
        }
      ],
      "nextPageToken": "eyJyZXNvbHZlZCI6I...",
      "cacheStatus": "backed-by-db"
    }
    ```
    Headers: `X-Persistence-Status`, `X-Retention-Expires-After`.
  - Empty `events` array when no records in range; still HTTP 200 with cache health metadata.

## Persistence Health Surfacing
- `X-Persistence-Status` header on both endpoints, values: `healthy`, `degraded`, `failed`.
- `/actuator/health` includes component `persistence` with same status plus details from `PersistenceHealthSnapshot`.
