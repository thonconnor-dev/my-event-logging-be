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
