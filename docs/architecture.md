# Architecture

## Shape of the system

```
Browser ──> Nginx ──┬── static SPA (React build)
                    └── /api/* ──> Spring Boot (one process) ──┬── PostgreSQL  (source of truth)
                                                               ├── Redis       (cache; safe to lose)
                                                               └── MinIO / S3  (file bytes)
```

One backend deployable, one database, one frontend. No microservices.

## Decisions

| Decision | Why |
| --- | --- |
| **Modular monolith** | One team and one product. A single deployable keeps transactions, debugging and releases simple, while enforced module boundaries keep the option to split later. |
| **Package-by-feature** | Code that changes together lives together. A module is a top-level package, not a layer spread across `controller/`, `service/`, `repository/`. |
| **PostgreSQL is the source of truth** | Every durable fact lives in PostgreSQL. Redis holds only derived data that can be rebuilt; object storage holds only bytes whose metadata is in PostgreSQL. |
| **Flyway owns the schema** | Hibernate runs with `ddl-auto: validate`: it checks the mapping against the schema at start-up and never changes it. Every schema change is a reviewed, versioned SQL file. |
| **Stateless REST API under `/api/v1`** | No server sessions, so instances can be added behind Nginx without sticky routing. The version is in the path so a breaking `/api/v2` can run alongside. |
| **Deny by default** | Every endpoint needs a valid access token unless it is on the short allow-list in `SecurityConfig`. A new endpoint is private until someone deliberately makes it public. |
| **Backend-enforced, permission-based access** | Short-lived JWT access tokens plus rotating, revocable refresh tokens. Code checks `MODULE_ACTION` permissions; roles are data that bundle them. The UI hides what a user cannot do but enforces nothing. See [security.md](security.md). |
| **One error format** | All failures, including ones raised by Spring Security and the framework, return the same `application/problem+json` body with a stable `code`. See [api-conventions.md](api-conventions.md). |
| **Audit by declaration** | An entity marked `@Audited` has every create, update, delete and status change recorded with before/after values, in the same transaction, by a Hibernate listener. No module writes audit code in its controllers or services. See [audit.md](audit.md). |
| **Configuration-driven catalog** | What differs between services (billing, price, unit, site visit, duration) is data that administrators edit. No controller or service branches on a vertical or service code. See [configuration.md](configuration.md). |
| **Java 21 language level** | The build targets release 21 whatever JDK runs it; the container image uses a Java 21 runtime. |

## Backend package structure

Base package: `com.pawanputra.bos`

```
platform/            Shared kernel. Knows nothing about any business module.
  config/              Clock, scheduling, cache, OpenAPI
  error/               ErrorCode + ApiException hierarchy
  event/               DomainEvent + DomainEventPublisher (the seam between modules)
  logging/             Request correlation id + access log
  persistence/         Entity base classes (BaseEntity, AuditableEntity, SoftDeletableEntity), JPA auditing
  security/            Security filter chain, JWT signing/verification, CurrentUser, CORS
  storage/             ObjectStorage port + S3 implementation
  timeline/            TimelineEvent, categories and event types; merging and cursor paging of events from many sources
  web/                 Error response, exception handler, PageResponse, Paging, ApiPaths

system/              Public build/version info
identity/            Organizations, branches, users, roles, permissions; sign-in, sessions, passwords
catalog/             Service verticals, categories and services (what the company sells); see configuration.md
                     Configurable per-service lead fields and their stored answers; see custom-fields.md
audit/               Audit log and record activity, written automatically for @Audited entities; see audit.md
project/             Projects, and the project template of each service (stages, schedule, planning a project
                     from it); see project-templates.md
fieldops/            Field jobs and site visits: scheduling, the technician workflow, sign-off; see field-operations.md
lead/                Leads, their activity log, qualification questions and the scoring engine; see leads.md
customer/            Customers with contacts, addresses, tags, notes and interactions; duplicate detection;
                     the Customer 360 (timeline, health, summary); see customers.md
<module>/            Every business module follows the same shape:
  api/                 What OTHER modules may use: enums, ids, service interfaces, event records
  internal/            Entities, repositories, services. Invisible to other modules
  web/                 Controllers and request/response DTOs
```

