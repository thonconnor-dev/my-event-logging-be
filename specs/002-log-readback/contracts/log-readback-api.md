# Contract - Log Retrieval Endpoint

> **Status:** Superseded by `/specs/004-remove-retention-components/`. This contract documents the legacy cache behavior; see the new contract in `/specs/004-remove-retention-components/contracts/log-service-api.md` for the active interface.

## POST /events (existing)
- **Purpose**: Accept event submissions.
- **Body**: `EventRequest` (unchanged).
- **Side Effects**: After the event is resolved and logged, the payload is inserted/updated inside `TransientLogCache` using the derived log ID.

## GET /events/logs
- **Description**: Returns the latest log entries along with cache health metadata.
- **Query Parameters**:
  - `limit` (optional int, default 50, max 200) - page size.
  - `cursor` (optional string) - opaque base64 string containing the last log ID returned by the previous page.
- **Response 200 Body**:
```json
{
  "items": [
    {
      "id": "string",
      "callerId": "string",
      "message": "string",
      "metadata": {"string": "string"},
      "timestamp": "ISO-8601",
      "severity": "INFO|WARN|ERROR|DEBUG",
      "source": "CLIENT|SERVER",
      "correlationId": "string"
    }
  ],
  "nextCursor": "string|null",
  "dataComplete": true,
  "cacheStatus": {
    "state": "HEALTHY|EMPTY|STALE|TRUNCATED",
    "lastRefresh": "ISO-8601",
    "evictionCount": 0,
    "stalenessSeconds": 5
  }
}
```
- **Errors**:
  - `400 Bad Request` when `limit` or `cursor` fails validation.
  - `503 Service Unavailable` only when cache cannot initialize (should be rare; log and alert).

## Cache Semantics
- Cache capacity and stale threshold are configurable via properties (`log.cache.capacity`, `log.cache.staleness-seconds`).
- When capacity is exceeded, drop the oldest entry, increment `evictionCount`, set response `dataComplete=false`, and mark `state=TRUNCATED` for that request.
- When cache is empty, response `items` is an empty array and `state=EMPTY`.
- When `Duration.between(lastRefresh, now)` exceeds threshold, set `state=STALE` and include a warning message string in logs.

## Pagination Contract
- Cursor payload contains `{ "lastId": "<log-id>" }` encoded as base64; unknown/expired cursors fall back to the first page with `cursor` ignored and informational warning logged.
- Clients keep requesting until `nextCursor` is null; a null cursor indicates the page contains the most recent slice at request time.
