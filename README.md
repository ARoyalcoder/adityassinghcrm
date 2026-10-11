# Pawan Putra Business OS

Business operating system (CRM and operations) for a multi-service company with exactly six
business verticals:

1. CCTV & Security
2. Digital Marketing
3. Interior Design
4. Architecture & Tech
5. Solar
6. IT Support

This repository currently contains the **platform foundation**: architecture, conventions, tooling and
the database foundation (organizations, branches, users, roles, permissions and the service catalog),
authentication with role/permission-based authorization, the organization and configuration
module (branches and the service catalog, with admin screens), automatic audit and activity logging,
and customer management with a Customer 360 (health, a unified timeline, logged calls and meetings).
Each service can be given its own lead fields (configured by administrators, rendered and validated dynamically).
Leads exist in a minimal form with qualification: a deterministic, explainable score, priority, health and
qualification status. Quotations, projects, invoicing and the other CRM modules are not built yet.

## Stack

| Layer | Technology |
| --- | --- |
| Frontend | React 19, TypeScript, Vite, React Router, TanStack Query, TanStack Table, React Hook Form, Zod, Tailwind CSS 4, shadcn/ui, Recharts, Lucide |
| Backend | Java 21, Spring Boot 4, Spring Security, Spring Data JPA (Hibernate), Bean Validation, Flyway, Spring Scheduler, Actuator, springdoc OpenAPI |
| Data | PostgreSQL (source of truth), Redis (cache), MinIO / S3-compatible object storage |
| Infrastructure | Docker, Docker Compose, Nginx |
| Testing | JUnit 5, Mockito, ArchUnit, Testcontainers, REST Assured, Playwright, k6 |

## Quick start

Prerequisites: JDK 21+, Node 22+, Docker.

```bash
cp .env.example .env            # local credentials for the containers
docker compose up -d            # PostgreSQL, Redis, MinIO

cd backend && ./mvnw spring-boot:run     # API on http://localhost:8080
cd frontend && npm install && npm run dev # UI on http://localhost:5173
```

- UI: <http://localhost:5173> (sign in with the local admin from `backend/src/main/resources/application-local.yml`)
- API docs (local profile only): <http://localhost:8080/swagger-ui.html>
- Health: <http://localhost:8080/actuator/health>

To run everything in containers behind Nginx instead: `docker compose --profile app up -d --build`,
then open <http://localhost:8088>.

## Repository layout

```
backend/        Spring Boot modular monolith (one deployable)
frontend/       React single-page app, plus the Nginx config that serves it
perf/k6/        Load / smoke test scripts
docs/           Architecture and conventions (start here before adding a module)
docker-compose.yml, .env.example
```

## Documentation

| Document | Read it when |
| --- | --- |
| [docs/architecture.md](docs/architecture.md) | You want to know how the system is structured and why |
| [docs/database.md](docs/database.md) | You are adding a table, a migration or an entity |
| [docs/configuration.md](docs/configuration.md) | You are working with branches, verticals, categories or services |
| [docs/project-analytics.md](docs/project-analytics.md) | You want to know how a dashboard figure is worked out, or how pipeline stages are configured |
| [docs/field-operations.md](docs/field-operations.md) | You are working with field jobs, the technician screens or customer sign-off |
| [docs/project-templates.md](docs/project-templates.md) | You are defining how a service's projects run, or planning a project from that |
| [docs/leads.md](docs/leads.md) | You are working with leads, their score and why, or the qualification questions |
| [docs/custom-fields.md](docs/custom-fields.md) | You are configuring what a service asks on a lead, or building a form that asks it |
| [docs/customers.md](docs/customers.md) | You are working with customers, or adding a module that attaches to them |
| [docs/audit.md](docs/audit.md) | You want changes to your entity audited, or need to record an approval, payment, export or import |
| [docs/security.md](docs/security.md) | You are protecting an endpoint, adding a permission, or deploying |
| [docs/api-conventions.md](docs/api-conventions.md) | You are adding or consuming an endpoint (includes the error format) |
| [docs/logging.md](docs/logging.md) | You are adding log statements or debugging a request |
| [docs/development.md](docs/development.md) | You are setting up, running tests, or adding a new module |

## Verifying a change

```bash
cd backend && ./mvnw verify          # unit + architecture tests; integration tests need Docker
cd frontend && npm run lint && npm run build && npm run test:e2e
```
