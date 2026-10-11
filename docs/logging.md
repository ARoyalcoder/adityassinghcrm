# Logging strategy

## Goals

1. Any error a user reports can be traced to its server log lines from one value: the request id.
2. Logs are machine-parseable in deployed environments and readable on a developer machine.
3. Logs never contain secrets or personal data.

## Request correlation

`RequestCorrelationFilter` runs first on every request and:

- takes `X-Request-Id` from the caller (Nginx sets it) or generates a UUID. A caller-supplied value
  is only accepted if it is 8-64 characters of `A-Z a-z 0-9 . _ -`, which prevents log injection;
- puts it in the logging context (MDC key `requestId`), so **every** log line written while handling
  the request carries it;
- returns it in the `X-Request-Id` response header and in the `requestId` field of error bodies;
- writes one access-log line per request on the `bos.access` logger:
  `GET /api/v1/service-verticals -> 200 (12 ms)`. Actuator calls are not logged.

The frontend shows the id as "Reference" on error states, so a user can quote it.

## Format

| Profile | Output |
| --- | --- |
| `local`, `test` | Human-readable console lines |
| `prod` | One JSON object per line (Elastic Common Schema, `logging.structured.format.console: ecs`) on stdout |

The application only writes to stdout. Collecting, shipping and retaining logs is the platform's job
(Docker logging driver or a log agent), not the application's.

## Levels

| Level | Use for | Example |
| --- | --- | --- |
| `ERROR` | A request or job failed because of a bug or a broken dependency. Someone should look | Unhandled exception, storage unreachable |
| `WARN` | Unexpected but handled; worth noticing if it repeats | Data conflict, retry succeeded, slow external call |
| `INFO` | Business-significant events and lifecycle | "Quote 4812 approved", job started/finished, application started |
| `DEBUG` | Detail for diagnosing; off in production | Rejected request and why |

Client mistakes (4xx) are **not** errors: validation failures and not-found are logged at `DEBUG`.
Unexpected exceptions are logged once, with the stack trace, by `GlobalExceptionHandler`. Do not
log-and-rethrow.

Levels are set with `APP_LOG_LEVEL` (application code) and can be changed per logger with
`LOGGING_LEVEL_<LOGGER>` environment variables.

## Writing log statements

```java
private static final Logger log = LoggerFactory.getLogger(QuoteService.class);

log.info("Quote approved: quoteId={} approvedBy={}", quote.getId(), actorId);
```

- Use SLF4J with `{}` placeholders; no string concatenation.
- Log identifiers, not objects: `leadId=42`, not the lead.
- Use `key=value` pairs for anything someone will search for.
- Pass the exception as the last argument to keep the stack trace: `log.error("Sync failed: jobId={}", id, ex)`.

## Never log

- Passwords, tokens, API keys, `Authorization` headers, presigned URLs.
- Personal data: phone numbers, email addresses, postal addresses, government ids, payment details.
- Full request or response bodies.

## Metrics and health

- `/actuator/health` (with `/liveness` and `/readiness`) for orchestrators and the Compose healthcheck.
- `/actuator/prometheus` for metrics scraping (JVM, HTTP server timings, connection pool, cache).
- Nginx returns 404 for `/actuator` from outside; these endpoints are reachable on the internal network only.

## Audit trail is not logging

Who changed what and when is business data and belongs in PostgreSQL (`created_by`, `updated_by`,
timestamps on `AuditableEntity`, and dedicated audit tables when a module needs history). Logs are
for operating the system and may be dropped or rotated.
