# Project analytics

One dashboard (`/project-analytics`, permission `PROJECT_VIEW`) that says how projects are doing.
Every figure is worked out from the project records on request by `ProjectAnalyticsService`;
nothing is stored and nothing is estimated. **A figure that cannot be worked out is `null`**, and
the screen says so in words ("Not known yet") instead of showing 0.

What "completed" and "cancelled" mean comes from the organization's own project statuses
(`project_statuses.is_completed` / `is_cancelled`). An **active** project is one that is neither.
Deleted projects are never counted.

## Dashboard cards

| Card | How it is worked out |
| --- | --- |
| Active Projects | Projects that are neither completed nor cancelled |
| Starting Today | Active, start date is today |
| Due Today | Active, expected completion date is today |
| Delayed Projects | Active, expected completion date has passed |
| Completed This Month | Completed, with an actual completion date in the current month |
| Revenue in Progress | Sum of `revenue` over active projects |
| Completed Revenue | Sum of `revenue` over completed projects (all time; the month's share is shown beneath) |
| Average Project Duration | Mean of actual completion date − start date, over completed projects |
| Average Delivery Delay | Mean days past the expected date, **over projects delivered late only** |
| Client Satisfaction | Mean rating (1 to 5) over rated projects, with how many were rated |

## Pipeline stages

The stages are **not defined in code**. They are the stages of the project's template
([project-templates.md](project-templates.md)), which administrators configure per service. A
pipeline such as Received → Presentation → Drafting/Modeling → Finishing/Rendering → Test
Render/Draft → Delivered → Under Revision → Revised & Closed → On Hold → Dead is created by adding
those ten stages to a template; the tests do exactly that. Stages that a project can be moved to
freely (On Hold, Dead) are simply stages that wait for nothing.

- A project enters the **first stage** of its template when it is planned from it
  (`POST /projects/{id}/apply-template`).
- `PUT /projects/{id}/stage` `{ "stageKey": "..." }` (`PROJECT_UPDATE`) moves it to any other stage
  of its template. On the dashboard this is the stage selector in the "Active projects" list.
- `project_stage_states` holds the current stage; `project_stage_history` every stage with when
  the project entered and left it. The stage's name is copied when the project enters, so renaming
  a stage in the template affects projects that enter it afterwards.
- The pipeline chart counts **all live projects** by current stage, in the template's order, and
  says how many active projects are in no stage yet. Stages of different templates with the same
  name are counted together.

## Delivery performance

Over completed projects that have an actual completion date: delivered on time (on or before the
expected date), delivered late, the on-time share, average delay (late ones only), the fastest and
the longest delivery (start to completion, with the project), and the average completion time.

## Productivity

| Figure | How it is worked out |
| --- | --- |
| Projects completed | All time, and this month |
| Average hours | Hours logged on tasks (`actual_hours`) per completed project, over completed projects that have tasks |
| Average deliverables | Deliverables per completed project |
| Average drawings | **Not tracked.** Nothing records a drawing separately from a deliverable, so this is always `null` and shown as "Not tracked yet" |
| Employee productivity | Per person with assigned tasks: tasks completed, open tasks, hours logged, projects involved (top 20) |

## Client satisfaction

`PUT /projects/{id}/satisfaction` `{ "rating": 1..5, "comment": "..." }` (`PROJECT_UPDATE`) records
the client's rating of a project in `project_feedback`; a second rating replaces the first.
There is **no screen for this yet**: until ratings are recorded through the API, the card reads
"No ratings yet".

## API

| Endpoint | Permission |
| --- | --- |
| `GET /project-analytics` | `PROJECT_VIEW` |
| `GET /project-analytics/pipeline-projects` (active projects with their stage and the stages they can move to) | `PROJECT_VIEW` |
| `GET /projects/{id}/stage` | `PROJECT_VIEW` |
| `PUT /projects/{id}/stage`, `PUT /projects/{id}/satisfaction` | `PROJECT_UPDATE` |

All figures are for the caller's organization. Tables: migration `V20`.

## The look

The dashboard follows the soft style of the reference design: a lavender page, white rounded cards
lifted by a faint shadow, one purple for what matters, and a small picture on each card. That
style is set once, in the design tokens of `frontend/src/index.css` and the shared card, button and
sidebar components, so every screen has it. The pictures are emoji; there are no custom 3D
illustrations. The pipeline is drawn as one bar per stage in a single colour, each labelled with
its count (ten stages do not fit a donut), and the on-time share as a meter.

## Not built

Date-range filters and trends over time; filtering by service, branch or manager; drawings; a
screen for recording client ratings; time spent per stage (the history is recorded but not yet
charted); export.
