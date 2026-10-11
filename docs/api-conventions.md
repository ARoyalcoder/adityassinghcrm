# API conventions

Every endpoint follows these rules. `ApiErrorContractTest` pins the error format.

## URLs

- Base path: `/api/v1`. Build mappings from `ApiPaths.V1`.
- Resources are plural nouns in kebab-case: `/api/v1/service-verticals`, `/api/v1/service-requests`.
- Nesting only for true ownership, one level deep: `/leads/{leadId}/notes`.
- Actions that are not CRUD are sub-resources named by a verb: `POST /quotes/{id}/approve`.
- Identifiers in paths are opaque to clients.

## Methods and status codes

| Method | Use | Success |
| --- | --- | --- |
| `GET` | Read. Never changes state | `200` |
| `POST` | Create, or run an action | `201` + `Location` header for create; `200`/`204` for actions |
| `PUT` | Replace the whole resource | `200` |
| `PATCH` | Change some fields | `200` |
| `DELETE` | Remove | `204` |

## Request and response bodies

- JSON, `camelCase` property names, UTF-8.
- Success responses return the resource directly, with no `{ "data": ... }` envelope.
- Timestamps are ISO-8601 in UTC (`2026-10-05T14:20:43Z`). Dates without a time are `YYYY-MM-DD`.
- Money is a decimal `amount` plus an ISO-4217 `currency`; never a floating-point number.
- Enums are `UPPER_SNAKE_CASE` strings. Clients must tolerate new values appearing.
- Absent optional fields are omitted or `null`; clients treat both the same.
- Controllers accept and return DTO records, never JPA entities. Validate request DTOs with Bean
  Validation and `@Valid`.

## Collections

Paginated collections return `PageResponse`:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalItems": 137,
  "totalPages": 7
}
```

- Query parameters: `page` (zero-based), `size` (default 20, maximum 100), `sort=field,asc|desc`.
  Build the `Pageable` with `Paging.of(...)`, passing the fields the endpoint allows sorting by;
  anything else is `400 MALFORMED_REQUEST`.
- Free-text search is the `search` parameter (case-insensitive "contains"); say which fields it covers
  in the operation summary.
- Optional booleans in request bodies are `Boolean`, not `boolean`: a missing primitive is rejected.
- Filters are plain query parameters named after the field: `?status=OPEN&vertical=SOLAR`.
- Small, fixed reference lists (such as `/service-verticals`) may return a bare array; say so in the
  operation summary.

## Errors

Every error, from any layer, has this body with `Content-Type: application/problem+json`
(RFC 9457 with extensions):

```json
{
  "type": "urn:bos:error:validation-failed",
  "title": "Validation failed",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "detail": "One or more fields are invalid",
  "instance": "/api/v1/leads",
  "requestId": "0b6c2a7e-6c1d-4a1e-9a55-0c3f3f1f6f10",
  "timestamp": "2026-10-05T14:20:45.323Z",
  "errors": [
    { "field": "phone", "message": "must not be blank" }
  ]
}
```

| Field | Meaning |
| --- | --- |
| `code` | Stable machine-readable code. **Clients branch on this**, never on `detail` |
| `title` | Fixed text for the code |
| `detail` | Human-readable explanation of this occurrence; safe to show to users |
| `instance` | Path of the request |
| `requestId` | Same value as the `X-Request-Id` response header and the server logs |
| `errors` | Only for `VALIDATION_FAILED`: one entry per invalid field |

### Error codes

| Code | HTTP | When |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | A field failed validation; see `errors` |
| `MALFORMED_REQUEST` | 400 | Unreadable JSON, wrong parameter type, missing parameter |
| `UNAUTHENTICATED` | 401 | Missing, invalid or expired access token, or the session has ended |
| `INVALID_CREDENTIALS` | 401 | Sign-in failed (wrong email, wrong password or locked account; deliberately not distinguished) |
| `FORBIDDEN` | 403 | Authenticated but lacking the required permission |
| `ACCOUNT_INACTIVE` | 403 | Correct password, but the account is switched off |
| `RESOURCE_NOT_FOUND` | 404 | Unknown resource or route |
| `METHOD_NOT_ALLOWED` | 405 | |
| `NOT_ACCEPTABLE` | 406 | |
| `CONFLICT` | 409 | Duplicate, or the resource changed since it was read |
| `POSSIBLE_DUPLICATE` | 409 | The record looks like an existing one. Show the user the matches; resend with `confirmDuplicates: true` to save anyway |
| `PAYLOAD_TOO_LARGE` | 413 | |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | |
| `BUSINESS_RULE_VIOLATION` | 422 | Well-formed request that a domain rule forbids |
| `RATE_LIMITED` | 429 | |
| `INTERNAL_ERROR` | 500 | Bug or unexpected failure. `detail` is generic; the cause is only in the logs |
| `SERVICE_UNAVAILABLE` | 503 | A dependency is down |

Codes are append-only: never rename or reuse one.

### Raising errors in code

Throw an `ApiException` subclass from a service; never build an error body in a controller.

```java
throw new ResourceNotFoundException("Lead", leadId);                    // 404
throw new ConflictException("A lead with this phone number exists");    // 409
throw new BusinessRuleException("A closed deal cannot be reopened");    // 422
```

Messages passed to these exceptions are returned to the client, so they must not contain internal
details. Anything else that escapes a controller becomes `INTERNAL_ERROR` with a generic message.

## Authentication and authorisation

Full description: [security.md](security.md).

- The API is stateless. Requests carry `Authorization: Bearer <access token>`.
- Every endpoint requires a valid token unless it is listed in `SecurityConfig`
  (`PUBLIC_GET_ENDPOINTS`, `PUBLIC_AUTH_ENDPOINTS`).
- What a user may do is checked per method with `@PreAuthorize("hasAuthority('MODULE_ACTION')")`.
- `401` means "we do not know who you are" (the client should refresh or sign in);
  `403` means "we know who you are and you may not do this" (retrying will not help).
- A resource that exists but belongs to another organization is `404`, not `403`.

## Headers

| Header | Direction | Purpose |
| --- | --- | --- |
| `X-Request-Id` | both | Correlation id. Sent back on every response; a client-supplied value is kept if it is 8-64 characters of `A-Z a-z 0-9 . _ -` |
| `Location` | response | URL of a resource created by `POST` |

## Concurrency

Mutable resources carry a `version`. Updates that send a stale version are rejected with
`409 CONFLICT`; the client reloads and retries.

## Documentation

Annotate controllers with `@Tag` and `@Operation`. The OpenAPI document is at `/v3/api-docs` and the
UI at `/swagger-ui.html` when `API_DOCS_ENABLED=true` (on by default in the `local` profile, off in `prod`).

## Versioning

Additive changes (new endpoints, new optional fields, new enum values) stay in `v1`. Removing or
renaming a field, changing a type, or changing the meaning of a status code needs `/api/v2`.
