# Quickstart: Retire Retention Job and Transient Cache

## 1. Prerequisites
- Java 21 (Temurin) with Maven Wrapper available (`./mvnw`).
- Existing SQLite database initialized via prior migrations.
- Ability to edit deployment properties/IaC templates that formerly set `eventlog.retention.*`.

## 2. Remove Legacy Retention Configuration
Delete any properties like the following from `event-log-api/src/main/resources/application.yml` or environment variables before upgrading:
```yaml
# REMOVE THESE
# eventlog:
#   retention:
#     cron: "0 0 2 * * *"
#     days: 30
```
Attempting to start the service with these properties now fails fast with an actionable message.

## 3. Build & Run Without Retention Job
```bash
cd event-log-api
./mvnw spring-boot:run
```
Startup logs should **not** list `RetentionJob` in the scheduled task registry. If the app exits complaining about legacy retention settings, fix configuration and retry.

## 4. Verify GET /events Contract
1. POST two events (as before) to `/events`.
2. Restart the service to ensure caches are cold.
3. Invoke (first page will set `dataComplete=false` while more rows exist):
```bash
curl 'http://localhost:8080/events?pageSize=50'
```
4. Re-issue the request with the returned `nextPageToken` until it becomes `null`. `dataComplete` should flip to `true` on the final page, demonstrating the new automation signal.

## 5. Confirm Monitoring Expectations
- `/actuator/health` exposes persistence indicators only; no cache metrics remain.
- Dashboards that previously tracked cache evictions/staleness must now monitor:
  - GET latency and error ratios from the REST endpoint.
  - Persistent `dataComplete=false` responses for the same time window (page queue keeps growing).

## 6. Regression Checklist
- Search the repository for `RetentionJob` and `TransientLogCache`; only historical specs should match.
- Run `./mvnw test`; suites previously covering the job/cache should be removed or rewritten to hit repository-based behavior.
