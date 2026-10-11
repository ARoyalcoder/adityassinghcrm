# Customer management

The people and companies the business works with. This is the record every later module (leads,
quotations, projects, invoices, tickets) attaches to.

## Model

```
Customer ──< Contact        people at the customer; one is the primary contact
    │    ──< Address        billing / service / other; one default per type
    │    ──< Note           written once, can be removed
    └──>< Tag               free-form labels the organization invents
```

| Thing | Notes |
| --- | --- |
| **Customer** | `INDIVIDUAL` (first and last name) or `BUSINESS` (company name, GSTIN). Has a customer number (`CUS-001001`), status, source, an assigned employee and tags. Belongs to one organization |
| **Contact** | Name, designation, phone, email. The first contact is the primary one; making another primary moves the title |
| **Address** | Type `BILLING`, `SERVICE` or `OTHER`. A customer can have several of each. The first of a type is its default; billing and service defaults are independent |
| **Tag** | Created the first time someone types a new name. Unique per organization, ignoring case |
| **Note** | Records its author's name at the time. Not editable |

| Field | Values |
| --- | --- |
| Status | `PROSPECT`, `ACTIVE` (default), `INACTIVE`, `BLOCKED` |
| Source | `WALK_IN`, `REFERRAL`, `WEBSITE`, `PHONE_CALL`, `SOCIAL_MEDIA`, `ADVERTISEMENT`, `PARTNER`, `OTHER` |
| Assigned employee | Must be an active user of the same organization |

Deleting a customer is a soft delete: it disappears from lists and from duplicate detection, and stays
in the database for the records that refer to it.

## Duplicate detection

Before a customer is saved, the system looks for existing customers that may be the same one.

| Compared | How | Also checked on |
| --- | --- | --- |
| **Phone** | Digits only, last 10: `+91 98765-43210`, `09876543210` and `9876543210` are the same number | the customer's contacts |
| **Email** | Ignoring case and surrounding spaces | the customer's contacts |
| **Company name** | Ignoring case, punctuation, and legal-form words at the end (`Pvt`, `Private`, `Ltd`, `Limited`, `LLP`, `LLC`, `Inc`, `Co`, `Company`, `Corp`): `Acme Solar Pvt. Ltd.` matches `ACME SOLAR` | |

How it is enforced:

1. The form calls `POST /customers/duplicate-check` when the user saves. If anything matches, it shows
   the existing customers and why each matched (`same phone`, `same email`, `same company name`),
   with a link to open each one.
2. The user either goes back, or chooses **Save anyway**, which sends `confirmDuplicates: true`.
3. The server applies the same rule itself: a create without that flag is refused with
   `409 POSSIBLE_DUPLICATE` if a match exists. A client that skips step 1 cannot create duplicates
   by accident.

Details:

- Only live customers of the **same organization** are compared.
- When **editing**, the check runs only if the phone, email or company name changed, and never
  matches the customer against itself.
- A match is a warning for a person to judge, not proof. Two people can share a landline; a group can
  have several companies. That is why it can be overridden.
- Matching is exact on the normalised value. It does not catch typos ("Acme Solr") or a person's name
  entered twice with no phone or email. Fuzzy matching and merging two customers are not built.
- Adding a **contact** with a phone or email that belongs to another customer is not blocked; it is
  found the next time someone enters that phone or email as a new customer.

## List

`/customers` (menu: Customers). Needs `CUSTOMER_VIEW`.

- **Search**: name, customer number, email, phone (in any format), company name.
- **Filters**: type, status, source, assigned employee, tag.
- **Sort**: name, newest/oldest, customer number, status.
- **Pagination**, with search, filters, sort and page kept in the URL.
- **Export CSV**: the whole filtered list in the chosen order, not just the visible page. Needs the
  separate permission `CUSTOMER_EXPORT`. Every export is written to the audit log with who did it,
  the filters used and the number of rows. At most 20,000 rows per export; beyond that the user is
  asked to narrow the filters. Cells that a spreadsheet would run as a formula (starting with
  `=`, `+`, `-`, `@`) are prefixed with an apostrophe, so phone numbers like `+91…` appear with a
  leading `'` in the raw file.

## Customer 360

`/customers/:id`. A header that is always in view, and one section per subject below it; the open
section is in the URL (`?tab=timeline`).

| Section | What it shows |
| --- | --- |
| **Header** | Name, status, **health**, customer number, type, who manages it, phone, email, primary contact, tags |
| Summary | Health with its reasons, summary facts (customer since, last contact, primary contact, default addresses, counts), the five latest timeline events, notes |
| Timeline | Everything that has happened, newest first, filterable by category |
| Business information | The record's own facts (type, company, GSTIN, source, assignment, tags) and its addresses |
| Contacts | List, add, edit, remove, choose the primary |
| Activities | Calls, meetings, emails, site visits and messages: log one, see and remove those logged |
| Sales history, Project history, Financial summary, Service history, AMC / Warranty, Documents | **Not built.** Each section is present and says so. It shows no figures, not even zeros, because "none" is not known |

### Interactions

