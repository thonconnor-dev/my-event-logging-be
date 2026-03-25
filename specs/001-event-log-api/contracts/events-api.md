# Events API Contract

## Endpoint: POST /events
- **Description**: Accepts a single event payload, resolves timestamp, logs to console, and returns acknowledgement.
- **Authentication**: Deferred/disabled for MVP (trusted network).
- **Headers**:
  - `Content-Type: application/json`
  - `X-Correlation-Id` (optional; UUID supplied by caller). Generated server-side if absent.

### Request Body
```json
{
  "callerId": "client-123",
  "message": "Daily summary",
  "metadata": {
    "source": "mobile",
    "priority": "info"
  },
  "timestamp": "2026-03-24T09:15:00-07:00"
}
```

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `callerId` | string | Yes | <=64 chars, `[A-Za-z0-9_-]+` |
| `message` | string | Yes | <=512 chars, trimmed, cannot be blank |
| `metadata` | object | No | Up to 10 key/value pairs, value <=128 chars |
| `timestamp` | string | No | ISO 8601 with offset; if missing server time used |

### Success Response (200)
```json
{
  "success": true,
  "status": "logged",
  "timestamp": "2026-03-24T16:15:00Z",
  "correlationId": "f4d2e5ae-0f58-4e4e-9c86-2ad8a915df65"
}
```

### Validation Error Response (400)
```json
{
  "success": false,
  "status": "validation_error",
  "timestamp": "2026-03-24T16:15:00Z",
  "correlationId": "f4d2e5ae-0f58-4e4e-9c86-2ad8a915df65",
  "errors": [
    "timestamp must be ISO 8601",
    "message may not be blank"
  ]
}
```

### Console Log Line (Informational)
```
eventlog callerId=client-123 status=logged timestamp=2026-03-24T16:15:00Z corr=f4d2e5ae-0f58-4e4e-9c86-2ad8a915df65 message="Daily summary"
```

### Failure Modes
- **422**: Reserved for future semantic validation (not used in MVP).
- **500**: Unexpected server error; payload NOT logged if response is 5xx.

### Rate & Throughput
- Target ≤50 RPS sustained; apply lightweight rate limiting at ingress (if needed) before controller.

## Manual Verification Notes
1. Run the service with `./mvnw spring-boot:run` from `event-log-api/`.
2. POST a payload using the sample in the Quickstart.
3. Confirm the console line matches the pattern above, including `callerId`, `status=logged`, `timestamp=<value>`, `corr=<uuid>`, and, when provided, `source=client`.
