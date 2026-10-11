# Audit and activity

Who did what, when, from where, and what the values were before and after. Modules do not write
audit code: they declare what is auditable and the infrastructure does the rest.

## Two tables, two jobs

| | `audit_logs` | `entity_activity_logs` |
| --- | --- | --- |
| Purpose | The forensic record for administrators and investigations | The readable history of one record, shown on that record's page |
| Contains | Every audited event, including sign-ins | Only events about a specific record |
| Detail | Actor, IP, browser, request id, full before/after values, metadata | One sentence plus field-level changes (`basePrice: 100 → 120`) |
| Can be changed | **No.** A database trigger refuses `UPDATE` and `DELETE` | Yes (it is a convenience view of the same events) |
| Read through | `GET /audit/logs` and the Audit log screen | `GET /audit/activity` and `<ActivityTimeline>` |

Both are written together, in the same transaction as the change they describe: a change that rolls
back leaves no audit row, and a change whose audit row cannot be written does not happen.

## What is recorded for each event

| Field | Meaning |
| --- | --- |
| `occurred_at` | When, in UTC |
| `actor_id`, `actor_label` | Who: the signed-in user and their email at the time. `system` for jobs and start-up, `anonymous` for public requests |
| `organization_id` | Whose audit log the event belongs to (decides who can see it) |
| `action` | `CREATE`, `UPDATE`, `DELETE`, `STATUS_CHANGE`, `LOGIN`, `LOGOUT`, `APPROVAL`, `PAYMENT`, `EXPORT`, `IMPORT` |
| `module` | The owning module: `identity`, `catalog`, ... |
| `entity_type`, `entity_id`, `entity_label` | What it was about: `Service`, its id, and its name at the time |
| `summary` | One sentence: `Updated Service '3 kW rooftop': basePrice, name` |
| `before_value`, `after_value` | JSON. For an update, only the fields that changed |
| `metadata` | JSON. Anything else: a reason, an outcome, a count, a session id |
| `ip_address`, `user_agent` | Where the request came from (empty for jobs) |
| `request_id` | The same id as in the application logs and the `X-Request-Id` header |

## Three ways an event gets recorded

### 1. Automatically, for data changes: `@Audited`

Put the annotation on the entity. That is all.

```java
@Entity
@Audited(module = "customers", entity = "Customer", label = "name")
public class Customer extends SoftDeletableEntity {

    @AuditExclude                 // never copied into the audit log
    private String internalNotes;
}
```

A Hibernate listener (`EntityAuditListener`) sees every insert, update and delete and records:

| Change | Recorded as |
| --- | --- |
| Insert | `CREATE`, with all values in `after` |
| Update that sets `deletedAt` (a soft delete) | `DELETE`, with the last values in `before` |
| Update of only `active` or `status` | `STATUS_CHANGE` |
| Any other update | `UPDATE`, with only the changed fields in `before` and `after` |
| Real delete | `DELETE` |

Not recorded: collections, the technical columns (timestamps, `createdBy`/`updatedBy`, `version`),
and fields marked `@AuditExclude`. A reference to another entity is stored as its id. An update that
touches only excluded fields produces no event, which is why a sign-in does not show up as "user
updated".

Use `@AuditExclude` for secrets (password hashes, tokens) and for bookkeeping that changes
constantly and means nothing to an auditor.

### 2. With an annotation, for operations: `@AuditedOperation`

For things that are not a change to one row.

```java
@AuditedOperation(action = AuditAction.EXPORT, module = "customers", entity = "Customer",
                  summary = "Exported customers")
public ExportFile exportCustomers(String format, CustomerFilter filter) { ... }
```

One event is recorded each time the method returns normally; its arguments are stored as metadata
by parameter name. Nothing is recorded if it throws. Like `@Transactional`, it only works when the
method is called through its Spring bean, and its parameters must not carry secrets.

### 3. Explicitly, when the details are only known in the code: `AuditRecorder`

```java
auditRecorder.record(AuditEvent.of(AuditAction.APPROVAL, "finance", "Approved invoice")
        .entity("Invoice", invoice.getId(), invoice.getNumber())
        .before(Map.of("status", "PENDING"))
        .after(Map.of("status", "APPROVED"))
        .metadata("amount", invoice.getTotal()));
```

