# Leads and lead qualification

A lead is an enquiry that may become business. Every lead carries four conclusions, each with its
reason:

| | What it says | Decided by |
| --- | --- | --- |
| **Score** | 0 to 100: how promising the lead is | A scorer (today: fixed rules), from the facts about the lead |
| **Priority** | `HIGH`, `MEDIUM`, `LOW`: how soon to work it | The score (bands), unless the lead is disqualified |
| **Health** | `GOOD`, `NEEDS_ATTENTION`, `AT_RISK`: is it being worked | How long since anything was done about it |
| **Qualification status** | `NOT_STARTED`, `IN_PROGRESS`, `QUALIFIED`, `DISQUALIFIED` | The answers to the qualification questions |

They are separate on purpose. A valuable lead nobody has called for ten days is high priority *and*
at risk; a small lead that answered every question well is qualified *and* low priority.

> **Scope.** The lead record here is the minimum that qualification needs: contact, service,
> estimated value, status (open / won / lost), an activity log, and the service's own fields
> ([custom-fields.md](custom-fields.md)). Assignment, sources, pipeline stages, follow-up tasks,
> conversion to a customer, duplicate detection and import are **not built**.

## The score is its explanation

The score is exactly the sum of its factors, limited to 0..100. Nothing else goes into it, so
"Why this score?" is not a description of the score: it is the score.

```
+20  High estimated value (₹5,00,000 or more)
+15  Decision maker identified: Yes
+10  Recent activity (yesterday)
───
 45
```

Left alone for three days, the same lead reads `+20`, `+15`, `−10 No activity for 3 days` = 25.

`GET /api/v1/leads/{id}/qualification` returns the score, the total before limiting, every factor
(`code`, `category`, `label`, `points`), the engine that produced it, and priority, health and
qualification status each with a reason in words. The lead page shows exactly that.

### The built-in rules (`rules-v1`)

| Factor | Points | When |
| --- | --- | --- |
| `HIGH_VALUE` | +20 | Estimated value ≥ ₹5,00,000 |
| `MEDIUM_VALUE` | +10 | Estimated value ≥ ₹1,00,000 (and below high) |
| `QUESTION:<key>` | as configured | One per answered qualification question: the points of the chosen option |
| `RECENT_ACTIVITY` | +10 | An activity was logged within the last 2 days |
| `NO_ACTIVITY` | −10 | 3 or more days with no activity (counted from creation if nothing was ever logged) |
| `GONE_COLD` | −20 | 7 or more days with no activity (instead of `NO_ACTIVITY`) |
| `COMPLETE_CONTACT` | +5 | Both a phone number and an email address are known |

A factor worth nothing is not listed. The amounts, day counts and points above are the defaults of
`app.leads.scoring.*` in `application.yml`; they are settings because they are business decisions.
The rules themselves are a list in `RuleBasedLeadScorer`: adding one is adding a method and a line.

The engine is deterministic. It has no clock and no database access: "now" is one of the facts it
is given (`LeadFacts.asOf`), so the same facts and settings always give the same score.
`LeadScoringTest` covers every rule, every boundary, and that score always equals the sum.

### Priority, health and qualification status

| | Rule | Example reason |
| --- | --- | --- |
| Priority | Score ≥ 60 `HIGH`; ≥ 30 `MEDIUM`; else `LOW`. A disqualified lead is `LOW` | "Score 45 is between 30 and 59" |
| Health | ≥ 7 silent days `AT_RISK`; ≥ 3 `NEEDS_ATTENTION`; else `GOOD` | "No activity for 3 days (3 or more needs a follow-up)" |
| Qualification | Any disqualifying answer: `DISQUALIFIED`. No answers: `NOT_STARTED`. A required question unanswered: `IN_PROGRESS`. Otherwise `QUALIFIED` | "1 of 2 required questions answered" |

Qualification status does not depend on the score. These three are decided in `LeadAssessment`,
not by the scorer, so they stay the same whichever scorer produces the score.

## Qualification questions

Each organization defines its own (Configuration → **Qualification**, permission `LEAD_CONFIGURE`).
A question has a key, a label, and two or more answers. Each answer has:

- **points** (−100 to 100), added to the score of a lead that gives it;
- optionally **rules the lead out**, which disqualifies the lead whatever else is true.

A question can be **required**: a lead is not `QUALIFIED` until every required question is
answered. People answering the questions on a lead do not see the points.

- Changing, switching off or removing a question **rescores every open lead** of the organization
  immediately.
- A switched-off or removed question is no longer asked and its answers no longer count; switching
  it back on makes earlier answers count again.
