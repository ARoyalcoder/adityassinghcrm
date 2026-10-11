-- Brings three tables in line with the entities that map them. Without these columns the
-- application does not start (Hibernate validates the schema against the entities at start-up).
--
-- Added as a new migration rather than by editing V13 and V14, so a database that has already
-- applied those is repaired too.

ALTER TABLE activity_reminders
    ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now();
UPDATE activity_reminders SET updated_at = created_at;

ALTER TABLE opportunity_notes
    ADD COLUMN updated_at timestamptz,
    ADD COLUMN updated_by varchar(100);
UPDATE opportunity_notes SET updated_at = created_at, updated_by = created_by;
ALTER TABLE opportunity_notes
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN updated_at SET DEFAULT now(),
    ALTER COLUMN updated_by SET NOT NULL;

ALTER TABLE opportunity_stage_history
    ADD COLUMN created_at timestamptz,
    ADD COLUMN updated_at timestamptz;
UPDATE opportunity_stage_history SET created_at = entered_at, updated_at = coalesce(exited_at, entered_at);
ALTER TABLE opportunity_stage_history
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN created_at SET DEFAULT now(),
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN updated_at SET DEFAULT now();

-- Foreign keys that no index covers: deleting or updating the row they point at scans the whole table.
CREATE INDEX ix_activities_parent ON activities (parent_activity_id);
CREATE INDEX ix_activity_reminders_activity ON activity_reminders (activity_id);
CREATE INDEX ix_opportunities_pipeline ON opportunities (pipeline_id);
CREATE INDEX ix_opportunities_stage ON opportunities (stage_id);
CREATE INDEX ix_opportunity_stage_history_from ON opportunity_stage_history (from_stage_id);
CREATE INDEX ix_opportunity_stage_history_to ON opportunity_stage_history (to_stage_id);
CREATE INDEX ix_project_deliverables_milestone ON project_deliverables (milestone_id);
