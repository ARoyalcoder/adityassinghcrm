# Project templates

How a project is carried out differs per service: an interior design job goes through concept, 3D
design and revisions; a solar job through survey, procurement and installation. That plan is
**data**. Administrators define one template per service; the backend validates and schedules it,
and a project can be planned from it. No stage of any template is named in Java, React or a
migration.

## What a template contains

A template belongs to one service (and one organization) and is a list of **stages**. Each stage has:

| | |
| --- | --- |
| Name and key | The key (lower_snake_case, made from the name when the stage is created) identifies the stage; dependencies refer to it |
| Estimated duration | Calendar days, 0 to 3650 |
| Responsible role | A role code (`DESIGNER`, `TECHNICIAN`, ...), or none |
| Waits for (dependencies) | The stages that must finish before this one starts |
| Milestones | Checkpoints reached in the stage ("Design approved by the customer") |
| Tasks | Title, optional description, role (defaults to the stage's) and estimated hours, each with a **checklist** |
| Required documents | Name, whether it must be provided, optional description |

### Examples

None of these is built in. Each is created on the admin screen (Configuration → **Project
templates**); the tests create exactly these through the API.

| Service | Stages |
| --- | --- |
| Interior design | Lead → Requirement → Concept → 3D Design → Revision → Working Drawings → Final Delivery |
| Solar | Site Survey → Design → Quotation → Procurement → Installation → Testing → Handover |
| CCTV | Site Survey → Design → Procurement → Installation → Configuration → Testing → Handover |

## Rules and schedule

`TemplatePlanner` holds the rules every template obeys, whatever its stages are:

- at least one stage, at most 40; keys and names distinct;
- a stage depends only on other stages that exist, and never, directly or through others, on itself;
- roles must be real role codes; milestones and documents are not repeated within a stage.

Every problem is reported at once, under the path of the value (`stages[2].dependsOn`), in the
standard `VALIDATION_FAILED` format, so the editor shows it on the stage concerned.

From a valid template it works out the **schedule**, in days from the start of a project: a stage
starts when the last stage it waits for has ended (day 0 if it waits for none), and the estimated
duration of the project is when the last stage ends. Stages that do not depend on each other run
side by side, so the total is the longest path, not the sum. The order in which stages are listed
is for reading only; dependencies decide the schedule.

`A → B → C` is simply each stage waiting for the one before, which is what the editor sets up when
a stage is added.

## Planning a project from a template

`POST /api/v1/projects/{id}/apply-template` (`PROJECT_UPDATE`) plans a project from the active
template of its service, dated from the project's start date:

| Template | Becomes in the project |
| --- | --- |
| Stage | A milestone named after the stage, due when the stage ends |
| Milestone of a stage | A further milestone, due with its stage |
| Task | A task under its stage's milestone, running from the stage's start to its end, with the responsible role written in its description |
| Checklist | The task's checklist items |
| Required document | A deliverable under the stage's milestone ("Required document" / "Optional document") |
| Dependency | The first task of a stage waits for the last task of each stage it depends on |
| Total duration | The project's expected completion date |

- The project gets a **copy**. Changing a template later affects future projects only.
- It works only on a project that has no milestones or tasks yet (`409` otherwise), so a plan is
  never duplicated or merged with a hand-made one.
- `422` if the project has no service, or its service has no active template.

Other modules read a service's plan through `project.api.ProjectTemplates.activeForService`.

## API

| Endpoint | Permission |
| --- | --- |
| `GET /project-templates` (service, stage count, estimated duration) | `PROJECT_VIEW` |
| `GET /project-templates/{id}` (stages with `startDay`, `endDay`; `totalDays`) | `PROJECT_VIEW` |
| `GET /project-templates/options` (role codes to choose from) | `PROJECT_VIEW` |
| `POST /project-templates` (one per service: `409` for a second) | `PROJECT_CONFIGURE` |
| `PUT /project-templates/{id}` (name, description and all stages, with `version`) | `PROJECT_CONFIGURE` |
| `POST /project-templates/{id}/activate`, `/deactivate`; `DELETE /project-templates/{id}` | `PROJECT_CONFIGURE` |
| `POST /projects/{id}/apply-template` | `PROJECT_UPDATE` |

`PROJECT_CONFIGURE` is held by Super Admin and Admin. Templates are per organization: another
organization's template is `404`, and two organizations can each have a template for the same service.

A template is saved as a whole. Stages are replaced as a set on every save (a stage that keeps its
name keeps its key), and each save raises the template's `revision`, which is what appears in the
audit log (entity `ProjectTemplate`). A switched-off template is kept but not applied; a removed
one frees its service for a new template.

## Storage

`project_templates` (one row per template) and `project_template_stages` (one row per stage, with
its dependencies, milestones, tasks and documents as JSON, since they are always read and written
with their stage). Migration `V19`.

## Not built

A project is not planned from its template automatically when it is created: it is a separate
call, and there is no project screen yet to make it from. Also not built: working-day calendars
(durations are calendar days), lead/lag between stages, assigning tasks to people rather than
roles, copying a template to another service, versions of a template to compare, and drag-and-drop
ordering.