### Module rules (enforced by `ArchitectureTest`)

1. `platform` never depends on a business module.
2. A module's `internal` and `web` packages are used only inside that module.
   Other modules use its `api` package or react to its domain events.
3. No dependency cycles between modules.
4. `@RestController` classes live in a `web` package.
5. `@Entity` classes live in an `internal` package: entities never cross a module boundary and are
   never returned from a controller (controllers map them to DTO records).

A rule failure breaks the build. Fix the dependency, not the rule.

### The six verticals

`catalog.api.ServiceVerticalCode` is the single definition of the company's verticals: a closed
enum of six, mirrored by a `CHECK` constraint on the `service_verticals` table and by
`BUSINESS_VERTICAL_CODES` in the frontend. Any module that is scoped to a vertical references this
enum. Changing the set requires a migration, the enum and the frontend constant together; tests on
both sides pin the list.

## Extracting a service later

Notification, integration, search and reporting are the likely candidates. The design keeps that
cheap without paying for it now:

- **Modules talk through `api` packages and domain events only.** An extracted module's `api`
  interface gets an HTTP-client implementation; callers do not change.
- **Events are plain records of ids and values** published through `DomainEventPublisher`. Today
  delivery is in-process (consumers use `@TransactionalEventListener(AFTER_COMMIT)`). To extract a
  consumer, replace the publisher's delivery with a transactional outbox table plus a broker; event
  producers do not change.
- **No cross-module joins or foreign keys to another module's tables.** Modules reference each
  other by id. That keeps each module's tables movable to their own schema or database. (This is
  why organizations, branches and users live together in `identity`: they are joined constantly.)
- **Side effects sit behind ports** (`ObjectStorage` today; mail/SMS/WhatsApp senders, search index
  and report generation later), so an implementation can move out of process behind the same interface.

What is deliberately **not** built yet: a message broker, an outbox, a search engine, separate
schemas per module. Each is added when a real requirement appears.

## Frontend structure

```
src/
  app/               Composition root: router, QueryClient, providers
  components/
    ui/                shadcn/ui primitives (owned source, restyle here)
    data-table/        The one table component (TanStack Table)
    feedback/          Error and empty states
    layout/            App shell, navigation
  config/            Navigation registry
  features/<name>/   One folder per feature: api.ts (schemas + queries), components/, pages/
  lib/               api client, env validation, utilities
  pages/             Pages that belong to no feature (404)
```

Rules:

- All HTTP goes through `lib/api/client.ts`. It validates every response with a Zod schema and turns
  every failure into an `ApiError` carrying the server's `code` and `requestId`.
- Server state lives in TanStack Query, never copied into component state. Query definitions sit in
  the feature's `api.ts` as `queryOptions`.
- A feature may import from `components/`, `lib/` and `config/`, not from another feature's internals.
- Forms use React Hook Form with a Zod resolver through `components/ui/form.tsx`.
- Recharts is installed for the reporting screens; no chart exists yet because there is no data to chart.

## Runtime concerns

| Concern | Approach |
| --- | --- |
| Configuration | Environment variables only; see [development.md](development.md#configuration). Secrets have no defaults outside the `local` profile. |
| Caching | Spring cache abstraction backed by Redis (`bos:cache:` prefix, 10 minute default TTL). Cache only what can be recomputed from PostgreSQL. |
| Scheduling | Spring `@Scheduled`. Jobs must be idempotent; when more than one instance runs, guard jobs with a database lock before adding the second instance. |
| Files | `ObjectStorage` port. The database stores the object key and metadata; clients download through short-lived presigned URLs. |
| Observability | Actuator health (liveness/readiness), Prometheus metrics, structured logs with a request id. See [logging.md](logging.md). |
| Time | UTC everywhere on the server. Inject `Clock`; do not call `Instant.now()` in business code. |
