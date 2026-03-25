# Quickstart - Log Retrieval API with Transient Cache

## 1. Boot the API locally
1. Navigate to `event-log-api`.
2. Start the Spring Boot app: `./mvnw spring-boot:run` (requires JDK 21).
3. Override cache tuning as needed:
   - `LOG_CACHE_CAPACITY` (int, default 500)
   - `LOG_CACHE_STALENESS_SECONDS` (int, default 60)
   - `LOG_CACHE_MAX_PAGE_SIZE` (int, default 200)
   Provide them via `application.yml` or environment variables.

## 2. Publish events (existing POST)
```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
        "callerId": "billing-worker",
        "message": "invoice posted",
        "metadata": {"severity": "INFO"}
      }'
```
Every accepted event is written to logs and appended to the new transient cache.

## 3. Read logs via new endpoint
```bash
curl "http://localhost:8080/events/logs?limit=100"
```
Sample response structure:
```json
{
  "items": [
    {
      "id": "2f8c...",
      "callerId": "billing-worker",
      "message": "invoice posted",
      "metadata": {"severity": "INFO"},
      "timestamp": "2026-03-25T19:30:11Z",
      "severity": "INFO",
      "source": "SERVER",
      "correlationId": "0f7c..."
    }
  ],
  "nextCursor": "eyJpZCI6ICIyZjhjLi4uIn0=",
  "dataComplete": true,
  "cacheStatus": {
    "state": "HEALTHY",
    "lastRefresh": "2026-03-25T19:30:11Z",
    "evictionCount": 0,
    "stalenessSeconds": 5
  }
}
```
- Use `cursor` query param from `nextCursor` to fetch the next slice.
- When `cacheStatus.state` is `EMPTY` or `STALE`, alerting/UX should indicate data may be incomplete.

## 4. Testing checklist
1. `./mvnw test` -> ensures unit + MockMvc suites (new GET + cache tests) pass.
2. Exercise pagination manually: request small limit (e.g., 2) repeatedly and confirm cursors progress and eventually return `nextCursor=null`.
3. Simulate cache truncation by lowering `LOG_CACHE_CAPACITY` to 5, POST 10 events, then GET logs; response should include `state=TRUNCATED` and `dataComplete=false`.
