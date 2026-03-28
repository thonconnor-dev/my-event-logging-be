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

See `specs/002-log-readback/` for the original cache-based design. That cache has now been retired—reads are served directly from SQLite using deterministic pagination.

### Fetching logs

```
curl "http://localhost:8080/events?pageSize=100"
```

Responses now include only `events`, `nextPageToken`, and `dataComplete`. Keep paging with the returned `nextPageToken` until either `dataComplete=true` or `nextPageToken` is `null`. Any legacy tooling that previously inspected `cacheStatus` must switch to monitoring the `dataComplete` flag and HTTP latency metrics instead (see `specs/004-remove-retention-components/contracts/log-service-api.md`).

## Retention & Cache Removal (branch `004-remove-retention-components`)

`specs/004-remove-retention-components/` documents the work to delete the `RetentionJob`, block `eventlog.retention.*` configuration at startup, and remove `TransientLogCache`. Operators must remove all `eventlog.retention.*` settings before upgrading; the service now fails fast if those keys are present. See the quickstart in the same directory for validating the new behavior (baseline build, GET pagination, `dataComplete` monitoring, and release approval checklist).
