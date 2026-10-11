# Development guide

## Prerequisites

| Tool | Version | Needed for |
| --- | --- | --- |
| JDK | 21 or newer | Backend. The build always compiles to Java 21 |
| Node.js | 22 or newer | Frontend |
| Docker + Compose | current | Local PostgreSQL/Redis/MinIO, integration tests, container builds |
| k6 | current | Load tests only (optional) |

Maven does not need to be installed; use the wrapper (`./mvnw`, or `mvnw.cmd` on Windows).

## Running locally

```bash
cp .env.example .env
docker compose up -d                      # PostgreSQL :5432, Redis :6379, MinIO :9000 (console :9001)

cd backend
./mvnw spring-boot:run                    # http://localhost:8080  (profile: local)

cd frontend
npm install
npm run dev                               # http://localhost:5173  (proxies /api to :8080)
```

The `local` profile's defaults match `.env.example`, so the backend needs no environment variables.
Flyway applies migrations at start-up.

**Signing in locally:** on a fresh database the `local` profile creates a super admin. The email and
password are the `bootstrap-admin` values in `backend/src/main/resources/application-local.yml`.

### Whole stack in containers

```bash
docker compose --profile app up -d --build   # http://localhost:8088
docker compose --profile app down            # add -v to also delete the data volumes
```

## Configuration

All configuration is environment variables. `backend/src/main/resources/application.yml` is the
complete list; the most important ones:

| Variable | Purpose | Local default |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `local`, `test` or `prod` | `local` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL connection | `jdbc:postgresql://localhost:5432/bos`, `bos` |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | Redis connection | `localhost:6379` |
| `CACHE_TYPE` | `redis`, or `simple` to run without Redis | `redis` |
| `STORAGE_ENDPOINT`, `STORAGE_BUCKET`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY` | S3-compatible storage | MinIO on `localhost:9000` |
| `JWT_SECRET` | Signing key for access tokens, 32+ characters. Required outside `local` | dev-only value |
| `AUTH_COOKIE_SECURE` | Refresh cookie over HTTPS only | `false` locally, `true` otherwise |
| `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD` | First administrator, used only while there are no users | set in `local` |
| `CORS_ALLOWED_ORIGINS` | Comma-separated browser origins allowed to call the API | `http://localhost:5173` |
| `API_DOCS_ENABLED` | Expose Swagger UI and `/v3/api-docs` | `true` locally, `false` in prod |
| `APP_LOG_LEVEL` | Log level for application code | `DEBUG` locally, `INFO` in prod |
| `SERVER_PORT` | HTTP port | `8080` |

Frontend (`frontend/.env.example`): `VITE_API_BASE_URL` (default `/api/v1`) and, for the dev server
only, `DEV_API_PROXY_TARGET`.

Rules: outside the `local` profile, secrets have no defaults, so a missing one fails start-up instead
of silently using a weak value. Never commit `.env`. Only `VITE_*` variables reach the browser, so
nothing secret goes there.

## Tests

| Command | What runs | Needs |
| --- | --- | --- |
| `./mvnw test` | Unit, web-slice (`@WebMvcTest`) and architecture tests (`*Test`) | Nothing |
| `./mvnw verify` | The above plus integration tests (`*IT`) against real PostgreSQL via Testcontainers | Docker |
| `./mvnw verify -Dit.db.url=jdbc:postgresql://localhost:5432/bos -Dit.db.username=bos -Dit.db.password=...` | Same, against a PostgreSQL you already run (no Docker needed) | A disposable database |
| `npm run lint` / `npm run build` | ESLint, type-check, production build | Nothing |
| `npm run test:e2e` | Playwright browser tests (API mocked in the test) | `npx playwright install chromium` once |
| `k6 run perf/k6/smoke.js` | Smoke/load test with latency and error thresholds | A running backend, k6 |

With neither Docker nor `it.db.url`, the `*IT` tests do not run (the report shows `Tests run: 0` for
them) and the build still passes, so check the count. CI must run with Docker so they execute.

