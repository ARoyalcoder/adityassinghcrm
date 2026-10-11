# Field operations

Jobs and site visits carried out at a customer's site by a technician: scheduled and assigned by
a dispatcher, worked through on a phone by the technician, and confirmed by the customer on the
spot.

There is no separate mobile application. The technician's screens are ordinary pages of the web
application, laid out for a phone held in one hand: one column, large buttons, the next step pinned
to the bottom of the screen, the phone's camera for photos, and a finger for the signature.

## What a job holds

| | |
| --- | --- |
| **Job** | Number (`JOB-001001`), title, type (`JOB` or `SITE_VISIT`), instructions, schedule (start, optional end) |
| **Customer and site** | Customer name, site address, contact on site and their phone. Copied onto the job, so the technician can read them whatever happens to the customer record |
| **Technician assignment** | One technician (any active member of staff); can be changed until someone sets off |
| **Checklist** | Items the technician ticks off. All must be ticked before the job can be completed |
| **Photos** | JPEG, PNG or WebP, up to 10 MB each, up to 30 per job, with an optional caption. Stored in object storage under a generated name |
| **Materials used** | Name, quantity, unit |
| **Notes** | Free text, by whoever is working the job |
| **Customer sign-off** | The name of the person confirming, optional remarks, and their signature drawn on the screen |

A site visit is a job of type `SITE_VISIT`: the same record and the same workflow.

## Workflow

```
UNASSIGNED → ASSIGNED → ACCEPTED → EN_ROUTE → ARRIVED → IN_PROGRESS → COMPLETED → CUSTOMER_CONFIRMED
                                                   (CANCELLED, by a dispatcher, from any open status)
```

- One step at a time: a step can only be taken from the one before it. The time of each step is recorded.
- **Assigning** (or reassigning) puts the job at `ASSIGNED`, for that technician to accept. It is
  possible while the job is `UNASSIGNED`, `ASSIGNED` or `ACCEPTED`; once someone is en route the
  job stays with them.
- Checklist, photos and materials can be recorded from `ACCEPTED` to `COMPLETED`; notes until the
  job is closed.
- **Completing** needs every checklist item ticked (`422` says how many are open).
- **Customer confirmation** needs the name of the person confirming; the signature is optional
  (a customer may be absent or decline) and is stored as a PNG.
- `CUSTOMER_CONFIRMED` and `CANCELLED` are closed: nothing more can be recorded or changed.

## Technician dashboard

`/my-jobs` shows the signed-in technician's own jobs in four lists, each with its count:

| List | Jobs |
| --- | --- |
| Today's Jobs | Still to do, scheduled for today |
| Upcoming Jobs | Still to do, scheduled for a later day |
| Delayed Jobs | Still to do, scheduled for a day that has passed |
| Completed Jobs | Completed or customer-confirmed, most recent first |

"Still to do" is `ASSIGNED` to `IN_PROGRESS`. Days are the server's days.

## Who may do what

| Permission | Allows | Default roles |
| --- | --- | --- |
| `FIELD_JOB_WORK` | The technician dashboard; opening and working **the jobs assigned to you** | Super Admin, Technician |
| `FIELD_JOB_VIEW` | Seeing every job of the organization | Super Admin, Admin, Director, Project Manager, Support Agent |
| `FIELD_JOB_MANAGE` | Creating, editing, assigning and cancelling jobs; taking a step or recording work on any job (the technician's phone is dead) | Super Admin, Admin, Project Manager |

A technician who is not assigned to a job gets `404` for it, as if it did not exist. Another
organization's job is `404` for everyone.

## API

All under `/api/v1/field-jobs`.

| Endpoint | Permission |
| --- | --- |
| `GET /my?bucket=TODAY\|UPCOMING\|DELAYED\|COMPLETED`, `GET /my/summary` | `FIELD_JOB_WORK` |
| `GET /` (`search`, `status`, `technicianId`, `sort`, `page`, `size`) | `FIELD_JOB_VIEW` or `FIELD_JOB_MANAGE` |
| `GET /technicians`; `POST /`; `PUT /{id}` (with `version`); `POST /{id}/assign`; `POST /{id}/cancel` | `FIELD_JOB_MANAGE` |
| `GET /{id}`; `GET /{id}/photos/{photoId}/content` | Any of the three (a technician: own jobs only) |
| `POST /{id}/advance` `{ "to", "completionNotes", "signedByName", "remarks", "signature" }` | The assigned technician, or `FIELD_JOB_MANAGE` |
| `PUT /{id}/checklist/{itemId}`; `POST`, `DELETE /{id}/materials`; `POST /{id}/notes`; `POST`, `DELETE /{id}/photos` | The assigned technician, or `FIELD_JOB_MANAGE` |

Every action on a job answers with the whole job, including `nextStatus` (the step that can be
taken now) and `canAct` (whether the caller may take it).

## Screens

| Path | For |
| --- | --- |
| `/my-jobs` | The technician: the four lists |
| `/field-jobs/:id` | The job itself: customer and site (with call and map buttons), instructions, checklist, photos, materials, notes, sign-off, and the next step |
| `/field-jobs` | Dispatchers and viewers: every job, searchable, by status and technician |
| `/field-jobs/new`, `/field-jobs/:id/edit` | Dispatchers: schedule a job with its checklist |

## Not built

A separate mobile application, and working offline (the pages need a connection). Also: GPS
location and travel tracking, route planning, a calendar or map view for dispatch, notifications
to the technician, several technicians on one job, checklists from a template, materials from a
stock list, linking a job to a customer record or a project from the screens (the columns exist),
a PDF job report, and cleaning up photos of deleted jobs.
