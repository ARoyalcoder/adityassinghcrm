-- Opportunities and Sales Pipelines module
-- Configurable pipelines and stages with stage history and analytics

CREATE TABLE pipelines (
    id              uuid         NOT NULL DEFAULT gen_random_uuid(),
    organization_id uuid         NOT NULL,
    name            varchar(100) NOT NULL,
    code            varchar(50)  NOT NULL,
    description     varchar(500),
    is_default      boolean      NOT NULL DEFAULT false,
    active          boolean      NOT NULL DEFAULT true,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      varchar(100) NOT NULL,
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    updated_by      varchar(100) NOT NULL,
    version         bigint       NOT NULL DEFAULT 0,
    deleted_at      timestamptz,
    CONSTRAINT pk_pipelines PRIMARY KEY (id),
    CONSTRAINT uq_pipelines_org_code UNIQUE (organization_id, code)
);

CREATE TABLE pipeline_stages (
    id                  uuid         NOT NULL DEFAULT gen_random_uuid(),
    pipeline_id         uuid         NOT NULL,
    name                varchar(100) NOT NULL,
    code                varchar(50)  NOT NULL,
    display_order       integer      NOT NULL,
    default_probability integer      NOT NULL DEFAULT 50,
    is_won              boolean      NOT NULL DEFAULT false,
    is_lost             boolean      NOT NULL DEFAULT false,
    color               varchar(30)  DEFAULT 'blue',
    created_at          timestamptz  NOT NULL DEFAULT now(),
    created_by          varchar(100) NOT NULL,
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    updated_by          varchar(100) NOT NULL,
    version             bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_pipeline_stages PRIMARY KEY (id),
    CONSTRAINT fk_pipeline_stages_pipeline FOREIGN KEY (pipeline_id) REFERENCES pipelines (id) ON DELETE CASCADE,
    CONSTRAINT ck_pipeline_stages_probability CHECK (default_probability >= 0 AND default_probability <= 100)
);

CREATE INDEX ix_pipeline_stages_pipeline_order ON pipeline_stages (pipeline_id, display_order);

CREATE TABLE opportunities (
    id                  uuid          NOT NULL DEFAULT gen_random_uuid(),
    organization_id     uuid          NOT NULL,
    title               varchar(250)  NOT NULL,
    customer_id         uuid,
    customer_name       varchar(255),
    lead_id             uuid,
    lead_name           varchar(255),
    service_id          uuid,
    service_name        varchar(255),
    vertical_code       varchar(50),
    value               numeric(14,2) NOT NULL DEFAULT 0.00,
    probability         integer       NOT NULL DEFAULT 50,
    expected_close_date date,
    owner_id            uuid          NOT NULL,
    owner_name          varchar(255)  NOT NULL,
    pipeline_id         uuid          NOT NULL,
    stage_id            uuid          NOT NULL,
    status              varchar(20)   NOT NULL DEFAULT 'OPEN',
    loss_reason         varchar(500),
    won_at              timestamptz,
    lost_at             timestamptz,
    stage_entered_at    timestamptz   NOT NULL DEFAULT now(),
    created_at          timestamptz   NOT NULL DEFAULT now(),
    created_by          varchar(100)  NOT NULL,
    updated_at          timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(100)  NOT NULL,
    version             bigint        NOT NULL DEFAULT 0,
    deleted_at          timestamptz,
    CONSTRAINT pk_opportunities PRIMARY KEY (id),
    CONSTRAINT fk_opportunities_pipeline FOREIGN KEY (pipeline_id) REFERENCES pipelines (id),
    CONSTRAINT fk_opportunities_stage FOREIGN KEY (stage_id) REFERENCES pipeline_stages (id),
    CONSTRAINT ck_opportunities_status CHECK (status IN ('OPEN', 'WON', 'LOST')),
    CONSTRAINT ck_opportunities_probability CHECK (probability >= 0 AND probability <= 100)
);

