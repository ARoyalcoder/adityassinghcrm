-- Where each project stands in its pipeline, and how satisfied the client was.
--
-- The pipeline stages are not defined here or in code: they are the stages of the project's
-- template (project_template_stages), which administrators configure per service. These tables
-- only record which of those stages a project is in.

-- The stage a project is in now. One row per project that has been placed in a stage.
CREATE TABLE project_stage_states (
    project_id      uuid          NOT NULL,
    organization_id uuid          NOT NULL,
    -- The template the stage came from, by id (kept even if the template is later removed).
    template_id     uuid          NOT NULL,
    stage_key       varchar(60)   NOT NULL,
    -- Copied when the project entered the stage, so analytics still read correctly after the
    -- template is edited.
    stage_name      varchar(150)  NOT NULL,
    stage_position  integer       NOT NULL,
    entered_at      timestamptz   NOT NULL,
    moved_by        varchar(100)  NOT NULL,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_project_stage_states PRIMARY KEY (project_id),
    CONSTRAINT fk_project_stage_states_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
);

CREATE INDEX ix_project_stage_states_org_stage ON project_stage_states (organization_id, stage_name);

-- Every stage a project has been in, with when it entered and left: how long stages really take.
CREATE TABLE project_stage_history (
    id          uuid          NOT NULL DEFAULT gen_random_uuid(),
    project_id  uuid          NOT NULL,
    stage_key   varchar(60)   NOT NULL,
    stage_name  varchar(150)  NOT NULL,
    entered_at  timestamptz   NOT NULL,
    -- NULL while the project is still in the stage.
    left_at     timestamptz,
    moved_by    varchar(100)  NOT NULL,
    CONSTRAINT pk_project_stage_history PRIMARY KEY (id),
    CONSTRAINT fk_project_stage_history_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
);

CREATE INDEX ix_project_stage_history_project ON project_stage_history (project_id, entered_at);

-- The client's rating of a project, recorded by staff after delivery. One per project.
CREATE TABLE project_feedback (
    project_id      uuid          NOT NULL,
    organization_id uuid          NOT NULL,
    -- 1 (very unhappy) to 5 (very happy).
    rating          smallint      NOT NULL,
    comment         varchar(1000),
    recorded_by     varchar(100)  NOT NULL,
    recorded_at     timestamptz   NOT NULL,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_project_feedback PRIMARY KEY (project_id),
    CONSTRAINT fk_project_feedback_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT ck_project_feedback_rating CHECK (rating BETWEEN 1 AND 5)
);