- If the option a lead chose is withdrawn, that answer falls away.
- A question's key never changes. An answer's stored value is kept when its label is reworded.

No questions are seeded. Until an organization adds some, a lead's score comes from the built-in
rules alone.

## When scores are worked out

- **On every change**: creating or editing a lead, logging an activity and answering a question
  each end by reassessing the lead and storing the result on it (for lists, filters and sorting).
- **On a schedule**: `LeadRescoringJob` reassesses every open lead hourly
  (`app.leads.scoring.rescore-cron`), because "no activity for 3 days" becomes true without any
  event. Between runs a list can lag by up to an hour; the lead page never does, because
  `/qualification` is always worked out as of now.
- **Closed leads** (won or lost) are judged as of the moment they closed and are not rescored.
- Storing an assessment does not change the lead's version, so a background rescore never makes a
  user's edit fail as "changed by someone else".
- Every change of assessment is appended to `lead_score_history` with its factors and engine
  (`GET /leads/{id}/score-history`).

## AI scoring later

No model is used today. The seam for one is `lead.api.LeadScorer`:

```java
public interface LeadScorer {
    String engine();                  // "rules-v1"; a model would be e.g. "model-2027-03"
    LeadScore score(LeadFacts facts); // the score AND the factors that explain it
}
```

- `LeadFacts` is everything a scorer may know, as plain values (value, contact details, activity
  dates, answers with their points, the service, "as of"). A scorer gets nothing else.
- `LeadScore` must contain factors. A scorer that cannot say why is not acceptable, so the
  "Why this score?" panel keeps working unchanged; it already names an unknown engine as
  "Scored by …" instead of claiming fixed rules.
- The scorer is selected by `app.leads.scoring.engine`. Start-up fails if no bean has that name.
- Priority, health, qualification status, storage, history, the API and every screen are
  independent of which scorer runs. `LeadScoringTest` includes a stand-in scorer to prove it.

To add one: implement `LeadScorer` as a Spring bean (it may live in its own module; it depends only
on `lead.api`), give it a new engine name, and switch the setting. What a real one will also need,
and what is **not** built: a fallback to the rules when the model is unavailable, the service's
custom-field answers in `LeadFacts`, and evaluation against outcomes. For that last one the data
is already being collected: `lead_score_history` records every assessment with its factors and
engine, and each lead ends as `WON` or `LOST`.

## Permissions

| Permission | Allows | Default roles |
| --- | --- | --- |
| `LEAD_VIEW` | List, lead, activities, "Why this score?", score history | Super Admin, Admin, Director, Sales Manager, Sales Executive |
| `LEAD_CREATE` | Create a lead | Super Admin, Admin, Sales Manager, Sales Executive |
| `LEAD_UPDATE` | Edit, close, log activity, answer qualification questions | Super Admin, Admin, Sales Manager, Sales Executive |
| `LEAD_DELETE` | Delete a lead | Super Admin, Admin, Sales Manager |
| `LEAD_CONFIGURE` | Define the qualification questions and their points | Super Admin, Admin, Sales Manager |

Everyone with `LEAD_VIEW` sees all leads of their organization. Another organization's lead or
question is `404`.

## API

| Endpoint | Permission |
| --- | --- |
| `GET /leads` (`search`, `status`, `priority`, `health`, `qualificationStatus`, `serviceId`, `sort`, `page`, `size`; default sort: highest score) | `LEAD_VIEW` |
| `GET /leads/{id}`, `/activities`, `/qualification`, `/score-history` | `LEAD_VIEW` |
| `POST /leads`, `PUT /leads/{id}` (with `version`; `customFields` are the service's own answers, errors reported as `customFields.<key>`) | `LEAD_CREATE` / `LEAD_UPDATE` |
| `POST /leads/custom-field-files?serviceId=&fieldKey=` (multipart `file`) | `LEAD_CREATE` or `LEAD_UPDATE` |
| `POST /leads/{id}/activities` | `LEAD_UPDATE` |
| `PUT /leads/{id}/qualification/answers` body `{ "answers": { "<key>": "<option>" \| null } }` | `LEAD_UPDATE` |
| `DELETE /leads/{id}` | `LEAD_DELETE` |
| `GET`, `POST /lead-qualification/questions`; `PUT`, `DELETE /{id}`; `POST /{id}/activate`, `/deactivate` | `LEAD_CONFIGURE` |

## Not built

Everything in the scope note above, and: a manual priority override; questions that apply only to
some services; scoring rules editable on a screen (they are settings in `application.yml`);
notifications when a lead needs attention; a chart of the score history; removing or editing a
logged activity; leads on the customer timeline.