CREATE INDEX ix_opportunities_org_pipeline_stage ON opportunities (organization_id, pipeline_id, stage_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_opportunities_org_status ON opportunities (organization_id, status) WHERE deleted_at IS NULL;
CREATE INDEX ix_opportunities_customer ON opportunities (customer_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_opportunities_lead ON opportunities (lead_id) WHERE deleted_at IS NULL;

CREATE TABLE opportunity_stage_history (
    id               uuid         NOT NULL DEFAULT gen_random_uuid(),
    opportunity_id   uuid         NOT NULL,
    from_stage_id    uuid,
    from_stage_name  varchar(100),
    to_stage_id      uuid         NOT NULL,
    to_stage_name    varchar(100) NOT NULL,
    entered_at       timestamptz  NOT NULL,
    exited_at        timestamptz,
    duration_seconds bigint,
    changed_by       varchar(100) NOT NULL,
    notes            varchar(1000),
    CONSTRAINT pk_opportunity_stage_history PRIMARY KEY (id),
    CONSTRAINT fk_opp_stage_hist_opportunity FOREIGN KEY (opportunity_id) REFERENCES opportunities (id) ON DELETE CASCADE,
    CONSTRAINT fk_opp_stage_hist_from_stage FOREIGN KEY (from_stage_id) REFERENCES pipeline_stages (id) ON DELETE SET NULL,
    CONSTRAINT fk_opp_stage_hist_to_stage FOREIGN KEY (to_stage_id) REFERENCES pipeline_stages (id) ON DELETE CASCADE
);

CREATE INDEX ix_opp_stage_hist_opportunity ON opportunity_stage_history (opportunity_id, entered_at DESC);

CREATE TABLE opportunity_notes (
    id             uuid          NOT NULL DEFAULT gen_random_uuid(),
    opportunity_id uuid          NOT NULL,
    body           varchar(4000) NOT NULL,
    author_id      uuid          NOT NULL,
    author_name    varchar(255)  NOT NULL,
    created_at     timestamptz   NOT NULL DEFAULT now(),
    created_by     varchar(100)  NOT NULL,
    version        bigint        NOT NULL DEFAULT 0,
    CONSTRAINT pk_opportunity_notes PRIMARY KEY (id),
    CONSTRAINT fk_opp_notes_opportunity FOREIGN KEY (opportunity_id) REFERENCES opportunities (id) ON DELETE CASCADE
);

CREATE INDEX ix_opp_notes_opportunity ON opportunity_notes (opportunity_id, created_at DESC);

-- Seed standard pipelines and stages for existing organizations
INSERT INTO pipelines (organization_id, name, code, description, is_default, active, created_by, updated_by)
SELECT id, 'Standard Sales Pipeline', 'STANDARD', 'Default company sales and deals pipeline', true, true, 'system', 'system'
FROM organizations;

INSERT INTO pipeline_stages (pipeline_id, name, code, display_order, default_probability, is_won, is_lost, color, created_by, updated_by)
SELECT p.id, s.name, s.code, s.display_order, s.default_probability, s.is_won, s.is_lost, s.color, 'system', 'system'
FROM pipelines p
CROSS JOIN (
    VALUES
        ('New', 'NEW', 1, 10, false, false, 'slate'),
        ('Qualified', 'QUALIFIED', 2, 25, false, false, 'indigo'),
        ('Requirement Confirmed', 'REQUIREMENT_CONFIRMED', 3, 40, false, false, 'cyan'),
        ('Site Visit', 'SITE_VISIT', 4, 50, false, false, 'amber'),
        ('Proposal', 'PROPOSAL', 5, 65, false, false, 'purple'),
        ('Negotiation', 'NEGOTIATION', 6, 80, false, false, 'blue'),
        ('Won', 'WON', 7, 100, true, false, 'emerald'),
        ('Lost', 'LOST', 8, 0, false, true, 'rose')
) AS s (name, code, display_order, default_probability, is_won, is_lost, color)
WHERE p.code = 'STANDARD';

-- Permissions
INSERT INTO permissions (code, name) VALUES
    ('OPPORTUNITY_VIEW', 'View opportunities and sales pipelines'),
    ('OPPORTUNITY_CREATE', 'Create opportunities'),
    ('OPPORTUNITY_UPDATE', 'Edit opportunities, log notes, and move pipeline stages'),
    ('OPPORTUNITY_DELETE', 'Delete opportunities'),
    ('OPPORTUNITY_CONFIGURE', 'Configure pipelines and stages');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE (p.code IN ('OPPORTUNITY_VIEW', 'OPPORTUNITY_CREATE', 'OPPORTUNITY_UPDATE') 
       AND r.code IN ('SUPER_ADMIN', 'ADMIN', 'DIRECTOR', 'SALES_MANAGER', 'SALES_EXECUTIVE'))
   OR (p.code IN ('OPPORTUNITY_DELETE', 'OPPORTUNITY_CONFIGURE') 
       AND r.code IN ('SUPER_ADMIN', 'ADMIN', 'DIRECTOR', 'SALES_MANAGER'));
