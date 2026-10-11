# Organization and configuration

How the company is set up in the system: its organization and branches, and the catalog of what it
sells (verticals, categories, services). Administrators manage all of it from the **Configuration**
section of the menu; nothing here needs a code change or a deployment.

## Structure

```
Organization ──< Branch (city, state)

Vertical (fixed: six) ──< Category ──< Service
```

| Thing | Who can add one | Can be removed | Notes |
| --- | --- | --- | --- |
| Organization | Nobody (created at set-up) | No | Details are editable. Every user and branch belongs to one |
| Branch | Administrators | Soft-deleted, if nobody is assigned and it is not the head office | Has a required city and state |
| Vertical | **Nobody** | **No** | Exactly six, fixed. Name, description, order and on/off are editable |
| Category | Administrators | Soft-deleted, once it has no services | Belongs to one vertical for life |
| Service | Administrators | Soft-deleted; its code becomes reusable | Belongs to a category, and through it to a vertical |

### The six verticals

`CCTV_SECURITY`, `DIGITAL_MARKETING`, `INTERIOR_DESIGN`, `ARCHITECTURE_TECH`, `SOLAR`, `IT_SUPPORT`.

The set is closed at every level: a CHECK constraint in the database, the `ServiceVerticalCode` enum,
a repository with no insert or delete, an API with no create or delete endpoint, and a screen with no
"new" button. Adding a seventh is a product decision that needs a migration and a code change.

Every service belongs to exactly one of the six because it must have a category, and every category
must have a vertical.

## Configuration, not code

The differences between services are stored as data on each service:

| Field | Meaning | Example |
| --- | --- | --- |
| `billingType` | `ONE_TIME`, `RECURRING` or `QUOTE_BASED` | An installation is one-time; an AMC is recurring |
| `basePrice` | Starting price per unit in INR. Required unless quote-based | `55000.00` |
| `unitLabel` | What one unit is | `per kW`, `per camera`, `per month` |
| `requiresSiteVisit` | A visit is needed before work starts | |
| `estimatedDurationDays` | Typical time to deliver | `7` |
| `displayOrder`, `active` | Where it appears, and whether it is offered | |

**Rule for all future code:** read these fields. Never write `if (vertical == SOLAR)` or
`if (service.code == ...)` in a controller, a service class or the UI. If two services need to behave
differently and no field expresses the difference, add a field (with a migration and a form input),
not a branch in the code. The controllers in this module contain no vertical- or service-specific
logic, and the same rule applies to the modules that will use the catalog.

Workflows (the stages a lead, quote or project moves through) are **not** modelled yet. When they
are, they follow the same rule: stages and transitions are rows that administrators edit, attached
to a vertical or a service, and the code that moves work along reads them.

## Growing from one city to many

- Every branch records its **city** and **state** (required). Listings are indexed and filterable by
  both, and `GET /branches/locations` returns the states and cities the organization is present in.
- Opening in a new city or state is just creating a branch there; nothing else changes.
- The form suggests cities and states already in use, to avoid the same place being spelled two ways.
  They are free text, not a fixed list. If reporting by region becomes important, replace them with
  reference tables of states and cities; the columns and filters are already in place.
- Users are attached to one organization and, optionally, one branch.
- The catalog is company-wide: the same verticals, categories and services apply in every branch.
  Per-branch availability or pricing is not built; it would be a table linking services to branches.

## Rules the system enforces

| Rule | Result if broken |
| --- | --- |
| Codes (branch, category, service) are fixed once created | The field is not accepted on edit |
| Service code is unique across the catalog; category code and name are unique within a vertical; service name is unique within a category; branch code is unique within the organization | `409 CONFLICT` |
| A fixed-price or recurring service needs a base price | `422 BUSINESS_RULE_VIOLATION` |
| A category with services cannot be deleted | `422` |
| The head office cannot be deactivated or deleted; making another branch the head office moves the title | `422` |
| A branch with people assigned cannot be deleted | `422` |
| Two people editing the same record: the second save is refused | `409 CONFLICT` (every edit sends the `version` it loaded) |
| A branch of another organization | `404`, as if it did not exist |