The actor, organization, IP address, browser and request id are filled in from the current request.
Call this from a **service**, never from a controller.

### Which to use

| Situation | Use |
| --- | --- |
| A row of your entity is created, changed, soft-deleted or switched on/off | `@Audited` on the entity |
| An export, import or other operation, described well enough by its arguments | `@AuditedOperation` on the service method |
| An approval, payment or anything needing chosen before/after values or metadata | `AuditRecorder` in the service |
| Sign-in and sign-out | Already done in `AuthService` |

## What is audited today

| Entity (`entity_type`) | Module | Excluded fields |
| --- | --- | --- |
| `Organization`, `Branch`, `Role` | identity | none |
| `User` | identity | password hash, last login, failed-attempt counter, lock time, password-changed time |
| `Vertical`, `Category`, `Service` | catalog | none |
| `Customer`, `Contact`, `Address`, `Note`, `Tag` | customers | derived columns (display name, matching keys) |

Plus, explicitly: sign-in (success and failure, with the reason), sign-out, a session revoked for
refresh-token reuse, and password change or reset (the fact, never the value).

A customer export is recorded as `EXPORT` with the filters used and the row count. `APPROVAL`,
`PAYMENT` and `IMPORT` are supported and tested, but no feature produces them yet.

An entity that is part of something bigger can implement `ActivityOwner` so its changes appear in
the owner's timeline: a customer's contacts, addresses and notes are filed under the customer. A
module can show a record's timeline to its own users through `ActivityFeed` without requiring
`AUDIT_VIEW`, as the customer profile's Activities tab does.

Not audited yet: which roles a user holds and which permissions a role has (they change only by
migration and at bootstrap today; annotate `UserRole` when role management is built), and reading
data (only changes and sign-ins are recorded).

## Who can see it

- The permission `AUDIT_VIEW`, held by `SUPER_ADMIN`, `ADMIN` and `DIRECTOR`.
- An organization sees only its own events. An event belongs to the organization of the person who
  caused it.
- Events with no organization are in the table but shown to nobody through the API: a sign-in
  attempt for an email that does not exist, and changes made by start-up tasks or jobs. Query the
  database directly to investigate those.
- There is no endpoint that creates, changes or deletes audit events, for anyone.

## API and screen

All under `/api/v1/audit`, all requiring `AUDIT_VIEW`.

| Endpoint | Returns |
| --- | --- |
| `GET /logs` | A page of events, newest first. Filters: `actorId` (user), `module`, `action`, `entityType`, `entityId`, `from` / `to` (ISO-8601 instants; `from` inclusive, `to` exclusive), `search` (summary, user, entity name) |
| `GET /logs/{id}` | One event with `before`, `after`, `metadata`, browser and request id |
| `GET /facets` | The modules, entity types and users that occur in the log, for filter choices |
| `GET /activity?entityType=&entityId=` | The timeline of one record |

**Audit log screen** (`/admin/audit`, under Administration): the list with filters for user, module,
action, entity and a date range, plus text search; filters live in the URL. "Details" opens one event
with a before/after table. Dates are picked in the viewer's own time zone and converted to exact
instants.

**Record timeline**: `<ActivityTimeline entityType="Service" entityId={id} />` on any record's page.
It is on the service, category and branch edit pages, and renders nothing for users without
`AUDIT_VIEW`.

## Things to know

- **Personal data.** Before/after values of users, branches and organizations include names, emails
  and phone numbers. That is the point of an audit log, but it means the table is sensitive: keep
  `AUDIT_VIEW` to few people, and think before marking a field with highly sensitive content as audited.
- **No retention yet.** Nothing deletes old audit rows, and the trigger would stop it. When a
  retention period is decided, implement it as a reviewed migration (for example monthly partitions
  that are detached and archived), not as an application job.
- **Volume.** One row per change and per sign-in, indexed for the screen's filters. Bulk imports of
  audited entities will write one audit row per row imported.
- **The trigger protects against the application, not against a database administrator**, who can
  drop it. Restrict database credentials accordingly.
- **Field names in `before`/`after` are Java property names.** Renaming a property changes the key
  used from then on; old rows keep the old name.
