# Quickstart

1. **Install prerequisites**
   - JDK 21 (Temurin recommended)
   - Maven 3.9+ (or use Maven Wrapper once project scaffolded)

2. **Clone & checkout feature branch**
```bash
git checkout 001-event-log-api
```

3. **Generate Spring Boot project skeleton** (first implementation task)
```bash
mvn -B archetype:generate \
  -DgroupId=com.example \
  -DartifactId=event-log-api \
  -DarchetypeArtifactId=maven-archetype-quickstart \
  -DarchetypeVersion=1.4
```
*(Replace with Spring Initializr download if preferred; ensure Java 21 + Spring Boot 3.2 settings.)*

4. **Add Spring Boot dependencies** in `event-log-api/pom.xml`
   - `spring-boot-starter-web`
   - `spring-boot-starter-validation`
   - `spring-boot-starter-test` (test scope)
   - Optional: `lombok`

5. **Run the service**
```bash
cd event-log-api
./mvnw spring-boot:run
```

6. **cURL example**
```bash
curl -X POST http://localhost:8080/events \
  -H 'Content-Type: application/json' \
  -d '{
    "callerId": "client-1",
    "message": "Daily sync",
    "timestamp": "2026-03-24T08:00:00-07:00"
  }'
```
Expected console log: `eventlog callerId=client-1 status=logged ...`

7. **Client timestamp example**
```bash
curl -X POST http://localhost:8080/events \
  -H 'Content-Type: application/json' \
  -d '{
    "callerId": "client-1",
    "message": "Daily sync from device",
    "timestamp": "2026-03-24T08:00:00-07:00"
  }'
```
Verify the console line uses the provided timestamp and shows `source=client`.

8. **Tests**
```bash
./mvnw test
```

9. **Shut down**
- Ctrl+C the running Spring Boot process.
