# Service-specific lead fields

What to ask when someone enquires differs per service: a solar enquiry needs the roof area, a CCTV
enquiry the camera count. These questions are **data**. Administrators define them per service; the
backend validates answers against the definitions, and the frontend draws the form from them.
No field is named in any Java class, React component or migration.

> Leads use this: the lead form asks the chosen service's fields, and a lead's answers are saved and
> shown with it (see [leads.md](leads.md)). The same component also drives the live preview on the
> admin screen.

## Field types

| Type | Answer (JSON) | Settings | Stored in |
| --- | --- | --- | --- |
| `TEXT` | string | `maxLength` (default 255, at most 1000) | `value_text` |
| `TEXTAREA` | string | `maxLength` (default 4000, at most 10000) | `value_text` |
| `NUMBER` | number (or numeric string), up to 4 decimals | `minValue`, `maxValue`, `unit` ("sq ft", "kW") | `value_number` |
| `CURRENCY` | number in INR, up to 2 decimals, not negative unless `minValue` says otherwise | `minValue`, `maxValue` | `value_number` |
| `SELECT` | one option's `value` | `options` (1 to 100) | `value_text` |
| `MULTI_SELECT` | array of option `value`s, no repeats | `options` | `value_json` |
| `BOOLEAN` | `true` / `false` | none | `value_boolean` |
| `DATE` | `"YYYY-MM-DD"` | none | `value_date` |
| `FILE` | the reference returned by the upload | `allowedFileTypes` (extensions; empty = any), `maxFileSizeMb` (default 10, at most 25) | `value_json` |

Every field also has a `key` (lower_snake_case, unique within its service), a `label`, optional
`helpText`, `required`, `displayOrder` and `active`.

- The **key and type never change** after creation: stored answers depend on them. To change
  either, remove the field and add a new one.
- A setting that does not belong to the type is rejected (a text field cannot have options).
- **Switching a field off** stops it being asked and validated; answers already given are kept.
- **Removing a field** is a soft delete: its answers stay in the database but are no longer shown,
  and its key can be reused.
- Removing an option does not touch answers that used it; it just cannot be chosen any more.
- An option's stored `value` is derived from its label when it is first added (`Shop floor` →
  `SHOP_FLOOR`) and kept for as long as the label is unchanged. Rewording a label creates a new value.

## Examples

Nothing below is built in. Each is created on the admin screen (Administration → Services →
**Lead fields**), and the integration tests create exactly these through the API.

| Service | Fields |
| --- | --- |
| Solar | Monthly electricity bill (currency, required) · Roof area (number, sq ft) · Required capacity (number, kW) · Site availability (yes/no) |
| CCTV | Camera count (number, required) · Site type (select) · Indoor / outdoor (select) · Storage requirement (number, days) |
| Interior | Property type (select) · Area (number, sq ft) · Design requirement (long text) · Rooms (multi-select) |
| Digital Marketing | Business type (text) · Website (text) · Current marketing channels (multi-select) · Monthly budget (currency) |

## Validation

`CustomFieldValidator` checks a map of `field key → value` against the service's active fields and
reports **every** problem at once, each under its field's key, in the standard error format:

```json
{ "status": 400, "code": "VALIDATION_FAILED",
  "errors": [ { "field": "roof_area", "message": "must be at least 100" },
              { "field": "monthly_electricity_bill", "message": "is required" },
              { "field": "camera_count", "message": "is not a field of this service" } ] }
```

Missing, `null`, blank and `[]` all mean "not answered". A required yes/no field accepts `false`.
The browser applies the same rules first (a schema generated from the definitions), but the server
decides.

## API

All under `/api/v1/catalog/services/{serviceId}/custom-fields`.

| Endpoint | Permission |
| --- | --- |
| `GET /` (`activeOnly=true` for the fields a lead form shows), `GET /{fieldId}` | `CATALOG_VIEW` |
| `POST /validate` body `{ "values": { ... } }` → the accepted answers, normalised | `CATALOG_VIEW` |
| `POST /`, `PUT /{fieldId}` (with `version`), `POST /{fieldId}/activate`, `/deactivate`, `DELETE /{fieldId}` | `CATALOG_UPDATE` |
| `POST /{fieldKey}/files` (multipart `file`) → `{ storageKey, fileName, contentType, sizeBytes }` | `CATALOG_UPDATE` (leads have their own, see below) |

A service has at most 50 fields. Changes to fields are audited (entity `ServiceField`).

### Files

A file is uploaded first and checked against its field's rules (type, size); the response is the
reference to send as the field's answer. The object is stored under
`lead-fields/<field id>/<generated name>`: nothing the user typed becomes part of the key, and a
reference is accepted as an answer only for the field it was uploaded for.

There are two upload endpoints with the same rules: this one for the administrator's preview
(`CATALOG_UPDATE`), and `POST /leads/custom-field-files` for people entering leads (`LEAD_CREATE`
or `LEAD_UPDATE`). Files that are uploaded and never attached to a lead are not cleaned up yet.

## Frontend

`frontend/src/components/dynamic-fields/` knows nothing about services or leads:

| Piece | Use |
| --- | --- |
| `<DynamicFields definitions name uploadFile />` | Renders the fields inside any react-hook-form `<Form>`; answers live under `name` |
| `buildAnswersSchema(definitions)` | Zod schema generated from the definitions (required, ranges, lengths, options) |
| `initialAnswers(definitions, stored?)` | Starting form values, or stored answers put back into the form |
| `toAnswers(definitions, values)` | What to send: numbers as numbers, yes/no as boolean, unanswered left out |
| `applyAnswerErrors(form, name, definitions, error)` | Puts the server's per-field errors next to the fields |

The only place a field type meets a component is the `INPUTS` map in `DynamicFields.tsx`. A type
the screen does not know is drawn as a text box rather than dropped.

## How the lead module uses it

Backend: it injects `catalog.api.LeadCustomFields`. It needs no knowledge of any field.

```java
leadCustomFields.validate(serviceId, answers);          // throws FieldValidationException naming each bad field
leadCustomFields.save(leadId, serviceId, answers);      // validates, then stores the complete set for the lead
leadCustomFields.values(leadId);                        // typed answers with their definitions, in form order
leadCustomFields.delete(leadId);                        // when the lead is removed
```

When a lead's service changes, it calls `delete(leadId)` before saving answers for the new service.
`lead_custom_field_values.lead_id` has no foreign key, like every reference between modules.

Frontend: load `customFieldsQuery(serviceId)` when the service is chosen, add
`customFields: buildAnswersSchema(active)` to the form's schema, render
`<DynamicFields definitions={active} name="customFields" />`, and send `toAnswers(...)`.
`LeadFormPage` and `ServiceFieldsPage`'s `Preview` are complete working examples.

## Not built

Searching or reporting on answers; conditional fields (show B only if A is yes); default values; copying fields from one
service to another; drag-and-drop ordering; cleaning up unattached uploads; downloading a stored
file from the UI.
