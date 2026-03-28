# Contract - Log Service Updates (Retention/Cache Removal)

## GET /events
- **Purpose**: Return paginated log records sourced exclusively from the persistent store.
- **Query Parameters** (unchanged):
  - `from` / `to` ISO-8601 timestamps (optional) defaulting to last 30 days span.
  - `pageSize` integer (default 50, max 500).
  - `pageToken` opaque cursor encoded by `LogCursorCodec` (id + resolvedTimestamp).
- **Response 200 Body**:
```json
{
  "events": [
    {
      "id": "string",
      "callerId": "string",
      "message": "string",
      "metadata": {"key": "value"},
      "timestamp": "ISO-8601",
      "severity": "INFO|WARN|ERROR|DEBUG",
      "source": "CLIENT|SERVER",
      "correlationId": "string"
    }
  ],
  "nextPageToken": "string or null",
  "dataComplete": true
}
```
- **Notable Change**: The `cacheStatus` object is deleted. Clients must infer completeness solely from `dataComplete` and continuation tokens. Any reference to cache state must be removed in downstream tooling.
- **Errors**:
  - `400 Bad Request` if `from > to`, `pageSize` invalid, or `pageToken` corrupt/expired.
  - `503 Service Unavailable` if the repository encounters a database outage (no cache fallback exists).

### Automation & Monitoring Guidance
- Treat `dataComplete=false` as an instruction to continue paging with the returned `nextPageToken` until either `dataComplete=true` or `nextPageToken` is `null`.
- Alerting that previously watched cache health must instead track:
  - Repeated `dataComplete=false` responses for the same time window (signals the caller is not keeping up).
  - GET latency/service availability via existing HTTP metrics.
- Clients should remove any parsing of `cacheStatus` fields and rely exclusively on `dataComplete` plus pagination tokens to know whether data is complete.

## POST /events (unchanged behavior)
- Still persists payloads directly to SQLite via `EventRecordRepository`.
- No longer mirrors entries into `TransientLogCache`; acknowledgement response is unaffected.

## Startup Validation Failure
When legacy retention configuration is detected the service **fails fast** during bootstrap:
- **Condition**: Any property matching `eventlog.retention.*` is supplied (e.g., `eventlog.retention.days`).
- **Outcome**: Application throws `IllegalStateException` with message `Remove obsolete eventlog.retention.* settings; retention now handled externally.` and terminates with non-zero exit code.
- **Contract**: Deployment automation must treat this as a blocking configuration error; removing the properties restores normal startup.

## Monitoring Expectations
- `/actuator` endpoints no longer mention cache health—only persistence-related indicators remain.
- Dashboards that previously surfaced cache staleness must switch to tracking DB availability and the `dataComplete` ratio described above.
