-- Project templates.
--
-- How a project for a service is carried out differs per service: an interior design job goes
-- through concept, 3D design and revisions; a solar job through survey, procurement and
-- installation. That plan is data, defined by administrators per service. No stage of any
-- template is named in code or in a migration.
--
--   project_templates ──< project_template_stages
--
-- A template belongs to an organization and to one service (service_id refers to the catalog
-- module by id only). A stage is a row, so stages can be listed, ordered and scheduled; what
-- happens inside a stage (checkpoints, tasks with their checklists, required documents) is edited
-- and read together with the stage and is stored with it as JSON.

CREATE TABLE project_templates (
    id              uuid          NOT NULL DEFAULT gen_random_uuid(),
    organization_id uuid          NOT NULL,
    service_id      uuid          NOT NULL,
    name            varchar(200)  NOT NULL,
    description     varchar(1000),
    -- A template that is switched off is kept but no longer offered for new projects.
    active          boolean       NOT NULL DEFAULT true,
    -- Counts saves of the stages, so the audit trail shows that the plan changed.
    revision        integer       NOT NULL DEFAULT 1,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    created_by      varchar(100)  NOT NULL,
    updated_at      timestamptz   NOT NULL DEFAULT now(),
    updated_by      varchar(100)  NOT NULL,
    version         bigint        NOT NULL DEFAULT 0,
    deleted_at      timestamptz,
    CONSTRAINT pk_project_templates PRIMARY KEY (id)
);

-- One template per service in an organization; a removed template frees the service.
CREATE UNIQUE INDEX uq_project_templates_org_service
    ON project_templates (organization_id, service_id) WHERE deleted_at IS NULL;

CREATE TABLE project_template_stages (
    id                    uuid          NOT NULL DEFAULT gen_random_uuid(),
    template_id           uuid          NOT NULL,
    -- Identifies the stage within its template; dependencies refer to stages by this key.
    stage_key             varchar(60)   NOT NULL,
    name                  varchar(150)  NOT NULL,
    description           varchar(1000),
    -- Order in which the stages are listed (dependencies, not this, decide the schedule).
    position              integer       NOT NULL,
    -- Estimated duration in calendar days.
    duration_days         integer       NOT NULL,
    -- A role code (identity module), e.g. DESIGNER; NULL when no single role is responsible.
    responsible_role      varchar(50),
    -- Keys of the stages that must finish before this one starts: ["site_survey", "design"]
    depends_on            jsonb         NOT NULL DEFAULT '[]',
    -- Checkpoints reached in this stage: ["Design approved by client"]
    milestones            jsonb         NOT NULL DEFAULT '[]',
    -- [{"title", "description", "responsibleRole", "estimatedHours", "checklist": ["..."]}]
    tasks                 jsonb         NOT NULL DEFAULT '[]',
    -- [{"name", "required", "description"}]
    documents             jsonb         NOT NULL DEFAULT '[]',
    created_at            timestamptz   NOT NULL DEFAULT now(),
    updated_at            timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_project_template_stages PRIMARY KEY (id),
    CONSTRAINT fk_project_template_stages_template FOREIGN KEY (template_id)
        REFERENCES project_templates (id) ON DELETE CASCADE,
    CONSTRAINT uq_project_template_stages_key UNIQUE (template_id, stage_key),
    CONSTRAINT ck_project_template_stages_key_format CHECK (stage_key ~ '^[a-z][a-z0-9_]*$'),
    CONSTRAINT ck_project_template_stages_duration CHECK (duration_days BETWEEN 0 AND 3650),
    CONSTRAINT ck_project_template_stages_json CHECK (
        jsonb_typeof(depends_on) = 'array' AND jsonb_typeof(milestones) = 'array'
        AND jsonb_typeof(tasks) = 'array' AND jsonb_typeof(documents) = 'array')
);

CREATE INDEX ix_project_template_stages_template ON project_template_stages (template_id, position);

-- Defining templates is an administrator's job, separate from running projects.
INSERT INTO permissions (code, name) VALUES ('PROJECT_CONFIGURE', 'Define project templates');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE p.code = 'PROJECT_CONFIGURE' AND r.code IN ('SUPER_ADMIN', 'ADMIN');
