# Quickstart: Persistent Event Log Storage

## 1. Prerequisites
- Java 21 (Temurin) and Maven Wrapper (`./mvnw`).
- SQLite available via the bundled Xerial driver (no external binary required).
- Write permissions to `event-log-api/target` and `event-log-api/event-log.db`.

## 2. Configure Database Location
Add (or confirm) the following properties in `event-log-api/src/main/resources/application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:sqlite:${EVENT_LOG_DB_PATH:./event-log.db}
    driver-class-name: org.sqlite.JDBC
  jpa:
    hibernate:
      ddl-auto: validate
    database-platform: com.example.eventlog.sqlite.SQLiteDialect
flyway:
  enabled: true
  locations: classpath:db/migration
```
Set `EVENT_LOG_DB_PATH` when running in different environments.

## 3. Run Migrations + Service
```bash
cd event-log-api
./mvnw spring-boot:run
```
Flyway runs automatically, creating the `event_records` table and indexes before the service accepts traffic.

## 4. POST an Event
```bash
curl -X POST http://localhost:8080/events \
  -H 'Content-Type: application/json' \
  -d '{
        "callerId": "support-tool",
        "message": "user login",
        "severity": "INFO",
        "timestamp": "2026-03-27T07:00:00Z",
        "metadata": {"userId": "123"}
      }'
```
Expected response:
```json
{
  "success": true,
  "eventId": "<uuid>",
  "resolvedTimestamp": "2026-03-27T07:00:00Z",
  "status": "LOGGED"
}
```

## 5. Retrieve Events
```bash
curl 'http://localhost:8080/events?pageSize=50&from=2026-03-26T00:00:00Z'
```
Response contains ordered entries sourced from SQLite plus pagination tokens and cache/persistence health metadata in headers (`X-Persistence-Status`).

## 6. Verify Persistence After Restart
1. Stop the Spring Boot process (`Ctrl+C`).
2. Restart via `./mvnw spring-boot:run`.
3. Re-run the GET request above; previously logged events remain because they are read from `event-log.db`, proving durability.

## 6. Observability & Maintenance
- `/actuator/health` now reports `persistence` status derived from `PersistenceHealthSnapshot`.
- A scheduled retention job deletes rows older than 30 days; adjust `eventlog.retention-days` property as needed.
- To inspect the database directly, open `event-log.db` with any SQLite client; schema is managed solely via Flyway migrations.