An interaction is a record of something that **happened** with the customer: type `CALL`, `MEETING`,
`EMAIL`, `SITE_VISIT`, `MESSAGE` or `OTHER`; optional direction `INBOUND` / `OUTBOUND`; when it
happened (defaults to now; a future time is refused with `422`, since something planned is a
follow-up, not an interaction); subject, details, outcome; and who logged it. Table
`customer_interactions` (migration `V10`).

### Timeline

The timeline is **not stored**. There is no timeline table and no copy of any event. Each request
asks every *source* for its newest events and merges them, so:

- an event exists exactly as long as the record it describes (remove a call logged by mistake and it
  is gone from the timeline);
- nothing can be shown twice or drift out of step with the record;
- a new module joins the timeline by declaring one bean, with no change to the customer module.

Sources today:

| Source | Events | Category |
| --- | --- | --- |
| Interactions | Call logged, Meeting completed, Email logged, Site visit completed, Message logged | `COMMUNICATION` |
| Notes | Note added (with the note's text) | `COMMUNICATION` |
| Audit trail | Customer created, Details updated, Status changed; Contact / Address added, updated, removed | `RECORD` |

Interactions and notes are also audited, but they reach the timeline only from their own source:
the audit source skips them, so each appears once.

Categories are `COMMUNICATION`, `SALES`, `PROJECT`, `FINANCE`, `SERVICE`, `AMC`, `RECORD`. The event
types reserved for modules that are not built yet (`LEAD_CREATED`, `QUOTATION_SENT`,
`QUOTATION_ACCEPTED`, `ORDER_CREATED`, `PROJECT_STARTED`, `MILESTONE_COMPLETED`,
`INVOICE_GENERATED`, `PAYMENT_RECEIVED`, `TICKET_CREATED`, `TICKET_RESOLVED`, `AMC_CREATED`,
`AMC_RENEWED`) are named in `platform.timeline.TimelineEventTypes` and already have icons in the
UI. **Nothing produces them yet**: they appear when their module provides a source.

`GET /{id}/timeline?limit=25&category=SALES&category=FINANCE&cursor=...` returns
`{ "events": [...], "nextCursor": "..." }`. Pass `nextCursor` back for the next (older) page; it is
absent on the last page. `limit` is 1 to 100. Events are ordered by when they happened, then by id,
so paging never skips or repeats an event, including events that share a timestamp.

#### Adding a module's events

In the new module (it may depend on `customer.api` and `platform.timeline`, never on customer
internals):

```java
@Component
class QuotationTimelineSource implements CustomerTimelineSource {

    public Set<TimelineCategory> categories() {
        return Set.of(TimelineCategory.SALES);
    }

    /** The newest `limit` events at or before `notAfter` (null = no bound), newest first. */
    public List<TimelineEvent> events(UUID organizationId, UUID customerId, Instant notAfter, int limit) {
        return quotations.sentTo(customerId, notAfter, limit).stream()
                .map(q -> TimelineEvent.of("quotation-sent:" + q.getId(), TimelineEventTypes.QUOTATION_SENT,
                        TimelineCategory.SALES, q.getSentAt(), "Quotation sent", q.getNumber(), q.getSentByName()))
                .toList();
    }
}
```

Rules for a source: ids must be stable and unique (`<kind>:<record id>`); return events newest
first; honour `notAfter` and `limit` in the query, not afterwards (the merge relies on a source that
returns fewer than `limit` having nothing older). `FutureModuleProbe` in the backend tests is a
working example, and `Customer360IT` proves that such a bean's events and health signals appear.

On the frontend there is nothing to do: an unknown event type is shown with its category's icon. To
give it its own icon, add a line to `frontend/src/components/timeline/event-types.ts`.

#### Reusable timeline components

`frontend/src/components/timeline/` knows nothing about customers:

| Piece | Use |
| --- | --- |
| `Timeline` | Draws a list of events (grouped by day, or `compact` for a short "recent" list) |
| `TimelineFeed` | A complete timeline: category filters, events, "Show older". Give it a `fetchPage` function |
| `model.ts` | The event and page schemas, the categories, the query-string builder |
| `event-types.ts` | Icon per event type, colour per category |

A project or ticket timeline later is a new endpoint returning `TimelinePage` and one `<TimelineFeed>`.
`platform.timeline.TimelineMerger` on the backend is equally independent of customers.

### Health

Health is worked out on request from current records; it is not stored and not a score.

| Level | Meaning |
| --- | --- |
| `GOOD` | No problem signals, and at least one positive one |
| `NEEDS_ATTENTION` | At least one warning |
| `AT_RISK` | At least one critical signal |
| `UNKNOWN` | No signals: too little is on record to say. Not the same as good |

The level is the worst signal found, and the response lists every signal with a sentence a person
can read, most serious first. Signals from the customer module:

| Code | Impact | When |
| --- | --- | --- |
| `CUSTOMER_BLOCKED` | critical | Status is `BLOCKED` |
| `CUSTOMER_INACTIVE` | warning | Status is `INACTIVE` |
| `NOT_REACHABLE` | warning | No phone or email on the customer or any contact |
| `CONTACT_STALE` | warning | Last interaction more than `stale-contact-days` ago (default 90) |
| `NEVER_CONTACTED` | warning | No interaction, and a customer for more than `stale-contact-days` |
| `CONTACT_RECENT` | positive | Last interaction within `recent-contact-days` (default 30) |

The thresholds are `app.customers.health.recent-contact-days` and `stale-contact-days`
(`CUSTOMER_HEALTH_RECENT_CONTACT_DAYS`, `CUSTOMER_HEALTH_STALE_CONTACT_DAYS`).

Health today rests **only** on status, reachability and logged interactions. It knows nothing about
money, projects or tickets, because those modules do not exist. A module adds its own signals by
declaring a `CustomerHealthContributor` bean (for example "2 invoices overdue" as critical); the
level then takes them into account with no change here.

### Plugging a module into the profile

The sections are a list in `frontend/src/features/customers/profile/tabs.tsx`. A module adds its
section by replacing its placeholder line:

```tsx
// before
notBuilt('sales', 'Sales history', 'Leads, opportunities, quotations and orders'),
// after
{ key: 'sales', label: 'Sales history', component: CustomerSalesTab },
```

Its component receives the customer (`{ customer }`) and fetches its own data, typically from an
endpoint of its own module filtered by `customerId`. On the backend the module stores the customer's
id (no foreign key across modules, like every other cross-module reference) and nothing in the
customer module changes.

## Permissions

| Permission | Allows | Default roles |
| --- | --- | --- |
| `CUSTOMER_VIEW` | List, profile, contacts, addresses, notes, interactions, timeline, health and summary | Super Admin, Admin, Director, Sales Manager, Sales Executive, Project Manager, Finance, Support Agent |
| `CUSTOMER_CREATE` | Create a customer | Super Admin, Admin, Sales Manager, Sales Executive |
| `CUSTOMER_UPDATE` | Edit a customer; add, edit and remove contacts, addresses and notes; log and remove interactions | Super Admin, Admin, Sales Manager, Sales Executive |
| `CUSTOMER_DELETE` | Delete a customer | Super Admin, Admin, Sales Manager |
| `CUSTOMER_EXPORT` | Export the list | Super Admin, Admin, Director, Sales Manager |

Everyone with `CUSTOMER_VIEW` sees **all** customers of their organization. "Assigned employee" says
who looks after a customer; it does not restrict who can see it. Limiting sales staff to their own
customers is not built.

A customer's timeline needs only `CUSTOMER_VIEW`, including the record changes on it. The full audit log
needs `AUDIT_VIEW`.

## API

All under `/api/v1/customers`.

| Endpoint | Permission |
| --- | --- |
| `GET /` (`search`, `type`, `status`, `source`, `assignedTo`, `tagId`, `sort`, `page`, `size`) | `CUSTOMER_VIEW` |
| `GET /export` (same filters and sort) | `CUSTOMER_EXPORT` |
| `GET /tags`, `GET /assignees` | `CUSTOMER_VIEW` |
| `POST /duplicate-check` | `CUSTOMER_CREATE` or `CUSTOMER_UPDATE` |
| `POST /`, `PUT /{id}` | `CUSTOMER_CREATE` / `CUSTOMER_UPDATE` |
| `DELETE /{id}` | `CUSTOMER_DELETE` |
| `GET /{id}`, `GET /{id}/activity` | `CUSTOMER_VIEW` |
| `GET /{id}/contacts`, `/addresses`, `/notes`, `/interactions` | `CUSTOMER_VIEW` |
| `GET /{id}/overview` (health and summary) | `CUSTOMER_VIEW` |
| `GET /{id}/timeline` (`cursor`, `limit`, `category`) | `CUSTOMER_VIEW` |
| `POST`, `PUT`, `DELETE` on `/{id}/contacts`, `/addresses`; `POST`, `DELETE` on `/{id}/notes`, `/{id}/interactions` | `CUSTOMER_UPDATE` |

Edits send the `version` that was loaded; a stale one is `409 CONFLICT`. A customer, contact, address
or note of another organization is `404`.

## For other modules

- Refer to a customer by its id. Do not join to the customer tables or add a foreign key to them.
- Auditing is automatic: `Customer`, `Contact`, `Address`, `Note`, `Interaction` and `Tag` are `@Audited`, and the
  parts file their activity under the customer (see `ActivityOwner` in [audit.md](audit.md)).
- To show your events on the customer timeline, provide a `CustomerTimelineSource`; to affect
  customer health, a `CustomerHealthContributor` (both in `customer.api`; see above). Do not write
  to any customer table to do either.
- `UserDirectory` (identity module) is how a module validates and names an assigned employee.

## Not built

Merging duplicates, fuzzy matching, bulk import, per-user visibility, custom fields, configurable
status or source lists (they are fixed enums; changing them needs a migration).

In the Customer 360: Sales history, Project history, Financial summary, Service history,
AMC / Warranty and Documents are placeholders, and no timeline event or health signal about sales,
projects, money, tickets or AMC exists until those modules are built. Editing an interaction
(remove and log again), follow-ups and reminders, attaching an interaction to a contact, and
opening the record behind a timeline event are also not built.