Switching a vertical or category off does **not** switch off what is inside it. Each level has its
own switch, so turning a vertical back on restores everything exactly as it was. Code that offers
services to users should check all three levels.

## API

All under `/api/v1`. Lists accept `page`, `size` (max 100) and `sort=field,asc|desc`, and return the
standard page body (see [api-conventions.md](api-conventions.md#collections)).

| Endpoint | Permission | Filters / notes |
| --- | --- | --- |
| `GET /organization`, `PUT /organization` | `ORGANIZATION_VIEW` / `ORGANIZATION_UPDATE` | The caller's own organization |
| `GET /branches` | `BRANCH_VIEW` | `search` (name, code, city), `state`, `city`, `active` |
| `GET /branches/locations` | `BRANCH_VIEW` | States and their cities |
| `POST /branches`, `PUT /branches/{id}` | `BRANCH_CREATE` / `BRANCH_UPDATE` | |
| `POST /branches/{id}/activate`, `/deactivate` | `BRANCH_UPDATE` | |
| `DELETE /branches/{id}` | `BRANCH_DELETE` | |
| `GET /catalog/verticals`, `GET /catalog/verticals/{code}` | `CATALOG_VIEW` | All six, including inactive |
| `PUT /catalog/verticals/{code}`, `POST .../activate`, `/deactivate` | `CATALOG_UPDATE` | No `POST` or `DELETE` exists |
| `GET /catalog/categories` | `CATALOG_VIEW` | `search` (name, code), `vertical`, `active` |
| `POST`, `PUT`, `DELETE /catalog/categories[/{id}]`, activate/deactivate | `CATALOG_CREATE` / `_UPDATE` / `_DELETE` | |
| `GET /catalog/services` | `CATALOG_VIEW` | `search` (name, code), `vertical`, `categoryId`, `active`, `billingType` |
| `POST`, `PUT`, `DELETE /catalog/services[/{id}]`, activate/deactivate | `CATALOG_CREATE` / `_UPDATE` / `_DELETE` | |
| `GET /service-verticals` | none (public) | Active verticals only, for the rest of the product |

## Screens

| Screen | Path | Needs |
| --- | --- | --- |
| Services: list, search, filter by vertical / category / billing / status | `/admin/services` | `CATALOG_VIEW` |
| Create / edit service | `/admin/services/new`, `/admin/services/:id/edit` | `CATALOG_CREATE` / `CATALOG_UPDATE` |
| Categories: list, create, edit | `/admin/categories[...]` | as above |
| Verticals: list, edit, switch on/off | `/admin/verticals[...]` | `CATALOG_VIEW` / `CATALOG_UPDATE` |
| Branches: list by state and city, create, edit | `/admin/branches[...]` | `BRANCH_VIEW` / `BRANCH_CREATE` / `BRANCH_UPDATE` |

Search, filters and the page number live in the URL, so a filtered view can be bookmarked or shared.
Menu entries, buttons and pages are hidden from users who lack the permission; the API refuses them
regardless.

Every change made on these screens is audited automatically, and the service, category and branch
edit pages show the record's history (see [audit.md](audit.md)).

Each service also has a **Lead fields** screen (`/admin/services/:id/fields`) where the questions asked
on a lead for that service are defined; see [custom-fields.md](custom-fields.md).

Not on a screen yet: editing the organization's details, and deleting branches, categories or
services (the API supports all of these).

## Adding a list screen like these

Backend: a `Filter` record and a `search(filter, pageable)` method built from `Specs` (see
`ServiceOfferingService`), and a controller method using `Paging.of(...)` with an explicit map of
sortable fields. Frontend: a `queryOptions` factory taking the filters, `useListParams()` for the URL
state, and `<AdminList>` with a toolbar of `<SearchInput>` and `<FilterSelect>`.
