# Daily Event Logging API

This repository hosts the implementation artifacts for the Daily Event Logging API feature (branch `001-event-log-api`). See `specs/001-event-log-api/` for the spec, plan, research, and tasks.

## Quickstart

Follow the detailed steps in `specs/001-event-log-api/quickstart.md`. In short:

```bash
git checkout 001-event-log-api
cd event-log-api
./mvnw spring-boot:run
```

Send a sample request:

```bash
curl -X POST http://localhost:8080/events \
  -H 'Content-Type: application/json' \
  -d '{
    "callerId": "client-1",
    "message": "Daily sync"
  }'
```

Override the timestamp explicitly:

```bash
curl -X POST http://localhost:8080/events \
  -H 'Content-Type: application/json' \
  -d '{
    "callerId": "client-1",
    "message": "Daily sync",
    "timestamp": "2026-03-24T08:00:00-07:00"
  }'
```

Refer to `specs/001-event-log-api/contracts/events-api.md` for the full API contract and expected console log shape.

## Log Retrieval Feature (branch `002-log-readback`)

See `specs/002-log-readback/` for the specification, plan, and runbook covering the transient log cache and GET surface.

### Cache configuration variables

| Variable | Default | Purpose |
|----------|---------|---------|
| `LOG_CACHE_CAPACITY` | `500` | Maximum number of entries stored in memory before evicting oldest records. |
| `LOG_CACHE_STALENESS_SECONDS` | `60` | Threshold for reporting the cache as `STALE` when no refresh occurs within this window. |
| `LOG_CACHE_MAX_PAGE_SIZE` | `200` | Absolute upper bound accepted by the GET endpoint's `limit` parameter. |

Values can be set via environment variables or overridden in `event-log-api/src/main/resources/application.yaml`.

### Fetching logs

```
curl "http://localhost:8080/events?limit=100"
```

Responses include `items`, `nextCursor`, `dataComplete`, and a `cacheStatus` block (state, lastRefresh, evictionCount, stalenessSeconds). Pass the `nextCursor` value back as `cursor` to continue paging. When `cacheStatus.state` reports `EMPTY`, `STALE`, or `TRUNCATED`, treat the data as potentially incomplete and follow the operational guidance in `specs/002-log-readback/quickstart.md`.