With `it.db.url`, the tests create and migrate a new schema named `it_<random>` on that server and use
nothing else. The schema is left behind; drop it with `DROP SCHEMA it_... CASCADE` or use a database
you can throw away.

Which kind of test to write:

- **Unit test** (`*Test`, Mockito): business rules in a service.
- **Web-slice test** (`@WebMvcTest` + `@Import(WebLayerTestConfig.class)`): a controller's contract:
  status codes, JSON shape, validation, security. Runs with the production security rules.
- **Integration test** (`*IT`, extends `AbstractIntegrationTest`): anything involving SQL, migrations
  or the full request path. Make it `@Transactional` if it writes data so each test rolls back. Use
  REST Assured against the random port for HTTP.
- **E2E** (Playwright): a few critical user journeys, not every screen.

## Adding a business module

Example: a `lead` module.

1. **Packages**: create `com.pawanputra.bos.lead` with `api/`, `internal/`, `web/`
   (see [architecture.md](architecture.md#backend-package-structure)).
2. **Migration**: add `backend/src/main/resources/db/migration/V<next>__<description>.sql`. Never
   edit a migration that has been merged. Follow the table conventions in [database.md](database.md).
3. **Entity** in `internal/`, extending `AuditableEntity` or `SoftDeletableEntity`. Reference other
   modules by id, not by JPA relationship. Add `@Audited(module = ..., entity = ..., label = ...)` so
   its changes are audited automatically, and `@AuditExclude` on any secret field
   (see [audit.md](audit.md)).
4. **Service** in `internal/`: `@Transactional`, throws `ApiException` subclasses, publishes domain
   events through `DomainEventPublisher` for anything other modules care about. Approvals, payments,
   exports and imports are recorded here with `@AuditedOperation` or `AuditRecorder`, never in the controller.
5. **Controller** in `web/`: mapped under `ApiPaths.V1`, DTO records in and out, `@Valid` on request
   bodies, `@Tag`/`@Operation` for the docs. It needs a valid token by default; add the module's
   permissions and `@PreAuthorize` checks as described in [security.md](security.md#roles-and-permissions),
   and scope every query by `CurrentUser.require().organizationId()`. No audit code goes here.
6. **Tests**: a unit test for the rules, a web-slice test for the contract including a 401 and a 403
   case, an `*IT` for persistence.
7. **Frontend**: `src/features/lead/` with `api.ts` (Zod schemas + `queryOptions`), `components/`,
   `pages/`; register the route in `src/app/router.tsx` (wrapped in `RequirePermission`) and the menu
   entry in `src/config/navigation.ts` (with its `permission`). Put
   `<ActivityTimeline entityType="..." entityId={...} />` on the record's page to show its history.
8. Run `./mvnw verify`: `ArchitectureTest` fails if the module reaches into another module's internals.

## Troubleshooting

**Backend fails to start with `Unable to establish loopback connection` / `Invalid argument: connect` (Windows).**
The JVM creates a socket file in the temp directory, and on some Windows machines that fails (seen
with a user profile name containing a space). Point it at a short path without spaces:

```bash
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Djdk.net.unixdomain.tmpdir=C:/bos-tmp"
java -Djdk.net.unixdomain.tmpdir=C:/bos-tmp -jar target/business-os.jar
```

(Create the directory first.) This does not affect Linux containers.

**`Schema validation: missing column ...` at start-up.** An entity and the schema disagree. Add a
migration; do not switch `ddl-auto` away from `validate`.

**Health is `DOWN` locally.** Redis or PostgreSQL is not running: `docker compose ps`. To work
without Redis, start with `CACHE_TYPE=simple` and `MANAGEMENT_HEALTH_REDIS_ENABLED=false`.

**`*IT` tests did not run.** Docker is not running or not installed. Start it, or pass `-Dit.db.url`.

**`Found more than one migration with version N`.** A renamed or deleted migration is still in
`target/`. Run `./mvnw clean verify`.

**Port 5173 is in use.** The dev server uses a fixed port (it is the allowed CORS origin); stop the
other process.
